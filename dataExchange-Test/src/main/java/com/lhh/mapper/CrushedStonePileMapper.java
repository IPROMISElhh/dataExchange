package com.lhh.mapper;

import com.lhh.pojo.CrushedStonePile;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 碎石桩数据 Mapper 接口（双表方案，type=0）
 * <p>
 * 分别查询热表（crushed_stone_pile_hot）和冷表（crushed_stone_pile_cold），
 * 业务层通过 Service 合并两张表的结果。
 * 热表插入使用 INSERT IGNORE，借助唯一键 device_id + seq + collect_time 判重，
 * 兼容 MQTT QoS 1 重复投递。
 * </p>
 *
 * @Author lhh
 * Create 2026/11/16
 */
public interface CrushedStonePileMapper {

    // ==================== 热表写入 ====================

    /** 向热表插入一条碎石桩数据（回写自增主键 id，重复数据自动忽略） */
    int insertHot(CrushedStonePile crushedStonePile);

    // ==================== 热表查询 ====================

    /** 按采集时间范围查询热表 */
    List<CrushedStonePile> selectHotByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按 ID 查询热表 */
    CrushedStonePile selectHotById(@Param("id") Long id);

    // ==================== 冷表查询 ====================

    /** 按采集时间范围查询冷表 */
    List<CrushedStonePile> selectColdByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 按 ID 查询冷表 */
    CrushedStonePile selectColdById(@Param("id") Long id);
}
