package com.lhh.task;

import com.lhh.service.PartitionHotColdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 冷热分区定时调度任务（双表方案 A）
 * <p>
 * 每天凌晨 00:00 触发，调用 PartitionHotColdService 执行冷热调度。
 * 调度流程：两表对称补齐未来分区 → 扫描热表/冷表分区 → 统计访问量 → 判定冷热 → 行级 INSERT+DELETE 双向搬迁。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 17:03
 */

@Slf4j       // Lombok: 自动生成日志对象 log
@Component   // 注册为 Spring Bean，被 @EnableScheduling 扫描到
public class HotColdScheduleTask {

    /** 冷热分区调度服务 */
    @Autowired
    private PartitionHotColdService partitionHotColdService;

    /**
     * 定时任务入口：每天凌晨 00:00 执行
     * cron 表达式: 秒 分 时 日 月 周 → "0 0 0 * * *" 表示每天 00:00:00
     */
//    @Scheduled(cron="0 0 0 * * *")
//    public void hotColdTask() {
//        log.info("===== 启动冷热分区定时调度（双表行级搬迁） =====");
//        partitionHotColdService.checkAndRunHotColdSchedule();
//    }
}

