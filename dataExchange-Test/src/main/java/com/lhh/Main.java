package com.lhh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类
 * <p>
 * 基于 Spring Boot 的冷热数据自动迁移项目入口（双表方案 A：对称分区 + 行级搬迁）。
 * 定时扫描热表/冷表各分区访问量，以 INSERT SELECT + DELETE 在热表与冷表之间双向搬迁行数据（不碰分区结构）。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 14:13
 */

/**
 * @SpringBootApplication - 标记为 Spring Boot 应用，自动配置 + 组件扫描
 * @MapperScan        - 扫描 com.lhh.mapper 包下的 MyBatis Mapper 接口
 * @EnableScheduling  - 开启 Spring 定时任务支持，使 @Scheduled 注解生效
 */
@SpringBootApplication
@MapperScan("com.lhh.mapper")
@EnableScheduling
public class Main {

    /**
     * 应用入口方法，启动 Spring Boot 容器
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}