package com.lhh.pojo;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 冷热分离配置类
 * <p>
 * 绑定 application.yaml 中 hotcold.* 前缀的配置项。
 * 支持多张业务表（碎石桩 / 防渗墙 / 固结）参与冷热调度：
 * tables 为「热表 + 冷表」表对清单，阈值 / 冷却时间 / 前瞻天数为全局共享参数。
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

    /** 参与冷热调度的表清单：每项为一对「热表 + 冷表」，两表须保持对称分区布局 */
    private List<TablePair> tables;

    /** 热阈值：本周期访问量 >= 该值则判定为热分区 */
    private Integer hotThreshold;

    /** 冷阈值：本周期访问量 <= 该值则判定为冷分区 */
    private Integer coldThreshold;

    /** 冷却时间（秒）：分区迁移后至少等待该时长才允许再次迁移，防止频繁抖动 */
    private Integer coolDownSeconds;

    /** 分区预建前瞻天数：每日为每张表的热/冷两表向前补齐 [今天, 今天+horizon) 的对称日分区 */
    private Integer horizonDays = 7;

    /**
     * Bean 初始化后自动执行参数校验
     */
    @PostConstruct
    public void validate() {
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("hotcold.tables 不能为空，至少配置一对热表/冷表");
        }
        for (TablePair pair : tables) {
            if (pair == null
                    || pair.getHotTableName() == null || pair.getHotTableName().isBlank()
                    || pair.getColdTableName() == null || pair.getColdTableName().isBlank()) {
                throw new IllegalArgumentException(
                        "hotcold.tables 中存在非法表对，hot-table-name 与 cold-table-name 均不可为空");
            }
        }
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

    /**
     * 冷热表对：描述一张业务表的热表与冷表名称（两表对称分区）
     */
    @Data
    public static class TablePair {

        /** 热数据表名（按天分区，存近期高频数据），如 crushed_stone_pile_hot */
        private String hotTableName;

        /** 冷数据表名（按天分区，存历史低频数据），如 crushed_stone_pile_cold */
        private String coldTableName;
    }
}
