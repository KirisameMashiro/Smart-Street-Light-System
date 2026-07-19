package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.LightBatchDTO;
import com.smartlight.backend.dto.LightQueryDTO;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.service.LightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lights")
public class LightController {

    @Autowired
    private LightService lightService;

    @GetMapping("/page")
    public Result<IPage<Light>> getPage(LightQueryDTO queryDTO) {
        IPage<Light> page = lightService.getPage(
                queryDTO.getPageNum(),
                queryDTO.getPageSize(),
                queryDTO.getKeyword(),
                queryDTO.getStatus(),
                queryDTO.getDistrict(),
                queryDTO.getRoad(),
                queryDTO.getDeviceType()
        );
        return Result.success(page);
    }

    @GetMapping("/districts")
    public Result<List<String>> getDistricts() {
        return Result.success(lightService.getDistricts());
    }

    @GetMapping("/roads")
    public Result<List<String>> getRoads() {
        return Result.success(lightService.getRoads());
    }

    @GetMapping("/device-types")
    public Result<List<String>> getDeviceTypes() {
        return Result.success(lightService.getDeviceTypes());
    }

    @GetMapping
    public Result<List<Light>> getAll() {
        return Result.success(lightService.getCachedList());
    }

    @GetMapping("/{id}")
    public Result<Light> getById(@PathVariable Long id) {
        Light light = lightService.getById(id);
        if (light == null) return Result.notFound("路灯不存在");
        return Result.success(light);
    }

    @PostMapping
    public Result<Boolean> add(@RequestBody Light light) {
        return Result.success(lightService.save(light));
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody Light light) {
        return Result.success(lightService.updateById(light));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(lightService.removeById(id));
    }

    @PostMapping("/batch-switch")
    public Result<Boolean> batchSwitch(@RequestBody LightBatchDTO batchDTO) {
        return Result.success(lightService.batchSwitchStatus(batchDTO.getIds(), batchDTO.getStatus()));
    }

    @PutMapping("/{id}/brightness")
    public Result<Boolean> setBrightness(@PathVariable Long id, @RequestParam Integer brightness) {
        if (brightness < 0 || brightness > 100)
            return Result.error(400, "亮度值必须在 0-100 之间");
        return Result.success(lightService.setBrightness(id, brightness));
    }

    @PutMapping("/{id}/release-manual")
    public Result<Boolean> releaseManualControl(@PathVariable Long id) {
        return Result.success(lightService.releaseManualControl(id));
    }

    @PutMapping("/release-manual-batch")
    public Result<Boolean> releaseManualControlBatch(@RequestBody List<Long> ids) {
        return Result.success(lightService.releaseManualControlBatch(ids));
    }

    @GetMapping("/stats")
    public Result<?> getStats() {
        long online = lightService.countByStatus(1);
        long offline = lightService.countByStatus(0);
        long fault = lightService.countByStatus(2);
        return Result.success(Map.of(
                "online", online,
                "offline", offline,
                "fault", fault,
                "total", online + offline + fault
        ));
    }

    @GetMapping("/group-stats")
    public Result<List<Map<String, Object>>> getGroupStats(@RequestParam String groupBy) {
        List<Light> lights = lightService.list();
        Map<String, Long> map = lights.stream().collect(Collectors.groupingBy(
            light -> {
                switch (groupBy) {
                    case "district": return light.getDistrict() != null ? light.getDistrict() : "未知";
                    case "road": return light.getRoad() != null ? light.getRoad() : "未知";
                    case "deviceType": return light.getDeviceType() != null ? light.getDeviceType() : "未知";
                    default: return "未知";
                }
            },
            Collectors.counting()
        ));
        List<Map<String, Object>> result = map.entrySet().stream()
            .map(entry -> java.util.Map.<String, Object>of("name", entry.getKey(), "count", entry.getValue()))
            .collect(Collectors.toList());
        return Result.success(result);
    }
}