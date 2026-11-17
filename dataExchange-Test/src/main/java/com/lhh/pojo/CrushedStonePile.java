package com.lhh.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 碎石桩数据实体类（type=0）
 * <p>
 * 对应热表 crushed_stone_pile_hot / 冷表 crushed_stone_pile_cold，两张表结构一致，
 * 均按 create_ts 字段按天分区。每条记录根据 create_ts 自动落入对应的日期分区（如 p20261116）。
 * </p>
 * <p>
 * 判重唯一键：device_id + seq + collect_time，用于兼容 MQTT QoS 1 重复投递与设备重启后 seq 重数。
 * 浮点字段按原值存储，不做预先四舍五入，保留与线上一致的有效位。
 * </p>
 *
 * @Author lhh
 * Create 2026/11/16
 */

@Data  // Lombok: 自动生成 getter/setter/toString/equals/hashCode
public class CrushedStonePile {

    /** 主键，自增 */
    private Long id;

    /** 设备编号，由发送端配置，三类数据共用 */
    private String deviceId;

    /** 采集时间（由 collectTime Unix 秒级时间戳转换，东八区基准），也是分区键 */
    private LocalDateTime collectTime;

    /** 帧序号，同一设备从 1 严格递增（设备重启后从 1 重新开始） */
    private Integer seq;

    /** 运行深度(m)，向下为负，原字段名 RunDepth */
    private Float runDepth;

    /** 运行电流(A)，原字段名 RunCurent（已修正拼写） */
    private Float runCurrent;

    /** 运行水压(MPa)，原字段名 RunWaterPress */
    private Float runWaterPress;

    /** 运行重量(kg)，原字段名 RunWeightAdd */
    private Float runWeightAdd;

    /** 运行状态码，原字段名 RunState1 */
    private Integer runState;

    /** 业务类型：0=碎石桩 */
    private Integer type;

    /** 入库时间（审计用，非分区键；分区键为 collectTime） */
    private LocalDateTime createTs;
}
