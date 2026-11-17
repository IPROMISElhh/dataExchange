package com.lhh.test;

import com.lhh.pojo.AntiSeepageWall;
import com.lhh.pojo.CrushedStonePile;
import com.lhh.service.AntiSeepageWallService;
import com.lhh.service.CrushedStonePileService;
import com.lhh.service.PartitionHotColdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
    /** 东八区时区：把 PDF 帧里的 Unix 秒级时间戳还原为本地时间（与发送端一致） */
    private static final ZoneId ZONE_EAST_8 = ZoneId.of("Asia/Shanghai");

    @Autowired
    private AntiSeepageWallService antiSeepageWallService;

    @Autowired
    private CrushedStonePileService crushedStonePileService;

    @Autowired
    private PartitionHotColdService partitionHotColdService;

    /**
     * 插入数据测试：模拟向防渗墙热表（anti_seepage_wall_hot）插入一条数据
     * <p>
     * collectTime 直接使用 PDF 帧里的 Unix 秒级时间戳（东八区基准），
     * 通过 Instant.ofEpochSecond + Asia/Shanghai 时区还原为 LocalDateTime 再落库；
     * 该字段是分区键，落库时按其日期命中对应日分区。
     * createTs 为空时由 Service 自动填充为当前时间。
     * </p>
     */
    @Test
    public void insertTest(){
        // PDF 防渗墙示例帧中的采集时间戳（Unix 秒级，东八区基准）
        long collectTimeEpochSecond = 1794873600L;

        AntiSeepageWall wall = new AntiSeepageWall();
        wall.setDeviceId("dev-003");
        // Unix 秒级时间戳 → LocalDateTime（按东八区还原，与发送端一致）
        wall.setCollectTime(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(collectTimeEpochSecond), ZONE_EAST_8));
        wall.setSeq(2);
        wall.setAngleX(0.19f);
        wall.setAngleY(-0.13f);
        wall.setMainCylinder(19.406f);
        wall.setAuxCylinder(2.63f);
        wall.setState(1);
        wall.setType(1);
        antiSeepageWallService.insertHot(wall);
    }

    /**
     * 插入数据测试2：模拟向碎石桩热表（crushed_stone_pile_hot）插入 PDF 示例帧数据
     * <p>
     * 取值来自 PDF《明文数据格式与落库对接说明》4.1 碎石桩（type=0）示例帧：
     * collectTime=1722751800（Unix 秒级，东八区还原为 2024-08-04 14:10:00）、
     * device_id=dev-003、seq=3，以及 runDepth/runCurrent/runWaterPress/runWeightAdd/runState。
     * 该 collectTime 早于预建分区窗口（2026-11-16 起），因首个分区 p20261116 无下界，
     * 数据会落入首分区 p20261116；createTs 为空时由 Service 自动填充为当前时间。
     * </p>
     */
    @Test
    public void insertTest2(){
        // PDF 碎石桩示例帧中的采集时间戳（Unix 秒级，东八区基准）
        long collectTimeEpochSecond = 1722751800L;

        CrushedStonePile pile = new CrushedStonePile();
        pile.setDeviceId("dev-003");
        // Unix 秒级时间戳 → LocalDateTime（按东八区还原，与发送端一致）
        pile.setCollectTime(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(collectTimeEpochSecond), ZONE_EAST_8));
        pile.setSeq(3);
        pile.setRunDepth(-0.38f);
        pile.setRunCurrent(202.9f);
        pile.setRunWaterPress(0.02f);
        pile.setRunWeightAdd(0f);
        pile.setRunState(1);
        pile.setType(0);
        crushedStonePileService.insertHot(pile);
    }

    /**
     * 查询数据测试
     */

    @Test
    public void queryTest(){
        AntiSeepageWall antiSeepageWall = antiSeepageWallService.queryById(2L);
        System.out.println(antiSeepageWall);
    }

    @Test
    public void queryTest2(){
        CrushedStonePile crushedStonePile = crushedStonePileService.queryById(1L);
        System.out.println(crushedStonePile);
    }

    /**
     * 热冷分区测试
     */

    @Test
    public void exTest(){
        partitionHotColdService.checkAndRunHotColdSchedule();
    }
}
