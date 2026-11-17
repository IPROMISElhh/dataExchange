package com.lhh.mapper;

import com.lhh.pojo.Consolidation;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 固结数据 Mapper 接口（双表方案，type=2）
 * <p>
 * 分别查询热表（consolidation_hot）和冷表（consolidation_cold），
 * 业务层通过 Service 合并两张表的结果。
 * 热表插入使用 INSERT IGNORE，借助唯一键 device_id + seq + collect_time 判重，
 * 兼容 MQTT QoS 1 重复投递。
 * </p>
 *
 * @Author lhh
 * Create 2026/11/16
 */
public interface ConsolidationMapper {

    // ==================== 热表写入 ====================

    /** 向热表插入一条固结数据（回写自增主键 id，重复数据自动忽略） */
    int insertHot(Consolidation consolidation);

    // ==================== 热表查询 ====================

    /** 按采集时间范围查询热表 */
    List<Consolidation> selectHotByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按 ID 查询热表 */
    Consolidation selectHotById(@Param("id") Long id);

    // ==================== 冷表查询 ====================

    /** 按采集时间范围查询冷表 */
    List<Consolidation> selectColdByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按 ID 查询冷表 */
    Consolidation selectColdById(@Param("id") Long id);
}
