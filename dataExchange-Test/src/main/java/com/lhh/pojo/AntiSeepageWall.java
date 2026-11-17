package com.lhh.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 防渗墙数据实体类（type=1）
 * <p>
 * 对应热表 anti_seepage_wall_hot / 冷表 anti_seepage_wall_cold，两张表结构一致，
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
public class AntiSeepageWall {

    /** 主键，自增 */
    private Long id;

    /** 设备编号，由发送端配置，三类数据共用 */
    private String deviceId;

    /** 采集时间（由 collectTime Unix 秒级时间戳转换，东八区基准），也是分区键 */
    private LocalDateTime collectTime;

    /** 帧序号，同一设备从 1 严格递增（设备重启后从 1 重新开始） */
    private Integer seq;

    /** X轴角度(°)，原字段名 角度X */
    private Float angleX;

    /** Y轴角度(°)，原字段名 角度Y */
    private Float angleY;

    /** 主油缸位移(m)，原字段名 主油缸_17 */
    private Float mainCylinder;

    /** 副油缸位移(m)，原字段名 副油缸_18 */
    private Float auxCylinder;

    /** 状态码，原字段名 state */
    private Integer state;

    /** 业务类型：1=防渗墙 */
    private Integer type;

    /** 入库时间（审计用，非分区键；分区键为 collectTime） */
    private LocalDateTime createTs;
}
