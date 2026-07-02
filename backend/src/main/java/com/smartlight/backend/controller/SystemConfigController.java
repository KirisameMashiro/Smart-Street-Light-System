package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.SystemConfig;
import com.smartlight.backend.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @GetMapping("/config")
    public Result<List<SystemConfig>> getConfig() {
        return Result.success(systemConfigService.list());
    }

    @PutMapping("/config")
    public Result<Boolean> updateConfig(@RequestBody SystemConfig config) {
        return Result.success(systemConfigService.updateById(config));
    }
}
