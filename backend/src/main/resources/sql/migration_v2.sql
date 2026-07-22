-- ============================================================
-- v2.0 迁移脚本：定时策略动态亮度
-- 执行方式: 直接在 MySQL 中运行本文件
-- ============================================================
USE dream32;

-- 1. timed_strategy 表新增 use_dynamic_brightness 列
ALTER TABLE `timed_strategy`
    ADD COLUMN `use_dynamic_brightness` TINYINT(1) DEFAULT 0
    COMMENT '是否启用动态亮度: 0-固定亮度, 1-根据光照动态调节'
    AFTER `brightness`;
