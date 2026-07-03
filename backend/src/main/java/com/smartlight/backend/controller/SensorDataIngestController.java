package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.SensorDataDTO;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.service.SensorDataIngestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 传感器数据上报 API（HTTP 通道）
 * 方便 MQTTX 或其他工具直接通过 HTTP 发送传感器数据用于测试和集成
 */
@RestController
@RequestMapping("/api/sensor-data")
@RequiredArgsConstructor
public class SensorDataIngestController {

    private final SensorDataIngestService sensorDataIngestService;

    /**
     * 单条传感器数据上报
     */
    @PostMapping("/ingest")
    public Result<SensorData> ingest(@RequestBody SensorDataDTO dto) {
        if (dto.getLightId() == null) {
            return Result.error("lightId 不能为空");
        }
        SensorData saved = sensorDataIngestService.ingest(dto);
        return Result.success("传感器数据已接收", saved);
    }

    /**
     * 批量传感器数据上报
     */
    @PostMapping("/ingest/batch")
    public Result<Integer> ingestBatch(@RequestBody List<SensorDataDTO> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return Result.error("数据列表不能为空");
        }
        int count = 0;
        for (SensorDataDTO dto : dtoList) {
            if (dto.getLightId() != null) {
                sensorDataIngestService.ingest(dto);
                count++;
            }
        }
        return Result.success("已接收 " + count + " 条传感器数据", count);
    }
}