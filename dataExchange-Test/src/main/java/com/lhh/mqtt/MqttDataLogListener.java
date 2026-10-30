package com.lhh.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhh.pojo.DataLog;
import com.lhh.service.DataLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MQTT 数据日志消息监听器
 * <p>
 * 接收 broker 推送的 topic 消息，解析 JSON 报文后写入热表 data_log_hot。
 * 预期报文格式：{"createTs": "2026-10-30 15:00:00", "content": "数据创建"}，
 * createTs 字段缺失或为空时默认取当前时间（保证落入已预建的当日分区）。
 * </p>
 * <p>
 * 本类由 {@link com.lhh.config.MqttClientConfig} 注册为 Paho 回调的下游处理器，
 * 运行在 Paho 的 messageArrived 投递线程上，处理失败仅记录日志、不抛异常，
 * 避免影响客户端后续消息接收。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/30 15:00
 */
@Slf4j
@Component
public class MqttDataLogListener {

    /** createTs 字段的时间格式，与 DataLog.createTs 语义对应 */
    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** JSON 解析器（spring-boot-starter-web 已自带 Jackson） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 数据日志服务，提供热表写入能力 */
    @Autowired
    private DataLogService dataLogService;

    /**
     * 处理单条 MQTT 消息：解析 JSON → 写入热表
     *
     * @param topic   消息来源 topic（通配符订阅时用于区分业务来源）
     * @param payload 消息报文（UTF-8 JSON 字符串）
     */
    public void handle(String topic, String payload) {
        try {
            // ① 解析报文
            JsonNode node = objectMapper.readTree(payload);

            // ② 提取字段：createTs 缺失/为空则取当前时间
            String tsText = node.path("createTs").asText(null);
            LocalDateTime createTs = (tsText == null || tsText.isBlank())
                    ? LocalDateTime.now()
                    : LocalDateTime.parse(tsText, TS_FORMATTER);
            String content = node.path("content").asText(null);

            // ③ 组装实体并落库热表
            DataLog dataLog = new DataLog();
            dataLog.setCreateTs(createTs);
            dataLog.setContent(content);
            int rows = dataLogService.insertHot(dataLog);

            log.info("MQTT 消息入库 topic={}, rows={}, id={}, createTs={}",
                    topic, rows, dataLog.getId(), createTs);
        } catch (Exception e) {
            // 解析/入库失败只记日志，避免异常传导回 Paho 投递线程导致断订阅
            log.error("MQTT 消息处理失败 topic={}, payload={}", topic, payload, e);
        }
    }
}
