package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.AlertRule;
import com.smartlight.backend.service.AlertRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/alert-rules")
public class AlertRuleController {

    @Autowired
    private AlertRuleService alertRuleService;

    @GetMapping
    public Result<List<AlertRule>> getAll() {
        return Result.success(alertRuleService.list());
    }

    @PostMapping
    public Result<Boolean> add(@RequestBody AlertRule rule) {
        return Result.success(alertRuleService.save(rule));
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody AlertRule rule) {
        return Result.success(alertRuleService.updateById(rule));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(alertRuleService.removeById(id));
    }
}
