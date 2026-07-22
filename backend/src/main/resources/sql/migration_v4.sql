-- ============================================================
-- Smart Light System - Migration v4
-- 移除策略级阈值字段，阈值配置完全由 region_groups 内的分组管理
-- ============================================================

-- 1. 删除策略级关灯阈值列（已迁移到 region_groups JSON 中每个分组的 lightOffThreshold）
ALTER TABLE timed_strategy
    DROP COLUMN IF EXISTS light_off_threshold;

-- 2. 删除策略级亮度分段列（已迁移到 region_groups JSON 中每个分组的 brightnessSegments）
ALTER TABLE timed_strategy
    DROP COLUMN IF EXISTS brightness_segments;
