package com.lhh.service;

import com.lhh.mapper.DataLogMapper;
import com.lhh.pojo.HotColdConfig;
import com.lhh.pojo.DataLog;
import com.lhh.util.PartitionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 数据日志业务服务（双表方案）
 * <p>
 * 封装热表（data_log_hot）和冷表（data_log_cold）的查询操作，
 * 每次查询两张表都查、合并结果，并自动调用 markVisit 记录各分区访问计数，
 * 供冷热调度任务判定各分区的热度。
 * </p>
 * <p>
 * 访问记录机制：根据实际命中的数据所在表 + 分区来 +1，
 * 即热表命中的分区记 (data_log_hot, 分区)，冷表命中的分区记 (data_log_cold, 分区)，
 * 热表/冷表同一分区各自独立计数，这样调度层能按表统计冷热。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 20:00
 */
@Slf4j
@Service
public class DataLogService {

    /** 数据日志 Mapper，分别提供热表、冷表的查询操作 */
    @Autowired
    private DataLogMapper dataLogMapper;

    /** 分区访问记录服务，提供 visit_count + 1 的 upsert 操作 */
    @Autowired
    private PartitionVisitService partitionVisitService;

    /** 冷热分离配置，获取热表名、冷表名 */
    @Autowired
    private HotColdConfig hotColdConfig;

    /**
     * 向热表写入一条数据日志（MQTT 消息落库等新增数据入口）
     * <p>
     * 新增数据统一进热表，create_ts 落入对应的日分区（依赖预建分区机制）；
     * 写入不记访问计数，热度由后续查询逐步累积，交由冷热调度判定是否迁冷。
     * </p>
     *
     * @param dataLog 数据日志（createTs 必填，插入后回写自增主键 id）
     * @return 影响行数
     */
    public int insertHot(DataLog dataLog) {
        if (dataLog.getCreateTs() == null) {
            dataLog.setCreateTs(LocalDateTime.now());
        }
        int rows = dataLogMapper.insertHot(dataLog);
        log.info("热表写入 id={}, createTs={}, rows={}", dataLog.getId(), dataLog.getCreateTs(), rows);
        return rows;
    }

    /**
     * 按时间范围查询数据日志：冷热表一起查、合并、按时间倒序，并记录涉及分区的访问计数
     * <p>
     * 流程：
     * 1. 分别查询热表和冷表（各自自动做分区裁剪）
     * 2. 按命中的数据所在表 + 分区，逐个 markVisit（热表/冷表分别计入各自统计）
     * 3. 合并两张表结果，按 create_ts 倒序排序
     * </p>
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 冷热表合并后的数据日志列表
     */
    public List<DataLog> queryByDateRange(LocalDateTime start, LocalDateTime end) {
        // ① 冷热表一起查询
        List<DataLog> hotList = dataLogMapper.selectHotByDateRange(start, end);
        List<DataLog> coldList = dataLogMapper.selectColdByDateRange(start, end);

        // ② 记录分区访问：按实际命中的数据所在表 + 分区 +1，热/冷表各自独立计数
        String hotTable = hotColdConfig.getTableName();
        String coldTable = hotColdConfig.getColdTableName();
        for (String partition : PartitionUtil.getDistinctPartitionsFromEntity(hotList)) {
            partitionVisitService.markVisit(hotTable, partition);
        }
        for (String partition : PartitionUtil.getDistinctPartitionsFromEntity(coldList)) {
            partitionVisitService.markVisit(coldTable, partition);
        }

        // ③ 合并结果并按创建时间倒序
        List<DataLog> result = new ArrayList<>(hotList.size() + coldList.size());
        result.addAll(hotList);
        result.addAll(coldList);
        result.sort(Comparator.comparing(DataLog::getCreateTs).reversed());

        log.info("范围查询 [{}, {}], 热表 {} 条 + 冷表 {} 条, 合并 {} 条",
                start, end, hotList.size(), coldList.size(), result.size());
        return result;
    }

    /**
     * 按 ID 查询单条数据日志：先查热表，未命中再查冷表，并记录对应分区的访问计数
     * <p>
     * 流程：
     * 1. 先查热表，命中则记热表分区访问并返回
     * 2. 热表未命中再查冷表，命中则记冷表分区访问
     * </p>
     *
     * @param id 主键 ID
     * @return 数据日志，两张表都不存在返回 null
     */
    public DataLog queryById(Long id) {
        String hotTable = hotColdConfig.getTableName();
        String coldTable = hotColdConfig.getColdTableName();

        // ① 先查热表
        DataLog dataLog = dataLogMapper.selectHotById(id);
        if (dataLog != null) {
            recordVisitById(hotTable, dataLog);
            log.info("按ID查询 id={}, 命中热表 {}", id, hotTable);
            return dataLog;
        }

        // ② 热表未命中，再查冷表
        dataLog = dataLogMapper.selectColdById(id);
        if (dataLog != null) {
            recordVisitById(coldTable, dataLog);
            log.info("按ID查询 id={}, 命中冷表 {}", id, coldTable);
        }
        return dataLog;
    }

    /**
     * 根据命中数据的 createTs 计算分区名，记录该表该分区的一次访问
     */
    private void recordVisitById(String tableName, DataLog dataLog) {
        if (dataLog.getCreateTs() != null) {
            String partitionName = PartitionUtil.getSinglePartitionName(dataLog.getCreateTs());
            partitionVisitService.markVisit(tableName, partitionName);
        }
    }
}
