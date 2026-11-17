package com.lhh.service;

import com.lhh.mapper.ConsolidationMapper;
import com.lhh.pojo.Consolidation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 固结数据业务服务（双表方案，type=2）
 * <p>
 * 封装热表（consolidation_hot）和冷表（consolidation_cold）的查询操作，
 * 每次范围查询两张表都查、合并结果，并自动调用 markVisit 记录各分区访问计数，
 * 供冷热调度任务判定各分区的热度。
 * </p>
 * <p>
 * 访问记录机制：根据实际命中的数据所在表 + 分区来 +1，
 * 热表命中的分区记 (consolidation_hot, 分区)，冷表命中的分区记 (consolidation_cold, 分区)，
 * 热表/冷表同一分区各自独立计数，这样调度层能按表统计冷热。
 * </p>
 *
 * @Author lhh
 * Create 2026/11/16
 */
@Slf4j
@Service
public class ConsolidationService {

    /** 固结热表名（分区访问统计的 table_name） */
    private static final String HOT_TABLE = "consolidation_hot";

    /** 固结冷表名（分区访问统计的 table_name） */
    private static final String COLD_TABLE = "consolidation_cold";

    /** 固结 Mapper，分别提供热表、冷表的查询操作 */
    @Autowired
    private ConsolidationMapper consolidationMapper;

    /** 分区访问记录服务，提供 visit_count + 1 的 upsert 操作 */
    @Autowired
    private PartitionVisitService partitionVisitService;

    /**
     * 向热表写入一条固结数据（MQTT 明文帧落库入口）
     * <p>
     * 新增数据统一进热表，collect_time 落入对应日分区（依赖预建分区机制）；
     * 底层 INSERT IGNORE 借助唯一键 device_id + seq + collect_time 判重，兼容 QoS 1 重复投递；
     * 写入不记访问计数，热度由后续查询逐步累积，交由冷热调度判定是否迁冷。
     * </p>
     *
     * @param consolidation 固结数据（createTs 为空时自动填充为当前时间，插入后回写自增主键 id）
     * @return 影响行数（重复数据被忽略时返回 0）
     */
    public int insertHot(Consolidation consolidation) {
        if (consolidation.getCreateTs() == null) {
            consolidation.setCreateTs(LocalDateTime.now());
        }
        int rows = consolidationMapper.insertHot(consolidation);
        log.info("固结热表写入 id={}, deviceId={}, seq={}, rows={}",
                consolidation.getId(), consolidation.getDeviceId(), consolidation.getSeq(), rows);
        return rows;
    }

    /**
     * 按采集时间范围查询固结数据：冷热表一起查、合并、按时间倒序，并记录涉及分区的访问计数
     * <p>
     * 流程：
     * 1. 分别查询热表和冷表（各自自动做分区裁剪）
     * 2. 按命中的数据所在表 + 分区，逐个 markVisit（热表/冷表分别计入各自统计）
     * 3. 合并两张表结果，按 collect_time 倒序排序
     * </p>
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 冷热表合并后的固结数据列表
     */
    public List<Consolidation> queryByDateRange(LocalDateTime start, LocalDateTime end) {
        // ① 冷热表一起查询
        List<Consolidation> hotList = consolidationMapper.selectHotByDateRange(start, end);
        List<Consolidation> coldList = consolidationMapper.selectColdByDateRange(start, end);

        // ② 记录分区访问：按数据时间解析其「真实所在分区」再计数，热/冷表各自独立
        //    RANGE 首个分区无下界，历史数据会落入首个分区，不能按日期直接推分区名
        partitionVisitService.markVisitByTimes(HOT_TABLE, hotList, Consolidation::getCollectTime);
        partitionVisitService.markVisitByTimes(COLD_TABLE, coldList, Consolidation::getCollectTime);

        // ③ 合并结果并按采集时间倒序
        List<Consolidation> result = new ArrayList<>(hotList.size() + coldList.size());
        result.addAll(hotList);
        result.addAll(coldList);
        result.sort(Comparator.comparing(Consolidation::getCollectTime).reversed());

        log.info("固结范围查询 [{}, {}], 热表 {} 条 + 冷表 {} 条, 合并 {} 条",
                start, end, hotList.size(), coldList.size(), result.size());
        return result;
    }

    /**
     * 按 ID 查询单条固结数据：先查热表，未命中再查冷表，并记录对应分区的访问计数
     *
     * @param id 主键 ID
     * @return 固结数据，两张表都不存在返回 null
     */
    public Consolidation queryById(Long id) {
        // ① 先查热表
        Consolidation entity = consolidationMapper.selectHotById(id);
        if (entity != null) {
            recordVisitById(HOT_TABLE, entity.getCollectTime());
            log.info("固结按ID查询 id={}, 命中热表 {}", id, HOT_TABLE);
            return entity;
        }

        // ② 热表未命中，再查冷表
        entity = consolidationMapper.selectColdById(id);
        if (entity != null) {
            recordVisitById(COLD_TABLE, entity.getCollectTime());
            log.info("固结按ID查询 id={}, 命中冷表 {}", id, COLD_TABLE);
        }
        return entity;
    }

    /**
     * 根据命中数据的 collectTime（分区键）解析其真实所在分区，记录该表该分区的一次访问
     */
    private void recordVisitById(String tableName, LocalDateTime collectTime) {
        partitionVisitService.markVisitByTime(tableName, collectTime);
    }
}
