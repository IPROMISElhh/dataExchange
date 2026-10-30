package com.lhh.pojo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分区访问统计实体类
 * <p>
 * 对应 partition_visit_stat 表，记录每个业务表分区（热表或冷表）的访问量统计和上次迁移时间。
 * 冷热调度任务根据 visit_count 判定分区冷热，根据 last_moved_time 判断是否在冷却期内。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 15:55
 */

@Data  // Lombok: 自动生成 getter/setter/toString/equals/hashCode
public class PartitionVisitStat {

    /** 主键，自增 */
    private Long id;

    /** 业务表名，如 data_log_hot 或 data_log_cold */
    private String tableName;

    /** 分区名，如 p20261026 */
    private String partitionName;

    /** 本周期内的访问次数，每次查询/写入该分区时 +1 */
    private Long visitCount;

    /** 上次迁移时间，用于冷却期判断，避免分区被频繁迁移 */
    private LocalDateTime lastMovedTime;
}
