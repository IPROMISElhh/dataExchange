-- ============================================
-- 冷热数据分离 初始化DDL（双表方案 A：对称分区 + 行级 INSERT/DELETE 搬迁）
-- 执行顺序：1. 创建热表 → 2. 创建冷表 → 3. 创建统计表
-- 兼容 MySQL 和 OceanBase（MySQL 租户）
--
-- 【核心约定】热表与冷表必须保持完全相同的分区布局（同名、同边界）。
--   迁移只在两表之间搬运「行数据」（INSERT SELECT + DELETE），不新建/删除分区，
--   因此彻底规避 RANGE 分区「ADD/REORGANIZE 只能按边界递增操作」的限制。
--   空分区予以保留，作为下次迁回的落点，保证双向对称。
--
-- 【注意】若之前已用旧 DDL（含 p_future / p_cold_init 兜底分区）建过表，
--   请先 DROP TABLE data_log_hot / data_log_cold 再执行本脚本，
--   因为 CREATE TABLE IF NOT EXISTS 不会修改已存在表的分区结构。
-- ============================================

-- ============================================
-- 1. 创建热数据表 data_log_hot
-- 按天分区，存储近期/高频访问数据；分区键 create_ts，TO_DAYS() 按天
-- 预建窗口：2026-10-20 ~ 2026-11-05（后续由定时任务按 horizon-days 向前补齐）
-- 首个分区无下界，早于窗口的历史数据自动落入 p20261020
-- ============================================
CREATE TABLE IF NOT EXISTS data_log_hot (
    id          BIGINT AUTO_INCREMENT COMMENT '主键',
    create_ts   DATETIME     NOT NULL COMMENT '创建时间，也是分区键',
    content     VARCHAR(256) COMMENT '数据内容',
    PRIMARY KEY (id, create_ts)
) ENGINE = InnoDB
  PARTITION BY RANGE (TO_DAYS(create_ts)) (
    PARTITION p20261029 VALUES LESS THAN (TO_DAYS('2026-10-30')),
    PARTITION p20261030 VALUES LESS THAN (TO_DAYS('2026-10-31')),
    PARTITION p20261031 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p20261101 VALUES LESS THAN (TO_DAYS('2026-11-02')),
    PARTITION p20261102 VALUES LESS THAN (TO_DAYS('2026-11-03')),
    PARTITION p20261103 VALUES LESS THAN (TO_DAYS('2026-11-04')),
    PARTITION p20261104 VALUES LESS THAN (TO_DAYS('2026-11-05')),
    PARTITION p20261105 VALUES LESS THAN (TO_DAYS('2026-11-06'))
);

-- ============================================
-- 2. 创建冷数据表 data_log_cold
-- 结构与分区布局与热表完全一致（对称分区是行级双向搬迁的前提）
-- 存储低频访问的历史数据
-- ============================================
CREATE TABLE IF NOT EXISTS data_log_cold (
    id          BIGINT AUTO_INCREMENT COMMENT '主键',
    create_ts   DATETIME     NOT NULL COMMENT '创建时间，也是分区键',
    content     VARCHAR(256) COMMENT '数据内容',
    PRIMARY KEY (id, create_ts)
) ENGINE = InnoDB
  PARTITION BY RANGE (TO_DAYS(create_ts)) (
    PARTITION p20261029 VALUES LESS THAN (TO_DAYS('2026-10-30')),
    PARTITION p20261030 VALUES LESS THAN (TO_DAYS('2026-10-31')),
    PARTITION p20261031 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p20261101 VALUES LESS THAN (TO_DAYS('2026-11-02')),
    PARTITION p20261102 VALUES LESS THAN (TO_DAYS('2026-11-03')),
    PARTITION p20261103 VALUES LESS THAN (TO_DAYS('2026-11-04')),
    PARTITION p20261104 VALUES LESS THAN (TO_DAYS('2026-11-05')),
    PARTITION p20261105 VALUES LESS THAN (TO_DAYS('2026-11-06'))
);

-- ============================================
-- 3. 创建分区访问统计表 partition_visit_stat
-- 记录每张表每个分区的访问量和上次迁移时间
-- table_name 区分热表（data_log_hot）和冷表（data_log_cold），各自独立计数
-- 【注意】若之前用过无 table_name 列的旧 DDL 建过本表，
--   请先 DROP TABLE partition_visit_stat 再执行本脚本，
--   因为 CREATE TABLE IF NOT EXISTS 不会修改已存在表的列结构。
-- ============================================
CREATE TABLE IF NOT EXISTS partition_visit_stat (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    table_name      VARCHAR(128) NOT NULL COMMENT '业务表名，如 data_log_hot 或 data_log_cold',
    partition_name  VARCHAR(128) NOT NULL COMMENT '分区名，如 p20261026',
    visit_count     BIGINT       DEFAULT 0 COMMENT '本周期访问次数',
    last_moved_time DATETIME     DEFAULT NULL COMMENT '上次迁移时间，用于冷却期判断',
    UNIQUE KEY uk_table_partition (table_name, partition_name)
) COMMENT = '分区冷热访问统计';
