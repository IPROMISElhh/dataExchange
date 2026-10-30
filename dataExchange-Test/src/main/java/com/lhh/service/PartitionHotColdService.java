package com.lhh.service;

import com.lhh.mapper.PartitionVisitMapper;
import com.lhh.pojo.HotColdConfig;
import com.lhh.pojo.PartitionVisitStat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 冷热分区调度服务（双表方案 A：对称分区 + 行级搬迁）
 * <p>
 * 职责：<b>只做冷热判定</b>——定时扫描热表和冷表各分区的访问量，根据阈值判定冷热，
 * 再委托 {@link PartitionMigrationService} 在两张表之间做行级数据搬迁（判定与迁移解耦）。
 * </p>
 * <p>
 * 本服务还负责 ensureFuturePartitions：每天为热表和冷表<b>对称地</b>向前补齐日分区，
 * 保证两表分区布局始终一一对应，从而让双向搬迁都能落到已存在的目标分区上。
 * </p>
 * <p>
 * 兼容 MySQL 和 OceanBase（MySQL 租户），仅使用标准分区 DDL 与 INSERT SELECT / DELETE。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 14:32
 */

@Slf4j
@Service
public class PartitionHotColdService {

    /** 分区名日期格式化器，如 20261027 */
    private static final DateTimeFormatter PARTITION_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** LESS THAN 日期格式化器，如 2026-10-28 */
    private static final DateTimeFormatter LESS_THAN_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 分区名前缀 */
    private static final String PARTITION_PREFIX = "p";

    /** 冷热分离配置（热表名、冷表名、阈值、冷却时间、预建天数等） */
    @Autowired
    private HotColdConfig hotColdConfig;

    /** 分区访问 Mapper，提供统计与分区查询操作 */
    @Autowired
    private PartitionVisitMapper partitionVisitMapper;

    /** 分区数据迁移服务（行级搬迁，带事务） */
    @Autowired
    private PartitionMigrationService partitionMigrationService;

    /**
     * 为热表和冷表对称地向前补齐日分区：[今天, 今天 + horizonDays)。
     * <p>
     * 逐日期升序 ADD PARTITION，因为两表都不含 MAXVALUE 兜底分区，且新分区边界始终大于
     * 当前最大分区边界，所以 ADD 合法。已存在的分区跳过。这样保证两表分区布局一一对应。
     * </p>
     */
    public void ensureFuturePartitions() {
        int horizon = hotColdConfig.getHorizonDays() == null ? 7 : hotColdConfig.getHorizonDays();
        LocalDate today = LocalDate.now();
        String[] tables = {hotColdConfig.getTableName(), hotColdConfig.getColdTableName()};

        for (String tableName : tables) {
            // 按日期升序遍历，保证新分区边界递增，ADD PARTITION 合法
            for (int i = 0; i < horizon; i++) {
                LocalDate date = today.plusDays(i);
                String partName = PARTITION_PREFIX + date.format(PARTITION_DATE_FMT);

                if (partitionVisitMapper.countPartition(tableName, partName) > 0) {
                    continue;
                }

                Map<String, Object> param = new HashMap<>();
                param.put("tableName", tableName);
                param.put("partitionName", partName);
                param.put("lessThanDate", date.plusDays(1).format(LESS_THAN_FMT));
                try {
                    partitionVisitMapper.addPartition(param);
                    log.info("表 {} 自动补齐分区: {} (LESS THAN {})", tableName, partName, date.plusDays(1));
                } catch (Exception e) {
                    log.error("表 {} 补齐分区 {} 失败", tableName, partName, e);
                }
            }
        }
    }

    /**
     * 判断分区是否在冷却期内
     * 冷却期 = 距上次迁移不足 coolDownSeconds 秒
     *
     * @param stat 分区统计记录
     * @return true=在冷却期内（不应迁移），false=不在冷却期（可以迁移）
     */
    private boolean isInCoolDown(PartitionVisitStat stat) {
        if (stat == null || stat.getLastMovedTime() == null) {
            return false;
        }
        long secondsSinceMoved = ChronoUnit.SECONDS.between(stat.getLastMovedTime(), LocalDateTime.now());
        return secondsSinceMoved < hotColdConfig.getCoolDownSeconds();
    }

    /**
     * 核心调度逻辑：扫描热表和冷表所有分区，根据访问量判定冷热并委托迁移服务搬迁
     * <p>
     * 执行流程：
     * 1. 为热表和冷表对称补齐未来分区
     * 2. 扫描热表分区：跳过空分区/今日及未来分区 → 读访问量 → 冷却期检查 → 访问量 <= 冷阈值 → 迁到冷表
     * 3. 扫描冷表分区：跳过空分区 → 读访问量 → 冷却期检查 → 访问量 >= 热阈值 → 迁回热表
     * 4. 本轮全部结束后统一 resetAllVisitCount 清零访问量（不动 last_moved_time，保留冷却期）
     * </p>
     */
    public void checkAndRunHotColdSchedule() {
        // ① 两表对称补齐未来分区
        ensureFuturePartitions();

        String hotTable = hotColdConfig.getTableName();
        String coldTable = hotColdConfig.getColdTableName();
        // 今天分区名：今天及以后的分区持续有写入/尚无历史数据，不参与迁冷
        String todayPart = PARTITION_PREFIX + LocalDate.now().format(PARTITION_DATE_FMT);

        // ② 扫描热表分区，挑出冷分区迁到冷表
        List<Map<String, Object>> hotPartitions = partitionVisitMapper.listAllTablePartition(hotTable);
        for (Map<String, Object> row : hotPartitions) {
            Object partNameObj = row.get("partition_name");
            if (partNameObj == null) {
                log.warn("热表发现 partition_name 为空的记录，跳过");
                continue;
            }
            String partName = partNameObj.toString();
            // 只降级"过去"的分区（分区名定宽 pYYYYMMDD，字符串比较等价于日期比较）
            if (partName.compareTo(todayPart) >= 0) {
                continue;
            }
            // 空分区（数据已在冷表的镜像分区）直接跳过，避免反复迁冷并误清冷表统计
            if (partitionVisitMapper.countRowsInPartition(hotTable, partName) == 0) {
                continue;
            }

            PartitionVisitStat stat = partitionVisitMapper.getStat(hotTable, partName);
            // 访问量按 (表, 分区) 双键统计，热表/冷表同一分区各自独立计数
            long visitCount = (stat != null) ? stat.getVisitCount() : 0L;

            // 冷却期检查（迁到冷表后登记的是冷表统计，热表扫描不受冷却限制，反之同理）
            if (isInCoolDown(stat)) {
                log.debug("热表分区 {} 处于冷却期(上次迁移: {})，跳过本轮判定", partName, stat.getLastMovedTime());
                continue;
            }

            // 冷分区判定：访问量 <= 冷阈值 → 迁到冷表
            if (visitCount <= hotColdConfig.getColdThreshold()) {
                partitionMigrationService.moveToCold(partName);
            }

        }

        // ③ 扫描冷表分区，挑出热分区迁回热表
        List<Map<String, Object>> coldPartitions = partitionVisitMapper.listAllTablePartition(coldTable);
        for (Map<String, Object> row : coldPartitions) {
            Object partNameObj = row.get("partition_name");
            if (partNameObj == null) {
                log.warn("冷表发现 partition_name 为空的记录，跳过");
                continue;
            }
            String partName = partNameObj.toString();

            // 空分区（数据已迁回热表的镜像分区，或尚未使用的未来分区）直接跳过
            if (partitionVisitMapper.countRowsInPartition(coldTable, partName) == 0) {
                continue;
            }

            // 读冷表维度的统计记录（与热表扫描按表各自独立计数）
            PartitionVisitStat stat = partitionVisitMapper.getStat(coldTable, partName);
            long visitCount = (stat != null) ? stat.getVisitCount() : 0L;

            if (isInCoolDown(stat)) {
                log.info("冷表分区 {} 处于冷却期(上次迁移: {})，跳过本轮判定", partName, stat.getLastMovedTime());
                continue;
            }

            // 热分区判定：访问量 >= 热阈值 → 迁回热表
            if (visitCount >= hotColdConfig.getHotThreshold()) {
                partitionMigrationService.moveToHot(partName);
            }
        }

        // ④ 本轮全部分区判定/迁移结束后，统一将 visit_count 归零（不动 last_moved_time，保留冷却期）
        partitionVisitMapper.resetAllVisitCount();

        log.info("本轮冷热调度完成（双表行级搬迁）");
    }
}
