package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Broadcast;
import com.smartlight.backend.entity.BroadcastStrategy;
import com.smartlight.backend.mapper.BroadcastStrategyMapper;
import com.smartlight.backend.service.BroadcastService;
import com.smartlight.backend.service.BroadcastStrategyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BroadcastStrategyServiceImpl extends ServiceImpl<BroadcastStrategyMapper, BroadcastStrategy> implements BroadcastStrategyService {

    @Autowired
    private BroadcastService broadcastService;

    @Override
    public List<BroadcastStrategy> listWithBroadcastTitle() {
        LambdaQueryWrapper<BroadcastStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BroadcastStrategy::getId);
        List<BroadcastStrategy> list = this.list(wrapper);
        fillBroadcastTitle(list);
        return list;
    }

    private void fillBroadcastTitle(List<BroadcastStrategy> list) {
        if (list == null || list.isEmpty()) return;
        List<Broadcast> broadcasts = broadcastService.list();
        for (BroadcastStrategy strategy : list) {
            if (strategy.getBroadcastId() != null) {
                broadcasts.stream()
                        .filter(b -> b.getId().equals(strategy.getBroadcastId()))
                        .findFirst()
                        .ifPresent(b -> strategy.setBroadcastTitle(b.getTitle()));
            }
        }
    }
}