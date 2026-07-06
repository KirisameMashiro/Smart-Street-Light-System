package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.ThresholdControl;
import com.smartlight.backend.service.ThresholdControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/control")
@RequiredArgsConstructor
public class ControlController {

    private final ThresholdControlService thresholdControlService;

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
}