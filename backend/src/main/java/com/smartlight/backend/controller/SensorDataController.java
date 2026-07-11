package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.SensorDataQueryDTO;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.service.CumulativeEnergyService;
import com.smartlight.backend.service.SensorDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 传感器数据管理 API
 * <p>
 * - 历史数据查询 /page、/average 仍走 MySQL<br>
 * - 最新数据查询 /latest/{lightId}、/latest/all 优先走 Redis 缓存
 */
@RestController
@RequestMapping("/api/sensor-data")
public class SensorDataController {

    @Autowired
    private SensorDataService sensorDataService;

    @Autowired
    private CumulativeEnergyService cumulativeEnergyService;

    /**
     * 分页查询传感器数据（历史数据，从小时聚合表查询）
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
     * 优先读 Redis 缓存，Miss 则查 MySQL
     */
    @GetMapping("/latest/{lightId}")
    public Result<SensorData> getLatest(@PathVariable Long lightId) {
        return Result.success(sensorDataService.getLatestByLightId(lightId));
    }

    /**
     * 批量获取所有路灯最新传感器数据
     * 通过 Redis pipeline 一次获取全部，替代 N+1 次独立查询
     * 实时监控页面使用此接口替代逐盏查询
     */
    @GetMapping("/latest/all")
    public Result<Map<Long, SensorData>> getAllLatest() {
        Map<Long, SensorData> allLatest = sensorDataService.getAllLatest();
        return Result.success(allLatest);
    }

    /**
     * 获取平均传感器数据（历史聚合，查 MySQL）
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

    /**
     * 获取所有路灯今日累计耗电 (Wh)
     */
    @GetMapping("/today-energy")
    public Result<Map<Long, Double>> getTodayEnergy() {
        return Result.success(cumulativeEnergyService.getAllTodayEnergy());
    }
}