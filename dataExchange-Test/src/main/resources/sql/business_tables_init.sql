-- ============================================
-- 三类业务数据 冷热分离 初始化DDL
-- 数据来源：明文数据格式与落库对接说明 v1.0 (2026-11-16)
-- 业务类型：碎石桩(type=0)、防渗墙(type=1)、固结(type=2)
--
-- 【架构说明】采用双表方案（对称分区 + 行级 INSERT/DELETE 搬迁）
--   - 热表：存储近期/高频访问数据，位于 SSD
--   - 冷表：存储历史/低频访问数据，位于 HDD
--   - 热表与冷表保持完全相同的分区布局（同名、同边界）
--   - 迁移只在两表之间搬运「行数据」，不新建/删除分区
--
-- 【判重设计】唯一键：device_id + seq + collectTime
--   - QoS 1 可能重复投递，需按 seq 判重
--   - 设备重启后 seq 重数，加 collectTime 兜底区分
--
-- 【分区策略】按天分区，分区键为 collect_time (DATETIME，采集/事件时间)
--   预建窗口：2026-11-16 ~ 2026-11-30（后续由定时任务向前补齐）
--
-- 【为何用 collect_time 而非 create_ts 作分区键】
--   MySQL/OceanBase 分区表要求：每个唯一索引（含主键）必须包含分区函数的全部列（错误码 1503）。
--   判重唯一键为 (device_id, seq, collect_time)，已含 collect_time，故以 collect_time 为分区键，
--   既满足约束、保留判重语义，又让「按 collect_time 范围查询」能触发分区裁剪。
--   主键相应为 (id, collect_time)；create_ts 降级为普通审计列（入库时间），不再参与分区。
-- ============================================

-- ============================================
-- 一、碎石桩（type=0）
-- 字段：runDepth(深度m), runCurrent(电流A), runWaterPress(水压MPa),
--       runWeightAdd(重量kg), runState(状态码)
-- ============================================

-- 1.1 碎石桩热表
CREATE TABLE IF NOT EXISTS crushed_stone_pile_hot (
    id              BIGINT AUTO_INCREMENT COMMENT '主键',
    device_id       VARCHAR(64)  NOT NULL COMMENT '设备编号',
    collect_time    DATETIME     NOT NULL COMMENT '采集时间（由collectTime时间戳转换），也是分区键',
    seq             INT          NOT NULL COMMENT '帧序号，同一设备从1严格递增',
    run_depth       FLOAT        COMMENT '运行深度(m)，向下为负',
    run_current     FLOAT        COMMENT '运行电流(A)',
    run_water_press FLOAT        COMMENT '运行水压(MPa)',
    run_weight_add  FLOAT        COMMENT '运行重量(kg)',
    run_state       INT          COMMENT '运行状态码',
    type            TINYINT      NOT NULL DEFAULT 0 COMMENT '业务类型：0=碎石桩',
    create_ts       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（审计用，非分区键）',
    PRIMARY KEY (id, collect_time),
    UNIQUE KEY uk_device_seq_time (device_id, seq, collect_time)
) ENGINE = InnoDB
  COMMENT = '碎石桩热数据表'
  PARTITION BY RANGE (TO_DAYS(collect_time)) (
    PARTITION p20261116 VALUES LESS THAN (TO_DAYS('2026-11-17')),
    PARTITION p20261117 VALUES LESS THAN (TO_DAYS('2026-11-18')),
    PARTITION p20261118 VALUES LESS THAN (TO_DAYS('2026-11-19')),
    PARTITION p20261119 VALUES LESS THAN (TO_DAYS('2026-11-20')),
    PARTITION p20261120 VALUES LESS THAN (TO_DAYS('2026-11-21')),
    PARTITION p20261121 VALUES LESS THAN (TO_DAYS('2026-11-22')),
    PARTITION p20261122 VALUES LESS THAN (TO_DAYS('2026-11-23')),
    PARTITION p20261123 VALUES LESS THAN (TO_DAYS('2026-11-24')),
    PARTITION p20261124 VALUES LESS THAN (TO_DAYS('2026-11-25')),
    PARTITION p20261125 VALUES LESS THAN (TO_DAYS('2026-11-26')),
    PARTITION p20261126 VALUES LESS THAN (TO_DAYS('2026-11-27')),
    PARTITION p20261127 VALUES LESS THAN (TO_DAYS('2026-11-28')),
    PARTITION p20261128 VALUES LESS THAN (TO_DAYS('2026-11-29')),
    PARTITION p20261129 VALUES LESS THAN (TO_DAYS('2026-11-30')),
    PARTITION p20261130 VALUES LESS THAN (TO_DAYS('2026-12-01'))
);

-- 1.2 碎石桩冷表
CREATE TABLE IF NOT EXISTS crushed_stone_pile_cold (
    id              BIGINT AUTO_INCREMENT COMMENT '主键',
    device_id       VARCHAR(64)  NOT NULL COMMENT '设备编号',
    collect_time    DATETIME     NOT NULL COMMENT '采集时间（由collectTime时间戳转换），也是分区键',
    seq             INT          NOT NULL COMMENT '帧序号，同一设备从1严格递增',
    run_depth       FLOAT        COMMENT '运行深度(m)，向下为负',
    run_current     FLOAT        COMMENT '运行电流(A)',
    run_water_press FLOAT        COMMENT '运行水压(MPa)',
    run_weight_add  FLOAT        COMMENT '运行重量(kg)',
    run_state       INT          COMMENT '运行状态码',
    type            TINYINT      NOT NULL DEFAULT 0 COMMENT '业务类型：0=碎石桩',
    create_ts       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（审计用，非分区键）',
    PRIMARY KEY (id, collect_time),
    UNIQUE KEY uk_device_seq_time (device_id, seq, collect_time)
) ENGINE = InnoDB
  COMMENT = '碎石桩冷数据表'
  PARTITION BY RANGE (TO_DAYS(collect_time)) (
    PARTITION p20261116 VALUES LESS THAN (TO_DAYS('2026-11-17')),
    PARTITION p20261117 VALUES LESS THAN (TO_DAYS('2026-11-18')),
    PARTITION p20261118 VALUES LESS THAN (TO_DAYS('2026-11-19')),
    PARTITION p20261119 VALUES LESS THAN (TO_DAYS('2026-11-20')),
    PARTITION p20261120 VALUES LESS THAN (TO_DAYS('2026-11-21')),
    PARTITION p20261121 VALUES LESS THAN (TO_DAYS('2026-11-22')),
    PARTITION p20261122 VALUES LESS THAN (TO_DAYS('2026-11-23')),
    PARTITION p20261123 VALUES LESS THAN (TO_DAYS('2026-11-24')),
    PARTITION p20261124 VALUES LESS THAN (TO_DAYS('2026-11-25')),
    PARTITION p20261125 VALUES LESS THAN (TO_DAYS('2026-11-26')),
    PARTITION p20261126 VALUES LESS THAN (TO_DAYS('2026-11-27')),
    PARTITION p20261127 VALUES LESS THAN (TO_DAYS('2026-11-28')),
    PARTITION p20261128 VALUES LESS THAN (TO_DAYS('2026-11-29')),
    PARTITION p20261129 VALUES LESS THAN (TO_DAYS('2026-11-30')),
    PARTITION p20261130 VALUES LESS THAN (TO_DAYS('2026-12-01'))
);

-- ============================================
-- 二、防渗墙（type=1）
-- 字段：angleX(X角度°), angleY(Y角度°), mainCylinder(主油缸m),
--       auxCylinder(副油缸m), state(状态码)
-- ============================================

-- 2.1 防渗墙热表
CREATE TABLE IF NOT EXISTS anti_seepage_wall_hot (
    id              BIGINT AUTO_INCREMENT COMMENT '主键',
    device_id       VARCHAR(64)  NOT NULL COMMENT '设备编号',
    collect_time    DATETIME     NOT NULL COMMENT '采集时间（由collectTime时间戳转换），也是分区键',
    seq             INT          NOT NULL COMMENT '帧序号，同一设备从1严格递增',
    angle_x         FLOAT        COMMENT 'X轴角度(°)',
    angle_y         FLOAT        COMMENT 'Y轴角度(°)',
    main_cylinder   FLOAT        COMMENT '主油缸位移(m)',
    aux_cylinder    FLOAT        COMMENT '副油缸位移(m)',
    state           INT          COMMENT '状态码',
    type            TINYINT      NOT NULL DEFAULT 1 COMMENT '业务类型：1=防渗墙',
    create_ts       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（审计用，非分区键）',
    PRIMARY KEY (id, collect_time),
    UNIQUE KEY uk_device_seq_time (device_id, seq, collect_time)
) ENGINE = InnoDB
  COMMENT = '防渗墙热数据表'
  PARTITION BY RANGE (TO_DAYS(collect_time)) (
    PARTITION p20261116 VALUES LESS THAN (TO_DAYS('2026-11-17')),
    PARTITION p20261117 VALUES LESS THAN (TO_DAYS('2026-11-18')),
    PARTITION p20261118 VALUES LESS THAN (TO_DAYS('2026-11-19')),
    PARTITION p20261119 VALUES LESS THAN (TO_DAYS('2026-11-20')),
    PARTITION p20261120 VALUES LESS THAN (TO_DAYS('2026-11-21')),
    PARTITION p20261121 VALUES LESS THAN (TO_DAYS('2026-11-22')),
    PARTITION p20261122 VALUES LESS THAN (TO_DAYS('2026-11-23')),
    PARTITION p20261123 VALUES LESS THAN (TO_DAYS('2026-11-24')),
    PARTITION p20261124 VALUES LESS THAN (TO_DAYS('2026-11-25')),
    PARTITION p20261125 VALUES LESS THAN (TO_DAYS('2026-11-26')),
    PARTITION p20261126 VALUES LESS THAN (TO_DAYS('2026-11-27')),
    PARTITION p20261127 VALUES LESS THAN (TO_DAYS('2026-11-28')),
    PARTITION p20261128 VALUES LESS THAN (TO_DAYS('2026-11-29')),
    PARTITION p20261129 VALUES LESS THAN (TO_DAYS('2026-11-30')),
    PARTITION p20261130 VALUES LESS THAN (TO_DAYS('2026-12-01'))
);

-- 2.2 防渗墙冷表
CREATE TABLE IF NOT EXISTS anti_seepage_wall_cold (
    id              BIGINT AUTO_INCREMENT COMMENT '主键',
    device_id       VARCHAR(64)  NOT NULL COMMENT '设备编号',
    collect_time    DATETIME     NOT NULL COMMENT '采集时间（由collectTime时间戳转换），也是分区键',
    seq             INT          NOT NULL COMMENT '帧序号，同一设备从1严格递增',
    angle_x         FLOAT        COMMENT 'X轴角度(°)',
    angle_y         FLOAT        COMMENT 'Y轴角度(°)',
    main_cylinder   FLOAT        COMMENT '主油缸位移(m)',
    aux_cylinder    FLOAT        COMMENT '副油缸位移(m)',
    state           INT          COMMENT '状态码',
    type            TINYINT      NOT NULL DEFAULT 1 COMMENT '业务类型：1=防渗墙',
    create_ts       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（审计用，非分区键）',
    PRIMARY KEY (id, collect_time),
    UNIQUE KEY uk_device_seq_time (device_id, seq, collect_time)
) ENGINE = InnoDB
  COMMENT = '防渗墙冷数据表'
  PARTITION BY RANGE (TO_DAYS(collect_time)) (
    PARTITION p20261116 VALUES LESS THAN (TO_DAYS('2026-11-17')),
    PARTITION p20261117 VALUES LESS THAN (TO_DAYS('2026-11-18')),
    PARTITION p20261118 VALUES LESS THAN (TO_DAYS('2026-11-19')),
    PARTITION p20261119 VALUES LESS THAN (TO_DAYS('2026-11-20')),
    PARTITION p20261120 VALUES LESS THAN (TO_DAYS('2026-11-21')),
    PARTITION p20261121 VALUES LESS THAN (TO_DAYS('2026-11-22')),
    PARTITION p20261122 VALUES LESS THAN (TO_DAYS('2026-11-23')),
    PARTITION p20261123 VALUES LESS THAN (TO_DAYS('2026-11-24')),
    PARTITION p20261124 VALUES LESS THAN (TO_DAYS('2026-11-25')),
    PARTITION p20261125 VALUES LESS THAN (TO_DAYS('2026-11-26')),
    PARTITION p20261126 VALUES LESS THAN (TO_DAYS('2026-11-27')),
    PARTITION p20261127 VALUES LESS THAN (TO_DAYS('2026-11-28')),
    PARTITION p20261128 VALUES LESS THAN (TO_DAYS('2026-11-29')),
    PARTITION p20261129 VALUES LESS THAN (TO_DAYS('2026-11-30')),
    PARTITION p20261130 VALUES LESS THAN (TO_DAYS('2026-12-01'))
);

-- ============================================
-- 三、固结（type=2）
-- 字段：pressure(压力MPa), flowIn(流入流量L/min), flowOut(流出流量L/min),
--       density(密度g/cm³), temperature(温度℃)
-- ============================================

-- 3.1 固结热表
CREATE TABLE IF NOT EXISTS consolidation_hot (
    id              BIGINT AUTO_INCREMENT COMMENT '主键',
    device_id       VARCHAR(64)  NOT NULL COMMENT '设备编号',
    collect_time    DATETIME     NOT NULL COMMENT '采集时间（由collectTime时间戳转换），也是分区键',
    seq             INT          NOT NULL COMMENT '帧序号，同一设备从1严格递增',
    pressure        FLOAT        COMMENT '压力(MPa)',
    flow_in         FLOAT        COMMENT '流入流量(L/min)',
    flow_out        FLOAT        COMMENT '流出流量(L/min)',
    density         FLOAT        COMMENT '密度(g/cm³)',
    temperature     FLOAT        COMMENT '温度(℃)',
    type            TINYINT      NOT NULL DEFAULT 2 COMMENT '业务类型：2=固结',
    create_ts       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（审计用，非分区键）',
    PRIMARY KEY (id, collect_time),
    UNIQUE KEY uk_device_seq_time (device_id, seq, collect_time)
) ENGINE = InnoDB
  COMMENT = '固结热数据表'
  PARTITION BY RANGE (TO_DAYS(collect_time)) (
    PARTITION p20261116 VALUES LESS THAN (TO_DAYS('2026-11-17')),
    PARTITION p20261117 VALUES LESS THAN (TO_DAYS('2026-11-18')),
    PARTITION p20261118 VALUES LESS THAN (TO_DAYS('2026-11-19')),
    PARTITION p20261119 VALUES LESS THAN (TO_DAYS('2026-11-20')),
    PARTITION p20261120 VALUES LESS THAN (TO_DAYS('2026-11-21')),
    PARTITION p20261121 VALUES LESS THAN (TO_DAYS('2026-11-22')),
    PARTITION p20261122 VALUES LESS THAN (TO_DAYS('2026-11-23')),
    PARTITION p20261123 VALUES LESS THAN (TO_DAYS('2026-11-24')),
    PARTITION p20261124 VALUES LESS THAN (TO_DAYS('2026-11-25')),
    PARTITION p20261125 VALUES LESS THAN (TO_DAYS('2026-11-26')),
    PARTITION p20261126 VALUES LESS THAN (TO_DAYS('2026-11-27')),
    PARTITION p20261127 VALUES LESS THAN (TO_DAYS('2026-11-28')),
    PARTITION p20261128 VALUES LESS THAN (TO_DAYS('2026-11-29')),
    PARTITION p20261129 VALUES LESS THAN (TO_DAYS('2026-11-30')),
    PARTITION p20261130 VALUES LESS THAN (TO_DAYS('2026-12-01'))
);

-- 3.2 固结冷表
CREATE TABLE IF NOT EXISTS consolidation_cold (
    id              BIGINT AUTO_INCREMENT COMMENT '主键',
    device_id       VARCHAR(64)  NOT NULL COMMENT '设备编号',
    collect_time    DATETIME     NOT NULL COMMENT '采集时间（由collectTime时间戳转换），也是分区键',
    seq             INT          NOT NULL COMMENT '帧序号，同一设备从1严格递增',
    pressure        FLOAT        COMMENT '压力(MPa)',
    flow_in         FLOAT        COMMENT '流入流量(L/min)',
    flow_out        FLOAT        COMMENT '流出流量(L/min)',
    density         FLOAT        COMMENT '密度(g/cm³)',
    temperature     FLOAT        COMMENT '温度(℃)',
    type            TINYINT      NOT NULL DEFAULT 2 COMMENT '业务类型：2=固结',
    create_ts       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间（审计用，非分区键）',
    PRIMARY KEY (id, collect_time),
    UNIQUE KEY uk_device_seq_time (device_id, seq, collect_time)
) ENGINE = InnoDB
  COMMENT = '固结冷数据表'
  PARTITION BY RANGE (TO_DAYS(collect_time)) (
    PARTITION p20261116 VALUES LESS THAN (TO_DAYS('2026-11-17')),
    PARTITION p20261117 VALUES LESS THAN (TO_DAYS('2026-11-18')),
    PARTITION p20261118 VALUES LESS THAN (TO_DAYS('2026-11-19')),
    PARTITION p20261119 VALUES LESS THAN (TO_DAYS('2026-11-20')),
    PARTITION p20261120 VALUES LESS THAN (TO_DAYS('2026-11-21')),
    PARTITION p20261121 VALUES LESS THAN (TO_DAYS('2026-11-22')),
    PARTITION p20261122 VALUES LESS THAN (TO_DAYS('2026-11-23')),
    PARTITION p20261123 VALUES LESS THAN (TO_DAYS('2026-11-24')),
    PARTITION p20261124 VALUES LESS THAN (TO_DAYS('2026-11-25')),
    PARTITION p20261125 VALUES LESS THAN (TO_DAYS('2026-11-26')),
    PARTITION p20261126 VALUES LESS THAN (TO_DAYS('2026-11-27')),
    PARTITION p20261127 VALUES LESS THAN (TO_DAYS('2026-11-28')),
    PARTITION p20261128 VALUES LESS THAN (TO_DAYS('2026-11-29')),
    PARTITION p20261129 VALUES LESS THAN (TO_DAYS('2026-11-30')),
    PARTITION p20261130 VALUES LESS THAN (TO_DAYS('2026-12-01'))
);

-- ============================================
-- 四、分区访问统计表（三类业务共用）
-- 记录每张表每个分区的访问量和上次迁移时间
-- table_name 区分不同的冷热表
-- ============================================
CREATE TABLE IF NOT EXISTS partition_visit_stat_business (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    table_name      VARCHAR(128) NOT NULL COMMENT '业务表名，如 crushed_stone_pile_hot',
    partition_name  VARCHAR(128) NOT NULL COMMENT '分区名，如 p20261116',
    visit_count     BIGINT       DEFAULT 0 COMMENT '本周期访问次数',
    last_moved_time DATETIME     DEFAULT NULL COMMENT '上次迁移时间，用于冷却期判断',
    UNIQUE KEY uk_table_partition (table_name, partition_name)
) ENGINE = InnoDB
  COMMENT = '业务分区冷热访问统计';

-- ============================================
-- 【表名对照】
-- | 业务类型 | type值 | 热表名                   | 冷表名                    |
-- |----------|--------|--------------------------|---------------------------|
-- | 碎石桩   | 0      | crushed_stone_pile_hot   | crushed_stone_pile_cold   |
-- | 防渗墙   | 1      | anti_seepage_wall_hot    | anti_seepage_wall_cold    |
-- | 固结     | 2      | consolidation_hot        | consolidation_cold        |
-- ============================================
