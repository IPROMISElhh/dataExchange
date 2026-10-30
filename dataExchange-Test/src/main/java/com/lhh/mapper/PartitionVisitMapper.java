package com.lhh.mapper;

import com.lhh.pojo.PartitionVisitStat;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 分区访问 Mapper 接口（双表方案）
 * <p>
 * 提供分区访问统计、冷热调度、双表迁移等数据库操作。
 * 热表（data_log_hot）和冷表（data_log_cold）均为分区表，
 * 迁移通过 INSERT SELECT + DELETE 行级搬迁实现。
 * 访问统计（partition_visit_stat）按 (table_name, partition_name) 双键记录，热表/冷表各自独立计数。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 15:13
 */
public interface PartitionVisitMapper {

    // ==================== 访问统计 ====================

    /**
     * 记录分区访问（upsert），visit_count + 1。
     * 按 (table_name, partition_name) 唯一键计数，热表/冷表各自独立。
     */
    void markVisit(Map<String,Object> param);

    /** 更新目标表指定分区的上次迁移时间为当前时间（upsert，保证冷却期跨表生效） */
    void updateLastMovedTime(Map<String,Object> param);

    /**
     * 一个调度周期迁移全部结束后，一次性将所有分区的 visit_count 归零。
     * 只清访问量、不动 last_moved_time，以保护冷却期记录。
     */
    void resetAllVisitCount();

    /** 查询指定表指定分区的统计记录（访问量 + 上次迁移时间） */
    PartitionVisitStat getStat(@Param("tableName") String tableName, @Param("partitionName") String partitionName);

    // ==================== 分区信息查询 ====================

    /**
     * 查询指定表的所有分区名
     * 从 INFORMATION_SCHEMA.PARTITIONS 获取，排除 p_future 和 p_cold_init。
     */
    List<Map<String,Object>> listAllTablePartition(String tableName);

    /** 检查指定分区是否已存在 */
    int countPartition(@Param("tableName") String tableName, @Param("partitionName") String partitionName);

    /**
     * 统计指定表指定分区的行数。
     * 用于冷热调度跳过空分区：数据只存在于一侧（热或冷），另一侧是空镜像分区，
     * 若不排除会对空分区反复“迁冢”并误清目标表统计。
     */
    int countRowsInPartition(@Param("tableName") String tableName, @Param("partitionName") String partitionName);

    // ==================== 双表迁移操作（行级搬迁）====================

    /**
     * 热→冷迁移：把热表指定分区的数据复制到冷表
     * INSERT IGNORE INTO coldTable SELECT * FROM hotTable PARTITION(partitionName)
     */
    void copyHotToCold(Map<String,Object> param);

    /**
     * 冷→热迁移：把冷表指定分区的数据复制到热表
     * INSERT IGNORE INTO hotTable SELECT * FROM coldTable PARTITION(partitionName)
     */
    void copyColdToHot(Map<String,Object> param);

    /**
     * 删除指定表指定分区内的行数据（保留空分区，不碰分区结构）
     * DELETE FROM tableName PARTITION (partitionName)
     *
     * @return 实际删除的行数，用于迁移的行数守恒校验
     */
    int deleteRowsFromPartition(Map<String,Object> param);

    /**
     * 为指定表新增一个日分区（仅用于每日向前补齐，必须按日期递增调用）
     * ALTER TABLE tableName ADD PARTITION (PARTITION pName VALUES LESS THAN (...))
     * 前提：该表无 MAXVALUE 兜底分区，且新分区边界大于当前最大分区。
     */
    void addPartition(Map<String,Object> param);
}
