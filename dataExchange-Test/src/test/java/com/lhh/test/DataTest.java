package com.lhh.test;

import com.lhh.pojo.DataLog;
import com.lhh.service.DataLogService;
import com.lhh.service.PartitionHotColdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ClassName:DataTest
 * Package:com.lhh.test
 * Description:
 *
 * @Author lhh
 * Create 2026/10/31 17:07
 */
@SpringBootTest
public class DataTest {
    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private DataLogService dataLogService;

    @Autowired
    private PartitionHotColdService partitionHotColdService;

    /**
     * 插入数据测试
     */
    @Test
    public void insetTest(){
        DataLog dataLog1 = new DataLog();
        dataLog1.setCreateTs(LocalDateTime.from(TS_FORMATTER.parse("2026-10-29 17:07:00")));
        dataLog1.setContent("test1");
        dataLogService.insertHot(dataLog1);
    }

    /**
     * 查询数据测试
     */

    @Test
    public void queryTest(){
        DataLog dataLog = dataLogService.queryById(1L);
        System.out.println(dataLog);
    }

    /**
     * 热冷分区测试
     */

    @Test
    public void exTest(){
        partitionHotColdService.checkAndRunHotColdSchedule();
    }
}
