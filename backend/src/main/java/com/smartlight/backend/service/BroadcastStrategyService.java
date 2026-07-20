package com.smartlight.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.BroadcastStrategy;

import java.util.List;

public interface BroadcastStrategyService extends IService<BroadcastStrategy> {
    List<BroadcastStrategy> listWithBroadcastTitle();
}