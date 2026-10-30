package com.lhh.service;

import com.lhh.mapper.PartitionVisitMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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

}

