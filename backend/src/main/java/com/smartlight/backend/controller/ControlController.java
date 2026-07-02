package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/control")
public class ControlController {

    @GetMapping("/strategies")
    public Result<List<Map<String, Object>>> getStrategies() {
        return Result.success(Arrays.asList(
                mapOf("id", 1, "name", "工作日模式", "type", "weekday", "startTime", "19:00", "endTime", "06:00", "brightness", 80, "enabled", 1),
                mapOf("id", 2, "name", "节假日模式", "type", "holiday", "startTime", "18:00", "endTime", "07:00", "brightness", 60, "enabled", 1),
                mapOf("id", 3, "name", "深夜节能模式", "type", "everyday", "startTime", "23:00", "endTime", "05:00", "brightness", 30, "enabled", 0)
        ));
    }

    @GetMapping("/strategies/page")
    public Result<Map<String, Object>> getStrategyPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        List<Map<String, Object>> records = Arrays.asList(
                mapOf("id", 1, "name", "工作日模式", "type", "weekday", "startTime", "19:00", "endTime", "06:00", "brightness", 80, "enabled", 1),
                mapOf("id", 2, "name", "节假日模式", "type", "holiday", "startTime", "18:00", "endTime", "07:00", "brightness", 60, "enabled", 1),
                mapOf("id", 3, "name", "深夜节能模式", "type", "everyday", "startTime", "23:00", "endTime", "05:00", "brightness", 30, "enabled", 0)
        );
        return Result.success(mapOf("records", records, "total", 3, "current", pageNum, "size", pageSize));
    }

    @PostMapping("/strategies")
    public Result<Boolean> addStrategy(@RequestBody Map<String, Object> data) { return Result.success(true); }

    @PutMapping("/strategies")
    public Result<Boolean> updateStrategy(@RequestBody Map<String, Object> data) { return Result.success(true); }

    @DeleteMapping("/strategies/{id}")
    public Result<Boolean> deleteStrategy(@PathVariable Long id) { return Result.success(true); }

    @PutMapping("/strategies/{id}/enable")
    public Result<Boolean> toggleStrategy(@PathVariable Long id, @RequestParam Integer enabled) { return Result.success(true); }

    @GetMapping("/threshold")
    public Result<Map<String, Object>> getThreshold() {
        return Result.success(mapOf("enabled", 1, "illuminanceThreshold", 30.0, "autoDimming", true, "autoBrightness", 50));
    }

    @PutMapping("/threshold")
    public Result<Boolean> updateThreshold(@RequestBody Map<String, Object> data) { return Result.success(true); }

    @PutMapping("/threshold/toggle")
    public Result<Boolean> toggleThreshold(@RequestParam Integer enabled) { return Result.success(true); }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
