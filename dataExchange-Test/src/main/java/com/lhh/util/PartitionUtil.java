package com.lhh.util;
import com.lhh.pojo.DataLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * 分区工具类
 * <p>
 * 提供分区名的计算和解析功能。
 * 分区命名规则：前缀 "p" + 日期 "yyyyMMdd"，如 p20261026 表示 2026年10月26日 的分区。
 * 与 OceanBase 建表时的 PARTITION BY RANGE (TO_DAYS(create_ts)) 对应。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 14:23
 */

public class PartitionUtil {

    /** 日期格式化器，与分区名中的日期部分对应，如 20261026 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 分区名前缀，如 p20261026 中的 "p" */
    private static final String PREFIX = "p";

    /**
     * 根据单条记录的时间获取其对应的分区名
     * 例如：2026-10-26 14:30:00 → "p20261026"
     *
     * @param dataTime 数据的时间
     * @return 分区名，如 p20261026
     */
    public static String getSinglePartitionName(LocalDateTime dataTime) {
        LocalDate date = dataTime.toLocalDate();
        return PREFIX + date.format(FORMATTER);
    }

    /**
     * 根据时间段查询，计算该区间涉及的所有分区名
     * 例如：2026-10-24 到 2026-10-26 → ["p20261024", "p20261025", "p20261026"]
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 分区名列表，包含起止日期之间的所有分区
     * @throws IllegalArgumentException 当 start 晚于 end 时抛出
     */
    public static List<String> calcPartitionNames(LocalDateTime start, LocalDateTime end) {
        // 参数空值保护
        if (start == null || end == null) {
            return Collections.emptyList();
        }
        // 参数合法性校验
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start不能晚于end: start=" + start + ", end=" + end);
        }
        List<String> list = new ArrayList<>();
        LocalDate curr = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();
        // 从起始日期遍历到结束日期，逐天生成对应的分区名
        while (!curr.isAfter(endDate)) {
            list.add(PREFIX + curr.format(FORMATTER));
            curr = curr.plusDays(1);
        }
        return list;
    }

    /**
     * 从一批实体数据中提取去重后的分区名列表
     * 适用于批量查询场景：传入一组 DataLog，直接计算它们涉及哪些分区。
     *
     * @param dataList 数据日志列表
     * @return 去重后的分区名列表
     */
    public static List<String> getDistinctPartitionsFromEntity(List<DataLog> dataList) {
        // 空集合保护
        if (dataList == null || dataList.isEmpty()) {
            return Collections.emptyList();
        }
        // 使用 Set 去重
        Set<String> set = new HashSet<>();
        for (DataLog log : dataList) {
            // 元素空值保护
            if (log != null && log.getCreateTs() != null) {
                set.add(getSinglePartitionName(log.getCreateTs()));
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 根据分区名反推建分区时的 LESS THAN 日期（分区日期 + 1 天）
     * 例如：p20261025 → "2026-10-26"
     * <p>
     * 双表迁移时，目标表新建分区需要用 VALUES LESS THAN (TO_DAYS(该日期))，
     * 该日期即分区名对应日期的次日，保证分区边界与热表一致。
     *
     * @param partitionName 分区名，如 p20261025
     * @return LESS THAN 日期字符串，格式 yyyy-MM-dd
     */
    public static String calcLessThanDate(String partitionName) {
        if (partitionName == null || partitionName.length() < 9 || !partitionName.startsWith(PREFIX)) {
            throw new IllegalArgumentException("非法分区名: " + partitionName);
        }
        // 去掉前缀 p，解析 yyyyMMdd
        LocalDate date = LocalDate.parse(partitionName.substring(PREFIX.length()), FORMATTER);
        // LESS THAN 为分区日期的次日
        return date.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}

