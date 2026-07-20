package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.Broadcast;
import com.smartlight.backend.entity.BroadcastStrategy;
import com.smartlight.backend.service.BroadcastService;
import com.smartlight.backend.service.BroadcastStrategyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/broadcast")
public class BroadcastController {

    @Autowired
    private BroadcastService broadcastService;

    @Autowired
    private BroadcastStrategyService broadcastStrategyService;

    // ========== 广播设计管理 ==========

    @GetMapping("/broadcasts")
    public Result<List<Broadcast>> listBroadcasts() {
        LambdaQueryWrapper<Broadcast> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Broadcast::getCreateTime);
        return Result.success(broadcastService.list(wrapper));
    }

    @GetMapping("/broadcasts/{id}")
    public Result<Broadcast> getBroadcast(@PathVariable Long id) {
        return Result.success(broadcastService.getById(id));
    }

    @PostMapping("/broadcasts")
    public Result<Boolean> addBroadcast(@RequestBody Broadcast broadcast) {
        return Result.success(broadcastService.save(broadcast));
    }

    @PutMapping("/broadcasts")
    public Result<Boolean> updateBroadcast(@RequestBody Broadcast broadcast) {
        return Result.success(broadcastService.updateById(broadcast));
    }

    @DeleteMapping("/broadcasts/{id}")
    public Result<Boolean> deleteBroadcast(@PathVariable Long id) {
        return Result.success(broadcastService.removeById(id));
    }

    // ========== 广播策略管理 ==========

    @GetMapping("/strategies")
    public Result<List<BroadcastStrategy>> listStrategies() {
        return Result.success(broadcastStrategyService.listWithBroadcastTitle());
    }

    @GetMapping("/strategies/{id}")
    public Result<BroadcastStrategy> getStrategy(@PathVariable Long id) {
        BroadcastStrategy strategy = broadcastStrategyService.getById(id);
        if (strategy != null) {
            Broadcast broadcast = broadcastService.getById(strategy.getBroadcastId());
            if (broadcast != null) {
                strategy.setBroadcastTitle(broadcast.getTitle());
            }
        }
        return Result.success(strategy);
    }

    @PostMapping("/strategies")
    public Result<Boolean> addStrategy(@RequestBody BroadcastStrategy strategy) {
        LambdaQueryWrapper<BroadcastStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BroadcastStrategy::getName, strategy.getName());
        if (strategy.getId() != null) {
            wrapper.ne(BroadcastStrategy::getId, strategy.getId());
        }
        if (broadcastStrategyService.count(wrapper) > 0) {
            throw new IllegalArgumentException("策略名称已存在：" + strategy.getName());
        }
        return Result.success(broadcastStrategyService.save(strategy));
    }

    @PutMapping("/strategies")
    public Result<Boolean> updateStrategy(@RequestBody BroadcastStrategy strategy) {
        LambdaQueryWrapper<BroadcastStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BroadcastStrategy::getName, strategy.getName());
        if (strategy.getId() != null) {
            wrapper.ne(BroadcastStrategy::getId, strategy.getId());
        }
        if (broadcastStrategyService.count(wrapper) > 0) {
            throw new IllegalArgumentException("策略名称已存在：" + strategy.getName());
        }
        return Result.success(broadcastStrategyService.updateById(strategy));
    }

    @DeleteMapping("/strategies/{id}")
    public Result<Boolean> deleteStrategy(@PathVariable Long id) {
        return Result.success(broadcastStrategyService.removeById(id));
    }
}