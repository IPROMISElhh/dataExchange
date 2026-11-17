package com.lhh.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhh.pojo.AntiSeepageWall;
import com.lhh.pojo.Consolidation;
import com.lhh.pojo.CrushedStonePile;
import com.lhh.service.AntiSeepageWallService;
import com.lhh.service.ConsolidationService;
import com.lhh.service.CrushedStonePileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * MQTT 明文数据消息监听器（按 type 分类型落库）
 * <p>
 * 订阅明文主题后，每帧为一个完整 JSON 对象（见《明文数据格式与落库对接说明》）。
 * 解析时先取公共系统字段（type、device_id、collectTime、seq），再按 type 路由到对应业务表：
 * <ul>
 *   <li>type=0 碎石桩 → {@link CrushedStonePileService}（crushed_stone_pile_hot）</li>
 *   <li>type=1 防渗墙 → {@link AntiSeepageWallService}（anti_seepage_wall_hot）</li>
 *   <li>type=2 固结   → {@link ConsolidationService}（consolidation_hot）</li>
 * </ul>
 * collectTime 为 Unix 秒级时间戳（东八区基准），还原为 LocalDateTime 后作为分区键落库；
 * 判重由各热表的唯一键 (device_id, seq, collect_time) + INSERT IGNORE 保证，兼容 QoS 1 重复投递。
 * </p>
 * <p>
 * 前向兼容：type 缺失/未知、collectTime 缺失时仅告警丢弃，不抛异常；解析忽略未知字段。
 * 本类由 {@link com.lhh.config.MqttClientConfig} 注册为 Paho 回调的下游处理器，
 * 运行在 Paho 的 messageArrived 投递线程上，处理失败仅记录日志、不抛异常，避免断订阅。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/30 15:00
 */
@Slf4j
@Component
public class MqttDataLogListener {

    /** 业务类型：碎石桩 */
    private static final int TYPE_CRUSHED_STONE_PILE = 0;

    /** 业务类型：防渗墙 */
    private static final int TYPE_ANTI_SEEPAGE_WALL = 1;

    /** 业务类型：固结 */
    private static final int TYPE_CONSOLIDATION = 2;

    /** 东八区：把帧里的 collectTime（Unix 秒级时间戳）还原为本地时间，与发送端一致 */
    private static final ZoneId ZONE_EAST_8 = ZoneId.of("Asia/Shanghai");

    /** JSON 解析器（spring-boot-starter-web 已自带 Jackson） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 碎石桩业务服务（type=0） */
    @Autowired
    private CrushedStonePileService crushedStonePileService;

    /** 防渗墙业务服务（type=1） */
    @Autowired
    private AntiSeepageWallService antiSeepageWallService;

    /** 固结业务服务（type=2） */
    @Autowired
    private ConsolidationService consolidationService;

    /**
     * 处理单条 MQTT 消息：解析 JSON → 按 type 路由 → 写入对应业务热表
     *
     * @param topic   消息来源 topic（通配符订阅时用于区分业务来源）
     * @param payload 消息报文（UTF-8 JSON 字符串）
     */
    public void handle(String topic, String payload) {
        try {
            // ① 解析报文
            JsonNode node = objectMapper.readTree(payload);

            // ② 取 type：缺失/非法无法路由，告警丢弃（前向兼容：忽略未知 type）
            JsonNode typeNode = node.get("type");
            if (typeNode == null || !typeNode.canConvertToInt()) {
                log.warn("MQTT 消息缺少合法 type 字段，忽略 topic={}, payload={}", topic, payload);
                return;
            }
            int type = typeNode.asInt();

            // ③ 取 collectTime（分区键）：缺失则无法定位分区，告警丢弃
            LocalDateTime collectTime = parseCollectTime(node);
            if (collectTime == null) {
                log.warn("MQTT 消息缺少合法 collectTime，忽略 topic={}, type={}", topic, type);
                return;
            }

            // ④ 公共系统字段
            String deviceId = node.path("device_id").asText(null);
            Integer seq = getInt(node, "seq");

            // ⑤ 按 type 分发落库
            int rows = 0;
            switch (type) {
                case TYPE_CRUSHED_STONE_PILE -> rows = saveCrushedStonePile(node, deviceId, collectTime, seq);
                case TYPE_ANTI_SEEPAGE_WALL -> rows = saveAntiSeepageWall(node, deviceId, collectTime, seq);
                case TYPE_CONSOLIDATION -> rows = saveConsolidation(node, deviceId, collectTime, seq);
                default -> {
                    log.warn("MQTT 未知 type={}，暂无对应业务表，忽略 topic={}", type, topic);
                    return;
                }
            }

            log.info("MQTT 消息入库 topic={}, type={}, deviceId={}, seq={}, collectTime={}, rows={}",
                    topic, type, deviceId, seq, collectTime, rows);
        } catch (Exception e) {
            // 解析/入库失败只记日志，避免异常传导回 Paho 投递线程导致断订阅
            log.error("MQTT 消息处理失败 topic={}, payload={}", topic, payload, e);
        }
    }

    /**
     * type=0 碎石桩：提取业务字段并写入 crushed_stone_pile_hot
     */
    private int saveCrushedStonePile(JsonNode node, String deviceId, LocalDateTime collectTime, Integer seq) {
        CrushedStonePile entity = new CrushedStonePile();
        entity.setDeviceId(deviceId);
        entity.setCollectTime(collectTime);
        entity.setSeq(seq);
        entity.setRunDepth(getFloat(node, "runDepth"));
        entity.setRunCurrent(getFloat(node, "runCurrent"));
        entity.setRunWaterPress(getFloat(node, "runWaterPress"));
        entity.setRunWeightAdd(getFloat(node, "runWeightAdd"));
        entity.setRunState(getInt(node, "runState"));
        entity.setType(TYPE_CRUSHED_STONE_PILE);
        return crushedStonePileService.insertHot(entity);
    }

    /**
     * type=1 防渗墙：提取业务字段并写入 anti_seepage_wall_hot
     */
    private int saveAntiSeepageWall(JsonNode node, String deviceId, LocalDateTime collectTime, Integer seq) {
        AntiSeepageWall entity = new AntiSeepageWall();
        entity.setDeviceId(deviceId);
        entity.setCollectTime(collectTime);
        entity.setSeq(seq);
        entity.setAngleX(getFloat(node, "angleX"));
        entity.setAngleY(getFloat(node, "angleY"));
        entity.setMainCylinder(getFloat(node, "mainCylinder"));
        entity.setAuxCylinder(getFloat(node, "auxCylinder"));
        entity.setState(getInt(node, "state"));
        entity.setType(TYPE_ANTI_SEEPAGE_WALL);
        return antiSeepageWallService.insertHot(entity);
    }

    /**
     * type=2 固结：提取业务字段并写入 consolidation_hot
     */
    private int saveConsolidation(JsonNode node, String deviceId, LocalDateTime collectTime, Integer seq) {
        Consolidation entity = new Consolidation();
        entity.setDeviceId(deviceId);
        entity.setCollectTime(collectTime);
        entity.setSeq(seq);
        entity.setPressure(getFloat(node, "pressure"));
        entity.setFlowIn(getFloat(node, "flowIn"));
        entity.setFlowOut(getFloat(node, "flowOut"));
        entity.setDensity(getFloat(node, "density"));
        entity.setTemperature(getFloat(node, "temperature"));
        entity.setType(TYPE_CONSOLIDATION);
        return consolidationService.insertHot(entity);
    }

    /**
     * 解析 collectTime（Unix 秒级时间戳，东八区基准）为 LocalDateTime；缺失/非法返回 null
     */
    private LocalDateTime parseCollectTime(JsonNode node) {
        JsonNode collectTimeNode = node.get("collectTime");
        if (collectTimeNode == null || !collectTimeNode.canConvertToLong()) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(collectTimeNode.asLong()), ZONE_EAST_8);
    }

    /**
     * 读取 Float 字段：缺失/null 返回 null；浮点按原值存储，不预先四舍五入
     */
    private Float getFloat(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : (float) value.asDouble();
    }

    /**
     * 读取 Integer 字段：缺失/null 返回 null
     */
    private Integer getInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asInt();
    }
}
