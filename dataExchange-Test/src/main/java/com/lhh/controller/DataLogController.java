package com.lhh.controller;

import com.lhh.pojo.DataLog;
import com.lhh.service.DataLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据日志控制器
 * <p>
 * 提供数据日志的查询接口，每次查询会自动记录分区访问计数，
 * 供冷热调度任务判定各分区的热度。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 20:00
 */
@Slf4j
@RestController
@RequestMapping("/api/data")
public class DataLogController {

    /** 数据日志业务服务 */
    @Autowired
    private DataLogService dataLogService;

    /**
     * 按 ID 查询单条数据日志
     * <p>
     * 查询后自动记录对应分区的访问计数（visit_count + 1）
     * </p>
     *
     * @param id 主键 ID
     * @return 数据日志，不存在返回 null
     */
    @GetMapping("/{id}")
    public DataLog getById(@PathVariable Long id) {
        log.info("查询数据日志 id={}", id);
        return dataLogService.queryById(id);
    }

    /**
     * 按时间范围查询数据日志
     * <p>
     * 查询后自动记录涉及的所有分区的访问计数（每个分区 visit_count + 1）
     * </p>
     *
     * @param start 开始时间，格式：yyyy-MM-dd HH:mm:ss
     * @param end   结束时间，格式：yyyy-MM-dd HH:mm:ss
     * @return 符合条件的数据日志列表
     */
    @GetMapping("/range")
    public List<DataLog> getByDateRange(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end) {
        log.info("范围查询数据日志 [{}, {}]", start, end);
        return dataLogService.queryByDateRange(start, end);
    }
}
