package com.smartlight.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.SystemConfig;

public interface SystemConfigService extends IService<SystemConfig> {
    String getConfigValue(String key);
}
