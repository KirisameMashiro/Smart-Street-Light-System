package com.smartlight.backend.service;

import com.smartlight.backend.entity.ThresholdControl;

public interface ThresholdControlService {

    ThresholdControl getConfig();

    boolean saveConfig(ThresholdControl config);

    boolean toggleEnabled(boolean enabled);
}