-- ============================================================
-- 智慧路灯管理系统 - 数据库结构初始化脚本
-- 可重复执行：每次执行会先删除所有表再重建
-- ============================================================
CREATE DATABASE IF NOT EXISTS dream32
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE dream32;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. 系统用户表
-- ============================================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(200) NOT NULL COMMENT '密码(MD5加密)',
    `real_name`   VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    `role`        VARCHAR(20)  DEFAULT 'operator' COMMENT '角色: admin-管理员, operator-运维人员',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `status`      INT          DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 2. 路灯设备表
-- ============================================================
DROP TABLE IF EXISTS `light`;
CREATE TABLE `light` (
    `id`            BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `light_code`    VARCHAR(50)  NOT NULL COMMENT '路灯编号',
    `light_name`    VARCHAR(100) DEFAULT NULL COMMENT '路灯名称',
    `location`      VARCHAR(200) DEFAULT NULL COMMENT '安装位置',
    `longitude`     DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `latitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `status`        INT          DEFAULT 0 COMMENT '状态: 0-关闭, 1-开启, 2-故障',
    `brightness`    INT          DEFAULT 0 COMMENT '亮度百分比 (0-100)',
    `manual_control` TINYINT(1)  DEFAULT 0 COMMENT '是否手动控制',
    `device_type`   VARCHAR(50)  DEFAULT NULL COMMENT '设备类型',
    `rated_power`   DECIMAL(10,2) DEFAULT NULL COMMENT '额定功率 (W)',
    `district`      VARCHAR(50)  DEFAULT NULL COMMENT '行政区',
    `road`          VARCHAR(100) DEFAULT NULL COMMENT '路段',
    `has_camera`    TINYINT(1)   DEFAULT 0 COMMENT '是否有监控',
    `has_speaker`   TINYINT(1)   DEFAULT 0 COMMENT '是否有广播',
    `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_light_code` (`light_code`),
    KEY `idx_status` (`status`),
    KEY `idx_location` (`location`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路灯设备表';

-- ============================================================
-- 3. 传感器数据表（原始数据）
--    方案①后不再写入，仅保留旧数据兼容查询
-- ============================================================
DROP TABLE IF EXISTS `sensor_data`;
CREATE TABLE `sensor_data` (
    `id`              BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `light_id`        BIGINT NOT NULL COMMENT '关联路灯ID',
    `illuminance`     DECIMAL(10,2) DEFAULT NULL COMMENT '光照强度 (lux)',
    `power`           DECIMAL(10,2) DEFAULT NULL COMMENT '当前功率 (W)',
    `voltage`         DECIMAL(10,2) DEFAULT NULL COMMENT '电压 (V)',
    `current`         DECIMAL(10,3) DEFAULT NULL COMMENT '电流 (A)',
    `temperature`     DECIMAL(5,2)  DEFAULT NULL COMMENT '温度 (°C)',
    `humidity`        DECIMAL(5,2)  DEFAULT NULL COMMENT '湿度 (%RH)',
    `sampling_energy` DECIMAL(10,3) DEFAULT NULL COMMENT '采样间隔耗电(Wh)',
    `collect_time`    DATETIME NOT NULL COMMENT '数据采集时间',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_light_id` (`light_id`),
    KEY `idx_collect_time` (`collect_time`),
    CONSTRAINT `fk_sensor_data_light` FOREIGN KEY (`light_id`) REFERENCES `light` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器数据表（不再写入，仅保留历史数据）';

-- ============================================================
-- 4. 传感器数据小时级聚合表
--    方案①核心：写入此表替代原始 sensor_data
--    每盏灯每小时 1 行，写入量降低 99.9%
--    碳排放计算、趋势图均从此表读取
-- ============================================================
DROP TABLE IF EXISTS `sensor_data_hourly`;
CREATE TABLE `sensor_data_hourly` (
    `id`              BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `light_id`        BIGINT NOT NULL COMMENT '关联路灯ID',
    `hour_start`      DATETIME NOT NULL COMMENT '统计小时起点（分钟秒归零）',
    `avg_illuminance` DECIMAL(10,2) DEFAULT NULL COMMENT '小时平均光照(lux)',
    `avg_power`       DECIMAL(10,2) DEFAULT NULL COMMENT '小时平均功率(W)',
    `avg_voltage`     DECIMAL(10,2) DEFAULT NULL COMMENT '小时平均电压(V)',
    `avg_current`     DECIMAL(10,3) DEFAULT NULL COMMENT '小时平均电流(A)',
    `avg_temperature` DECIMAL(5,2)  DEFAULT NULL COMMENT '小时平均温度(°C)',
    `avg_humidity`    DECIMAL(5,2)  DEFAULT NULL COMMENT '小时平均湿度(%RH)',
    `total_energy`    DECIMAL(12,3) DEFAULT 0 COMMENT '小时累计耗电(Wh)',
    `data_count`      INT DEFAULT 0 COMMENT '小时采样次数',
    `max_power`       DECIMAL(10,2) DEFAULT NULL COMMENT '小时最高功率(W)',
    `min_power`       DECIMAL(10,2) DEFAULT NULL COMMENT '小时最低功率(W)',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_light_hour` (`light_id`, `hour_start`),
    KEY `idx_hour_start` (`hour_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器数据小时级聚合表（碳排放/趋势/分析用）';

-- ============================================================
-- 5. 报警信息表
-- ============================================================
DROP TABLE IF EXISTS `alert`;
CREATE TABLE `alert` (
    `id`            BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `light_id`      BIGINT NOT NULL COMMENT '关联路灯ID',
    `alert_type`    INT DEFAULT NULL COMMENT '报警类型: 1-过流, 2-过压, 3-欠压, 4-过热, 5-通讯故障, 6-其他',
    `alert_level`   INT DEFAULT 2 COMMENT '报警级别: 1-提示, 2-一般, 3-严重, 4-紧急',
    `message`       VARCHAR(500) DEFAULT NULL COMMENT '报警内容',
    `status`        INT DEFAULT 0 COMMENT '处理状态: 0-未处理, 1-已处理',
    `handler`       VARCHAR(50) DEFAULT NULL COMMENT '处理人',
    `handle_time`   DATETIME DEFAULT NULL COMMENT '处理时间',
    `handle_remark` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_light_id` (`light_id`),
    KEY `idx_status` (`status`),
    KEY `idx_alert_type` (`alert_type`),
    CONSTRAINT `fk_alert_light` FOREIGN KEY (`light_id`) REFERENCES `light` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警信息表';

-- ============================================================
-- 6. 告警规则表
-- ============================================================
DROP TABLE IF EXISTS `alert_rule`;
CREATE TABLE `alert_rule` (
    `id`          BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `rule_type`   VARCHAR(50)  DEFAULT NULL COMMENT '规则类型',
    `rule_name`   VARCHAR(100) DEFAULT NULL COMMENT '规则名称',
    `threshold`   VARCHAR(100) DEFAULT NULL COMMENT '阈值表达式',
    `enabled`     INT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '规则描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

-- ============================================================
-- 7. 操作日志表
-- ============================================================
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id`             BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `operator`       VARCHAR(50)  DEFAULT NULL COMMENT '操作人用户名',
    `operator_name`  VARCHAR(50)  DEFAULT NULL COMMENT '操作人姓名',
    `type`           VARCHAR(50)  DEFAULT NULL COMMENT '操作类型',
    `content`        VARCHAR(500) DEFAULT NULL COMMENT '操作内容',
    `result`         VARCHAR(50)  DEFAULT NULL COMMENT '操作结果',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ============================================================
-- 8. 系统配置表
-- ============================================================
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
    `id`           BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `config_key`   VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` VARCHAR(500) DEFAULT NULL COMMENT '配置值',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '配置描述',
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ============================================================
-- 9. 碳减排日统计数据表
-- ============================================================
DROP TABLE IF EXISTS `carbon_stats`;
CREATE TABLE `carbon_stats` (
    `id`             BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `stat_date`      DATE NOT NULL COMMENT '统计日期',
    `road`           VARCHAR(100) DEFAULT NULL COMMENT '路段（null=全路段汇总）',
    `light_count`    INT DEFAULT 0 COMMENT '路灯数量',
    `baseline_energy` DECIMAL(12,2) DEFAULT 0 COMMENT '基准能耗(kWh)',
    `actual_energy`  DECIMAL(12,2) DEFAULT 0 COMMENT '实际能耗(kWh)',
    `saved_energy`   DECIMAL(12,2) DEFAULT 0 COMMENT '节电量(kWh)',
    `co2_reduction`  DECIMAL(12,2) DEFAULT 0 COMMENT 'CO2减排量(kg)',
    `saving_rate`    DECIMAL(5,2) DEFAULT 0 COMMENT '节能率(%)',
    `create_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_date` (`stat_date`),
    KEY `idx_road` (`road`),
    KEY `idx_date_road` (`stat_date`, `road`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='碳减排日统计数据表';

-- ============================================================
-- 10. 阈值联动配置表
-- ============================================================
DROP TABLE IF EXISTS `threshold_control`;
CREATE TABLE `threshold_control` (
    `id`                  BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `enabled`             TINYINT DEFAULT 0 COMMENT '是否启用: 0-禁用, 1-启用',
    `light_off_threshold` DECIMAL(10,2) DEFAULT 100.00 COMMENT '关灯光照阈值(lux)',
    `segments`            TEXT DEFAULT NULL COMMENT '调光档位配置，JSON数组格式',
    `create_time`         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阈值联动配置表';

-- ============================================================
-- 11. 定时策略表
-- ============================================================
DROP TABLE IF EXISTS `timed_strategy`;
CREATE TABLE `timed_strategy` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '策略名称',
    `type` VARCHAR(20) NOT NULL COMMENT '策略类型：default/timed',
    `weekdays` JSON DEFAULT NULL COMMENT '适用星期 [1,2,3,4,5,6,7]，默认类型必填',
    `start_date` DATE DEFAULT NULL COMMENT '开始日期，时间段类型必填',
    `end_date` DATE DEFAULT NULL COMMENT '结束日期，时间段类型必填',
    `start_time` TIME NOT NULL COMMENT '开始时间',
    `end_time` TIME NOT NULL COMMENT '结束时间',
    `brightness` INT NOT NULL DEFAULT 80 COMMENT '目标亮度 0-100',
    `region_groups` JSON DEFAULT NULL COMMENT '适用区域分组 [{district, roads}]',
    `enabled` TINYINT DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    KEY `idx_type` (`type`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时策略表';

-- ============================================================
-- 12. AI 知识库表
-- ============================================================
DROP TABLE IF EXISTS `knowledge`;
CREATE TABLE `knowledge` (
    `id`          BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `title`       VARCHAR(200) DEFAULT NULL COMMENT '知识标题',
    `content`     TEXT DEFAULT NULL COMMENT '知识内容',
    `keywords`    VARCHAR(500) DEFAULT NULL COMMENT '关键词（逗号分隔）',
    `category`    VARCHAR(50) DEFAULT NULL COMMENT '知识分类',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- ============================================================
-- 13. 亮度推荐记录表
-- 每小时对每种道路等级计算一次推荐亮度并记录
-- ============================================================
DROP TABLE IF EXISTS `brightness_recommendation`;
CREATE TABLE `brightness_recommendation` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `calc_hour` DATETIME NOT NULL COMMENT '计算的目标小时',
    `road_level` VARCHAR(20) DEFAULT NULL COMMENT '道路等级：主干道/次干道/园区道路',
    `solar_elevation` DOUBLE DEFAULT NULL COMMENT '太阳高度角（度）',
    `theoretical_lux` DOUBLE DEFAULT NULL COMMENT '理论基准照度 Lstd (lux)',
    `weather_correction` DOUBLE DEFAULT NULL COMMENT '天气修正系数 Wcorr',
    `environmental_lux` DOUBLE DEFAULT NULL COMMENT '等效环境光照 Lenv (lux)',
    `e_min` INT DEFAULT NULL COMMENT '道路最低安全照度 Emin (lux)',
    `recommended_brightness` INT DEFAULT NULL COMMENT '推荐亮度 0-100',
    `period_label` VARCHAR(20) DEFAULT NULL COMMENT '时段标签',
    `cloud_cover` INT DEFAULT NULL COMMENT '云量 0-100',
    `rain_level` VARCHAR(20) DEFAULT NULL COMMENT '降雨等级',
    `weather_desc` VARCHAR(50) DEFAULT NULL COMMENT '天气描述',
    `latitude` DOUBLE DEFAULT NULL COMMENT '纬度',
    `longitude` DOUBLE DEFAULT NULL COMMENT '经度',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_calc_hour` (`calc_hour`),
    KEY `idx_road_level` (`road_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='亮度推荐记录表';

-- ============================================================
-- 14. 行政区基础表
-- ============================================================
DROP TABLE IF EXISTS `district`;
CREATE TABLE `district` (
    `id`            BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `district_name` VARCHAR(100) NOT NULL COMMENT '行政区名称',
    `district_code` VARCHAR(100) DEFAULT NULL COMMENT '行政区编码',
    `sort_order`    INT DEFAULT 0 COMMENT '排序号',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_district_name` (`district_name`),
    KEY `idx_sort` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政区基础表';

-- ============================================================
-- 15. 路段基础表
-- ============================================================
DROP TABLE IF EXISTS `road`;
CREATE TABLE `road` (
    `id`            BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `road_name`     VARCHAR(200) NOT NULL COMMENT '路段名称',
    `district_id`   BIGINT NOT NULL COMMENT '所属行政区ID',
    `sort_order`    INT DEFAULT 0 COMMENT '排序号',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_road_district` (`road_name`, `district_id`),
    KEY `idx_district` (`district_id`),
    KEY `idx_sort` (`sort_order`),
    CONSTRAINT `fk_road_district` FOREIGN KEY (`district_id`) REFERENCES `district` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路段基础表';

-- ============================================================
-- 16. 设备类型基础表
-- ============================================================
DROP TABLE IF EXISTS `device_type`;
CREATE TABLE `device_type` (
    `id`            BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `type_name`     VARCHAR(100) NOT NULL COMMENT '类型名称',
    `type_code`     VARCHAR(100) DEFAULT NULL COMMENT '类型编码',
    `rated_power`   DECIMAL(10,2) DEFAULT 0 COMMENT '额定功率(W)',
    `has_camera`    TINYINT(1)   DEFAULT 0 COMMENT '是否有监控',
    `has_speaker`   TINYINT(1)   DEFAULT 0 COMMENT '是否有广播',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_name` (`type_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型基础表';

-- ============================================================
-- 17. 广播设计表
-- ============================================================
DROP TABLE IF EXISTS `broadcast`;
CREATE TABLE `broadcast` (
    `id`            BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `title`         VARCHAR(200) NOT NULL COMMENT '广播主题',
    `content`       TEXT DEFAULT NULL COMMENT '广播内容',
    `light_ids`     JSON DEFAULT NULL COMMENT '关联路灯ID列表',
    `light_codes`   VARCHAR(1000) DEFAULT NULL COMMENT '关联路灯编号（逗号分隔）',
    `enabled`       TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广播设计表';

-- ============================================================
-- 18. 广播策略表
-- ============================================================
DROP TABLE IF EXISTS `broadcast_strategy`;
CREATE TABLE `broadcast_strategy` (
    `id`            BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `name`          VARCHAR(100) NOT NULL COMMENT '策略名称',
    `broadcast_id`  BIGINT NOT NULL COMMENT '关联广播ID',
    `start_time`    TIME NOT NULL COMMENT '开始时间',
    `end_time`      TIME NOT NULL COMMENT '结束时间',
    `repeat_type`   VARCHAR(20) DEFAULT 'daily' COMMENT '重复类型: daily-每天, weekdays-工作日, weekend-周末, custom-自定义',
    `custom_days`   JSON DEFAULT NULL COMMENT '自定义星期 [1,2,3,4,5,6,7]',
    `enabled`       TINYINT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `create_time`   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_strategy_name` (`name`),
    KEY `idx_broadcast_id` (`broadcast_id`),
    KEY `idx_enabled` (`enabled`),
    CONSTRAINT `fk_strategy_broadcast` FOREIGN KEY (`broadcast_id`) REFERENCES `broadcast` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广播策略表';

SET FOREIGN_KEY_CHECKS = 1;
