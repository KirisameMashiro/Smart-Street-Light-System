package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.SensorDataQueryDTO;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.service.SensorDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 传感器数据管理 API
 */
@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataController {

    @Autowired
    private SensorDataService sensorDataService;

    /**
     * 分页查询传感器数据
     */
    @GetMapping("/page")
    public Result<IPage<SensorData>> getPage(SensorDataQueryDTO queryDTO) {
        IPage<SensorData> page = sensorDataService.getPage(
                queryDTO.getPageNum(),
                queryDTO.getPageSize(),
                queryDTO.getLightId(),
                queryDTO.getStartTime(),
                queryDTO.getEndTime()
        );
        return Result.success(page);
    }

    /**
     * 获取路灯最新传感器数据
     */
    @GetMapping("/latest/{lightId}")
    public Result<SensorData> getLatest(@PathVariable Long lightId) {
        return Result.success(sensorDataService.getLatestByLightId(lightId));
    }

    /**
     * 获取平均传感器数据
     */
    @GetMapping("/average/{lightId}")
    public Result<SensorData> getAverage(
            @PathVariable Long lightId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        return Result.success(sensorDataService.getAverageData(lightId, startTime, endTime));
    }

    /**
     * 新增传感器数据
     */
    @PostMapping
    public Result<Boolean> add(@RequestBody SensorData sensorData) {
        if (sensorData.getCollectTime() == null) {
            sensorData.setCollectTime(LocalDateTime.now());
        }
        return Result.success(sensorDataService.save(sensorData));
    }
}
