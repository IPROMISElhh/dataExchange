package com.lhh.pojo;
import lombok.Data;
import java.time.LocalDateTime;
/**
 * 数据日志实体类
 * <p>
 * 对应热表 data_log_hot / 冷表 data_log_cold，两张表结构一致，均按 create_ts 字段按天分区。
 * 每条记录根据 create_ts 自动落入对应的日期分区（如 p20261026）。
 * </p>
 *
 * @Author lhh
 * Create 2026/10/26 14:25
 */

@Data  // Lombok: 自动生成 getter/setter/toString/equals/hashCode
public class DataLog {

    /** 主键，自增 */
    private Long id;

    /** 创建时间，也是分区键（PARTITION BY RANGE TO_DAYS(create_ts)） */
    private LocalDateTime createTs;

    /** 数据内容/备注 */
    private String content;
}
