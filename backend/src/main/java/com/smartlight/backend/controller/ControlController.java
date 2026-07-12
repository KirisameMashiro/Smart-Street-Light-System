package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.ThresholdControl;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.service.ThresholdControlService;
import com.smartlight.backend.service.TimedStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/control")
@RequiredArgsConstructor
public class ControlController {

    private final ThresholdControlService thresholdControlService;
    private final TimedStrategyService timedStrategyService;

    /**
     * 获取阈值联动配置
     */
    @GetMapping("/threshold")
    public Result<ThresholdControl> getThreshold() {
        return Result.success(thresholdControlService.getConfig());
    }

    /**
     * 保存阈值联动配置
     */
    @PutMapping("/threshold")
    public Result<Boolean> updateThreshold(@RequestBody ThresholdControl data) {
        return Result.success(thresholdControlService.saveConfig(data));
    }

    /**
     * 启停阈值联动总开关
     */
    @PutMapping("/threshold/toggle")
    public Result<Boolean> toggleThreshold(@RequestParam Boolean enabled) {
        return Result.success(thresholdControlService.toggleEnabled(enabled));
    }

    /**
     * 分页查询定时策略列表
     */
    @GetMapping("/strategies/page")
    public Result<IPage<TimedStrategy>> getStrategyPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name) {
        return Result.success(timedStrategyService.getPage(pageNum, pageSize, type, name));
    }

    /**
     * 获取所有定时策略列表
     */
    @GetMapping("/strategies")
    public Result<List<TimedStrategy>> getStrategyList() {
        return Result.success(timedStrategyService.listAll());
    }

    /**
     * 获取启用的定时策略列表
     */
    @GetMapping("/strategies/enabled")
    public Result<List<TimedStrategy>> getEnabledStrategies() {
        return Result.success(timedStrategyService.listEnabled());
    }

    /**
     * 新增定时策略
     */
    @PostMapping("/strategies")
    public Result<Boolean> addStrategy(@RequestBody TimedStrategy strategy) {
        try {
            return Result.success(timedStrategyService.save(strategy));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新定时策略
     */
    @PutMapping("/strategies")
    public Result<Boolean> updateStrategy(@RequestBody TimedStrategy strategy) {
        try {
            return Result.success(timedStrategyService.update(strategy));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除定时策略
     */
    @DeleteMapping("/strategies/{id}")
    public Result<Boolean> deleteStrategy(@PathVariable Long id) {
        return Result.success(timedStrategyService.delete(id));
    }

    /**
     * 启用/停用定时策略
     */
    @PutMapping("/strategies/{id}/enable")
    public Result<Boolean> toggleStrategy(@PathVariable Long id, @RequestParam Boolean enabled) {
        return Result.success(timedStrategyService.toggleEnabled(id, enabled));
    }
}