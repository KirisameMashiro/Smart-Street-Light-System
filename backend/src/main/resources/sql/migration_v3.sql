-- ============================================================
-- Smart Light System - Migration v3
-- 阈值配置从全局改为策略级独立管理
-- ============================================================

-- 1. timed_strategy 表新增阈值字段
ALTER TABLE timed_strategy
    ADD COLUMN light_off_threshold DOUBLE DEFAULT 100 COMMENT '关灯光照阈值(lux)，动态亮度模式下光照高于此值则关灯'
    AFTER use_dynamic_brightness;

ALTER TABLE timed_strategy
    ADD COLUMN brightness_segments JSON COMMENT '亮度分段配置，格式：[{"threshold":30,"brightness":100},...]，策略级独立阈值'
    AFTER light_off_threshold;

-- 2. 为已有的动态亮度策略设置默认阈值（避免空值导致回退固定亮度）
--    注意：仅当 use_dynamic_brightness=1 时才需要
UPDATE timed_strategy
SET light_off_threshold = 100,
    brightness_segments = '[{"threshold":30,"brightness":100},{"threshold":60,"brightness":60},{"threshold":90,"brightness":30}]'
WHERE use_dynamic_brightness = 1;
