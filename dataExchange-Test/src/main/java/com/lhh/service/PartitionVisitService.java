package com.lhh.service;

import com.lhh.mapper.PartitionVisitMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 分区访问记录服务
 * <p>
 * 提供分区访问计数功能，业务层每次查询/写入某个分区时调用 markVisit，
 * 该分区的 visit_count 自动 +1，用于后续冷热调度判定。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 14:26
 */

@Slf4j
@Service
public class PartitionVisitService {

    /** 分区访问 Mapper，提供数据库操作 */
    @Autowired
    private PartitionVisitMapper partitionVisitMapper;

    /**
     * 记录一次分区访问（visit_count + 1）
     * <p>
     * 双表方案下访问统计按 (table_name, partition_name) 唯一键区分，
     * 热表和冷表的同一分区各自独立计数，因此需要显式传入所属表名。
     * 底层使用 INSERT ... ON DUPLICATE KEY UPDATE 实现 upsert：
     * - 记录不存在时插入一条新记录（visit_count = 1）
     * - 记录已存在时 visit_count 自增 1
     *
     * @param tableName     所属业务表名（热表或冷表），如 data_log_hot
     * @param partitionName 分区名，如 p20261026
     */
    public void markVisit(String tableName, String partitionName) {
        // 组装参数：表名 + 分区名
        Map<String,Object> param = new HashMap<>();
        param.put("tableName",tableName);
        param.put("partitionName",partitionName);
        // 执行 upsert
        partitionVisitMapper.markVisit(param);
        log.info("表 {} 分区 {} visit_count +1", tableName, partitionName);
    }

    /**
     * 按数据时间记录一次分区访问：先解析该时间「实际落入的分区」，再对该分区 +1。
     * <p>
     * 解决按日期格式化推分区名的缺陷：RANGE 首个分区无下界，
     * 早于窗口起始的历史数据（如 2026-08-13）实际落在首个分区 p20261116，而非 p20260813。
     * </p>
     *
     * @param tableName 业务表名
     * @param dataTime  数据时间（分区键取值），为空则忽略
     */
    public void markVisitByTime(String tableName, LocalDateTime dataTime) {
        if (dataTime == null) {
            return;
        }
        String partition = partitionVisitMapper.resolvePartitionByTime(tableName, dataTime);
        if (partition == null) {
            log.warn("表 {} 中时间 {} 超出所有分区上界，无法定位分区，跳过访问记录", tableName, dataTime);
            return;
        }
        markVisit(tableName, partition);
    }

    /**
     * 批量按数据时间记录分区访问（泛型）。
     * <p>
     * 先按日期去重以减少解析查询，再把各日期解析为「真实分区名」并去重
     * （多个日期可能落入同一分区，如早于首个分区下界的历史数据都归入首个分区），最后逐个 +1。
     * </p>
     *
     * @param tableName     业务表名
     * @param list          实体列表
     * @param timeExtractor 从实体提取数据时间（分区键）的函数，如 CrushedStonePile::getCollectTime
     * @param <T>           实体类型
     */
    public <T> void markVisitByTimes(String tableName, List<T> list, Function<T, LocalDateTime> timeExtractor) {
        if (list == null || list.isEmpty()) {
            return;
        }
        // ① 按日期去重，减少解析查询次数
        Set<LocalDate> distinctDays = new HashSet<>();
        for (T item : list) {
            if (item != null) {
                LocalDateTime time = timeExtractor.apply(item);
                if (time != null) {
                    distinctDays.add(time.toLocalDate());
                }
            }
        }
        // ② 解析每个日期实际落入的分区并去重
        Set<String> partitions = new HashSet<>();
        for (LocalDate day : distinctDays) {
            String partition = partitionVisitMapper.resolvePartitionByTime(tableName, day.atStartOfDay());
            if (partition != null) {
                partitions.add(partition);
            }
        }
        // ③ 逐个真实分区 +1
        for (String partition : partitions) {
            markVisit(tableName, partition);
        }
    }

}

