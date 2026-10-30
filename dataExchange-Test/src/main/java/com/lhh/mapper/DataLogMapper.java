package com.lhh.mapper;

import com.lhh.pojo.DataLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据日志 Mapper 接口（双表方案）
 * <p>
 * 分别查询热表（data_log_hot）和冷表（data_log_cold），
 * 业务层通过 DataLogService 合并两张表的结果。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 20:00
 */
public interface DataLogMapper {

    // ==================== 热表写入 ====================

    /** 向热表插入一条数据日志（回写自增主键 id） */
    int insertHot(DataLog dataLog);

    // ==================== 热表查询 ====================

    /** 按时间范围查询热表 */
    List<DataLog> selectHotByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按 ID 查询热表 */
    DataLog selectHotById(@Param("id") Long id);

    // ==================== 冷表查询 ====================

    /** 按时间范围查询冷表 */
    List<DataLog> selectColdByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按 ID 查询冷表 */
    DataLog selectColdById(@Param("id") Long id);
}
