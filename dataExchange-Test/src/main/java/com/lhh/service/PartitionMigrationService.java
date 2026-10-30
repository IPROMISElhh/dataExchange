package com.lhh.service;

import com.lhh.mapper.PartitionVisitMapper;
import com.lhh.pojo.HotColdConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 分区数据迁移服务（双表方案 A：行级搬迁）
 * <p>
 * 只负责「在热表和冷表之间搬运某个分区的行数据」，不涉及冷热判定，
 * 冷热判定由 {@link PartitionHotColdService} 完成后调用本服务。
 * </p>
 * <p>
 * 由于热表和冷表预先保持完全相同的分区布局（对称分区），且目标镜像分区迁移前必为空，
 * 迁移流程为「复制 → 行数校验 → 删除 → 行数校验」：
 * 1. countRowsInPartition(源, 分区)  —— 记录应搬运行数基准 srcCount
 * 2. INSERT IGNORE INTO 目标 SELECT * FROM 源 PARTITION(分区)  —— 复制行
 * 3. countRowsInPartition(目标, 分区) 必须 == srcCount —— 防 INSERT IGNORE 静默丢行/列错位
 * 4. DELETE FROM 源 PARTITION(分区)，返回行数必须 == srcCount —— 防源被并发修改
 * 全程不新建/删除分区，规避了 RANGE 分区只能按边界递增操作的限制，双向皆可安全迁移。
 * </p>
 * <p>
 * 独立成 Bean 是为了让 {@code @Transactional} 生效（被外部类调用才走 Spring 代理）。
 * 任一行数校验不通过即抛异常，复制与删除整体回滚：要么两边都成功，要么回到迁移前状态，
 * 绝不会出现数据双份或两空（数据守恒）。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 14:32
 */
@Slf4j
@Service
public class PartitionMigrationService {

    @Autowired
    private HotColdConfig hotColdConfig;

    @Autowired
    private PartitionVisitMapper partitionVisitMapper;

    /**
     * 热→冷：把热表指定分区的数据搬到冷表（原子操作）
     *
     * @param partitionName 分区名，如 p20261024
     * @return true=搬迁成功，false=失败（已回滚，源数据仍在热表）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean moveToCold(String partitionName) {
        String hotTable = hotColdConfig.getTableName();
        String coldTable = hotColdConfig.getColdTableName();

        // ① 迁移前统计源（热表）分区行数，作为「应搬运行数」基准
        int srcCount = partitionVisitMapper.countRowsInPartition(hotTable, partitionName);
        if (srcCount == 0) {
            log.info("热表分区 {} 无数据，跳过迁冷", partitionName);
            return false;
        }

        // ② 复制热表该分区数据到冷表
        Map<String, Object> copyParam = new HashMap<>();
        copyParam.put("hotTableName", hotTable);
        copyParam.put("coldTableName", coldTable);
        copyParam.put("partitionName", partitionName);
        partitionVisitMapper.copyHotToCold(copyParam);

        // ③ 行数守恒校验（复制侧）：冷表镜像分区迁移前应为空，复制后必须恰好持有 srcCount 行。
        //    若不等于 srcCount，说明 INSERT IGNORE 静默丢弃了行（列错位/语法不支持/约束失败）
        //    或目标分区存在非预期残留，此时绝不能删源数据，抛异常整体回滚，避免丢数。
        int dstCount = partitionVisitMapper.countRowsInPartition(coldTable, partitionName);
        if (dstCount != srcCount) {
            throw new IllegalStateException(String.format(
                    "迁冷复制校验失败：热表分区 %s 应搬运 %d 行，冷表实际 %d 行（疑似 INSERT IGNORE 静默丢行），回滚",
                    partitionName, srcCount, dstCount));
        }

        // ④ 删除热表该分区的行数据（保留空分区），删除数必须等于源行数，否则视为并发修改，回滚
        Map<String, Object> delParam = new HashMap<>();
        delParam.put("tableName", hotTable);
        delParam.put("partitionName", partitionName);
        int deleted = partitionVisitMapper.deleteRowsFromPartition(delParam);
        if (deleted != srcCount) {
            throw new IllegalStateException(String.format(
                    "迁冷删除校验失败：热表分区 %s 预期删除 %d 行，实际删除 %d 行（源数据被并发修改），回滚",
                    partitionName, srcCount, deleted));
        }

        // ⑤ 登记冷表该分区的迁移时间并清零访问量（upsert 到目标表，冷却期跨表生效）
        Map<String, Object> movedParam = new HashMap<>();
        movedParam.put("tableName", coldTable);
        movedParam.put("partitionName", partitionName);
        partitionVisitMapper.updateLastMovedTime(movedParam);

        log.info("分区 {} 数据已从热表 {} 搬到冷表 {}，搬运行数={}（行数校验通过）", partitionName, hotTable, coldTable, srcCount);
        return true;
    }

    /**
     * 冷→热：把冷表指定分区的数据搬回热表（原子操作）
     *
     * @param partitionName 分区名
     * @return true=搬迁成功，false=失败（已回滚，源数据仍在冷表）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean moveToHot(String partitionName) {
        String hotTable = hotColdConfig.getTableName();
        String coldTable = hotColdConfig.getColdTableName();

        // 前置校验：目标热表分区必须存在（对称分区应始终成立，防御性检查）
        if (partitionVisitMapper.countPartition(hotTable, partitionName) == 0) {
            log.warn("热表缺少分区 {}，无法安全迁回（历史中间分区不可 ADD），跳过", partitionName);
            return false;
        }

        // ① 迁移前统计源（冷表）分区行数，作为「应搬运行数」基准
        int srcCount = partitionVisitMapper.countRowsInPartition(coldTable, partitionName);
        if (srcCount == 0) {
            log.info("冷表分区 {} 无数据，跳过迁热", partitionName);
            return false;
        }

        // ② 复制冷表该分区数据到热表
        Map<String, Object> copyParam = new HashMap<>();
        copyParam.put("hotTableName", hotTable);
        copyParam.put("coldTableName", coldTable);
        copyParam.put("partitionName", partitionName);
        partitionVisitMapper.copyColdToHot(copyParam);

        // ③ 行数守恒校验（复制侧）：热表镜像分区迁移前应为空，复制后必须恰好持有 srcCount 行。
        //    若不等于 srcCount，说明 INSERT IGNORE 静默丢弃了行或目标分区有非预期残留，抛异常回滚。
        int dstCount = partitionVisitMapper.countRowsInPartition(hotTable, partitionName);
        if (dstCount != srcCount) {
            throw new IllegalStateException(String.format(
                    "迁热复制校验失败：冷表分区 %s 应搬运 %d 行，热表实际 %d 行（疑似 INSERT IGNORE 静默丢行），回滚",
                    partitionName, srcCount, dstCount));
        }

        // ④ 删除冷表该分区的行数据（保留空分区），删除数必须等于源行数，否则视为并发修改，回滚
        Map<String, Object> delParam = new HashMap<>();
        delParam.put("tableName", coldTable);
        delParam.put("partitionName", partitionName);
        int deleted = partitionVisitMapper.deleteRowsFromPartition(delParam);
        if (deleted != srcCount) {
            throw new IllegalStateException(String.format(
                    "迁热删除校验失败：冷表分区 %s 预期删除 %d 行，实际删除 %d 行（源数据被并发修改），回滚",
                    partitionName, srcCount, deleted));
        }

        // ⑤ 登记热表该分区的迁移时间并清零访问量（upsert 到目标表，冷却期跨表生效）
        Map<String, Object> movedParam = new HashMap<>();
        movedParam.put("tableName", hotTable);
        movedParam.put("partitionName", partitionName);
        partitionVisitMapper.updateLastMovedTime(movedParam);

        log.info("分区 {} 数据已从冷表 {} 搬回热表 {}，搬运行数={}（行数校验通过）", partitionName, coldTable, hotTable, srcCount);
        return true;
    }
}
