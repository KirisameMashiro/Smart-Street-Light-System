package com.smartlight.backend.service.impl;

import com.smartlight.backend.entity.ThresholdControl;
import com.smartlight.backend.mapper.ThresholdControlMapper;
import com.smartlight.backend.service.ThresholdControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 阈值联动配置服务
 * <p>
 * 自 v2.0 起，阈值联动不再有独立的定时调度任务（已融入定时策略的「动态亮度」模式）。
 * 本服务仅负责阈值配置的 CRUD 管理 + 光照→亮度匹配算法，
 * 供 TimedStrategyScheduler 在 {@code useDynamicBrightness=true} 时调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdControlServiceImpl implements ThresholdControlService {

    private final ThresholdControlMapper thresholdControlMapper;

    @Override
    public ThresholdControl getConfig() {
        List<ThresholdControl> list = thresholdControlMapper.selectList(null);
        if (list == null || list.isEmpty()) {
            ThresholdControl defaults = new ThresholdControl();
            defaults.setEnabled(false);
            defaults.setLightOffThreshold(100.0);
            defaults.setSegments(List.of(
                    seg(30, 100),
                    seg(60, 60),
                    seg(90, 30)));
            return defaults;
        }
        return list.get(0);
    }

    private static ThresholdControl.SegmentConfig seg(double threshold, int brightness) {
        ThresholdControl.SegmentConfig s = new ThresholdControl.SegmentConfig();
        s.setThreshold(threshold);
        s.setBrightness(brightness);
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveConfig(ThresholdControl config) {
        // 验证 segments：光照阈值越小 → 亮度必须越高（越暗越亮）
        List<ThresholdControl.SegmentConfig> segs = config.getSegments();
        if (segs != null && segs.size() > 1) {
            for (int i = 1; i < segs.size(); i++) {
                ThresholdControl.SegmentConfig prev = segs.get(i - 1);
                ThresholdControl.SegmentConfig curr = segs.get(i);
                if (curr.getThreshold() > prev.getThreshold()
                        && curr.getBrightness() != null && prev.getBrightness() != null
                        && curr.getBrightness() > prev.getBrightness()) {
                    throw new IllegalArgumentException(
                            "调光档位不合法：光照阈值 " + curr.getThreshold()
                                    + " > " + prev.getThreshold()
                                    + "，但亮度 " + curr.getBrightness()
                                    + "% > " + prev.getBrightness()
                                    + "%。越暗应越亮，高阈值的亮度不应高于低阈值");
                }
            }
        }

        List<ThresholdControl> list = thresholdControlMapper.selectList(null);
        if (list == null || list.isEmpty()) {
            config.setId(null);
            return thresholdControlMapper.insert(config) > 0;
        } else {
            ThresholdControl existing = list.get(0);
            config.setId(existing.getId());
            return thresholdControlMapper.updateById(config) > 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(boolean enabled) {
        ThresholdControl config = getConfig();
        config.setEnabled(enabled);
        if (config.getId() == null) {
            return thresholdControlMapper.insert(config) > 0;
        } else {
            return thresholdControlMapper.updateById(config) > 0;
        }
    }

    @Override
    public Integer findMatchingBrightness(double illuminance) {
        ThresholdControl config = getConfig();
        List<ThresholdControl.SegmentConfig> segments = config.getSegments();
        if (segments == null || segments.isEmpty()) {
            return 100;
        }

        ThresholdControl.SegmentConfig matched = null;
        for (ThresholdControl.SegmentConfig seg : segments) {
            if (illuminance <= seg.getThreshold()) {
                if (matched == null || seg.getThreshold() < matched.getThreshold()) {
                    matched = seg;
                }
            }
        }
        if (matched == null && !segments.isEmpty()) {
            matched = segments.stream()
                    .max((a, b) -> Double.compare(a.getThreshold(), b.getThreshold()))
                    .orElse(null);
        }
        return matched != null && matched.getBrightness() != null ? matched.getBrightness() : 100;
    }
}
