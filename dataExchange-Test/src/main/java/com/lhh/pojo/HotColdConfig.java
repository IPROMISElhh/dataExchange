package com.lhh.pojo;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 冷热分离配置类
 * <p>
 * 绑定 application.yaml 中 hotcold.* 前缀的配置项，
 * 包含热表名、冷表名、冷热判定阈值、冷却时间等参数。
 * Bean 初始化时自动校验参数合法性。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 14:30
 */

@Data
@Component
@ConfigurationProperties(prefix = "hotcold")
public class HotColdConfig {

    /** 热数据表名（按天分区，存近期高频数据），如 data_log_hot */
    private String tableName;

    /** 冷数据表名（按天分区，存历史低频数据），如 data_log_cold */
    private String coldTableName;

    /** 热阈值：本周期访问量 >= 该值则判定为热分区 */
    private Integer hotThreshold;

    /** 冷阈值：本周期访问量 <= 该值则判定为冷分区 */
    private Integer coldThreshold;

    /** 冷却时间（秒）：分区迁移后至少等待该时长才允许再次迁移，防止频繁抖动 */
    private Integer coolDownSeconds;

    /** 分区预建前瞻天数：每日为热/冷两表向前补齐 [今天, 今天+horizon) 的对称日分区 */
    private Integer horizonDays = 7;

    /**
     * Bean 初始化后自动执行参数校验
     */
    @PostConstruct
    public void validate() {
        if (hotThreshold != null && coldThreshold != null && hotThreshold <= coldThreshold) {
            throw new IllegalArgumentException(
                    "hotThreshold(" + hotThreshold + ")必须大于coldThreshold(" + coldThreshold + ")");
        }
        if (coolDownSeconds != null && coolDownSeconds < 0) {
            throw new IllegalArgumentException("coolDownSeconds不能为负数, 当前值: " + coolDownSeconds);
        }
        if (horizonDays != null && horizonDays < 1) {
            throw new IllegalArgumentException("horizonDays必须 >= 1, 当前值: " + horizonDays);
        }
    }
}
