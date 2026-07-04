-- 智慧路灯管理系统 数据库初始化脚本
-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS smart_light DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE smart_light;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码(MD5加密)',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `role` VARCHAR(20) DEFAULT 'operator' COMMENT '角色: admin-管理员, operator-运维人员',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `status` INT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 路灯设备表
CREATE TABLE IF NOT EXISTS `light` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `light_code` VARCHAR(50) NOT NULL COMMENT '路灯编号',
    `light_name` VARCHAR(100) DEFAULT NULL COMMENT '路灯名称',
    `location` VARCHAR(200) DEFAULT NULL COMMENT '安装位置',
    `longitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '经度',
    `latitude` DECIMAL(10, 6) DEFAULT NULL COMMENT '纬度',
    `status` INT DEFAULT 0 COMMENT '状态: 0-关闭, 1-开启, 2-故障',
    `brightness` INT DEFAULT 0 COMMENT '亮度百分比 (0-100)',
    `device_type` VARCHAR(50) DEFAULT NULL COMMENT '设备类型',
    `rated_power` DECIMAL(10, 2) DEFAULT NULL COMMENT '额定功率 (W)',
    `district` VARCHAR(50) DEFAULT NULL COMMENT '行政区',
    `road` VARCHAR(100) DEFAULT NULL COMMENT '路段',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_light_code` (`light_code`),
    KEY `idx_status` (`status`),
    KEY `idx_location` (`location`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路灯设备表';

-- 传感器数据表
CREATE TABLE IF NOT EXISTS `sensor_data` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `light_id` BIGINT NOT NULL COMMENT '关联路灯ID',
    `illuminance` DECIMAL(10, 2) DEFAULT NULL COMMENT '光照强度 (lux)',
    `power` DECIMAL(10, 2) DEFAULT NULL COMMENT '当前功率 (W)',
    `voltage` DECIMAL(10, 2) DEFAULT NULL COMMENT '电压 (V)',
    `current` DECIMAL(10, 3) DEFAULT NULL COMMENT '电流 (A)',
    `temperature` DECIMAL(5, 2) DEFAULT NULL COMMENT '温度 (°C)',
    `humidity` DECIMAL(5, 2) DEFAULT NULL COMMENT '湿度 (%RH)',
    `total_energy` DECIMAL(12, 2) DEFAULT NULL COMMENT '累计耗电量(kWh)',
    `collect_time` DATETIME NOT NULL COMMENT '数据采集时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_light_id` (`light_id`),
    KEY `idx_collect_time` (`collect_time`),
    CONSTRAINT `fk_sensor_data_light` FOREIGN KEY (`light_id`) REFERENCES `light` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传感器数据表';

-- 报警信息表
CREATE TABLE IF NOT EXISTS `alert` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `light_id` BIGINT NOT NULL COMMENT '关联路灯ID',
    `alert_type` INT DEFAULT NULL COMMENT '报警类型: 1-过流, 2-过压, 3-欠压, 4-过热, 5-通讯故障, 6-其他',
    `alert_level` INT DEFAULT 2 COMMENT '报警级别: 1-提示, 2-一般, 3-严重, 4-紧急',
    `message` VARCHAR(500) DEFAULT NULL COMMENT '报警内容',
    `status` INT DEFAULT 0 COMMENT '处理状态: 0-未处理, 1-已处理',
    `handler` VARCHAR(50) DEFAULT NULL COMMENT '处理人',
    `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间',
    `handle_remark` VARCHAR(500) DEFAULT NULL COMMENT '处理备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_light_id` (`light_id`),
    KEY `idx_status` (`status`),
    KEY `idx_alert_type` (`alert_type`),
    CONSTRAINT `fk_alert_light` FOREIGN KEY (`light_id`) REFERENCES `light` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警信息表';

-- 插入默认管理员账号 (密码: admin123, MD5加密)
INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`) VALUES
('admin', '0192023a7bbd73250516f069df18b500', '系统管理员', 'admin', 1);

-- 插入默认运维人员账号 (密码: 123456)
INSERT INTO `user` (`username`, `password`, `real_name`, `role`, `status`) VALUES
('operator', 'e10adc3949ba59abbe56e057f20f883e', '运维人员', 'operator', 1);

-- 插入示例路灯数据
INSERT INTO `light` (`light_code`, `light_name`, `location`, `longitude`, `latitude`, `status`, `brightness`, `device_type`, `rated_power`) VALUES
('SL-001', '路灯A-01', '人民路与解放路交叉口', 118.123456, 36.123456, 1, 80, 'LED-100W', 100.00),
('SL-002', '路灯A-02', '人民路中段', 118.124567, 36.124567, 1, 75, 'LED-100W', 100.00),
('SL-003', '路灯B-01', '建设路与和平路交叉口', 118.125678, 36.125678, 0, 0, 'LED-150W', 150.00),
('SL-004', '路灯B-02', '建设路北段', 118.126789, 36.126789, 2, 0, 'LED-150W', 150.00);

-- ========== 以下为前后端对接新增的表和字段 ==========

-- 路灯设备表补充字段 (已用 ALTER 执行，此处仅作记录)
-- ALTER TABLE light ADD COLUMN district VARCHAR(50) DEFAULT NULL COMMENT '行政区';
-- ALTER TABLE light ADD COLUMN road VARCHAR(100) DEFAULT NULL COMMENT '路段';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
    `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人姓名',
    `type` VARCHAR(50) DEFAULT NULL COMMENT '操作类型',
    `content` VARCHAR(500) DEFAULT NULL COMMENT '操作内容',
    `result` VARCHAR(50) DEFAULT NULL COMMENT '操作结果',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_operator` (`operator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 系统配置表
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    `config_value` VARCHAR(500) DEFAULT NULL COMMENT '配置值',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '配置描述',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- 告警规则表
CREATE TABLE IF NOT EXISTS `alert_rule` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `rule_type` VARCHAR(50) DEFAULT NULL COMMENT '规则类型',
    `rule_name` VARCHAR(100) DEFAULT NULL COMMENT '规则名称',
    `threshold` VARCHAR(100) DEFAULT NULL COMMENT '阈值',
    `enabled` INT DEFAULT 1 COMMENT '是否启用: 0-禁用, 1-启用',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '规则描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

-- AI知识库表
CREATE TABLE IF NOT EXISTS `knowledge` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID',
    `title` VARCHAR(200) DEFAULT NULL COMMENT '知识标题',
    `content` TEXT DEFAULT NULL COMMENT '知识内容',
    `category` VARCHAR(50) DEFAULT NULL COMMENT '知识分类',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- 插入默认系统配置
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('energy_baseline_power', '250', '传统钠灯基准功率(W)'),
('energy_baseline_hours', '12', '日均照明时长(h)'),
('co2_factor', '0.997', '碳排放因子(kg CO₂/kWh)')
ON DUPLICATE KEY UPDATE `config_value`=VALUES(`config_value`);

-- 更新示例路灯的行政区/路段
UPDATE `light` SET `district`='中心区', `road`='人民路' WHERE `light_code` IN ('SL-001','SL-002');
UPDATE `light` SET `district`='城北区', `road`='建设路' WHERE `light_code` IN ('SL-003','SL-004');
