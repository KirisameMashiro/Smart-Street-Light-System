package com.smartlight.backend.service;

import com.smartlight.backend.entity.ThresholdControl;

public interface ThresholdControlService {

    ThresholdControl getConfig();

    boolean saveConfig(ThresholdControl config);

    boolean toggleEnabled(boolean enabled);

    /**
     * 根据光照值匹配对应的亮度百分比
     * <p>
     * 从阈值配置的 segments 中查找：光照越暗（值越小），匹配的亮度越高。
     * 若光照超出所有档位阈值，返回最低档位亮度。
     * 用于定时策略的动态亮度模式。
     *
     * @param illuminance 实时光照值 (lux)
     * @return 匹配的亮度百分比 (0-100)
     */
    Integer findMatchingBrightness(double illuminance);
}