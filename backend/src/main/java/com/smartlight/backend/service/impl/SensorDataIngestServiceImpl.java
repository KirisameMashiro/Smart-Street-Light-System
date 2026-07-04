package com.smartlight.backend.service.impl;

import com.smartlight.backend.dto.SensorDataDTO;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.service.AlertCheckService;
import com.smartlight.backend.service.SensorDataIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 传感器数据入库服务实现
 * 数据入库后立即触发告警实时检测
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataIngestServiceImpl implements SensorDataIngestService {

    private final SensorDataMapper sensorDataMapper;
    private final AlertCheckService alertCheckService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SensorData ingest(SensorDataDTO dto) {
        SensorData entity = new SensorData();
        entity.setLightId(dto.getLightId());
        entity.setIlluminance(dto.getIlluminance());
        entity.setPower(dto.getPower());
        entity.setVoltage(dto.getVoltage());
        entity.setCurrent(dto.getCurrent());
        entity.setTemperature(dto.getTemperature());
        entity.setHumidity(dto.getHumidity());
        entity.setCollectTime(dto.getCollectTime() != null ? dto.getCollectTime() : LocalDateTime.now());

        sensorDataMapper.insert(entity);
        log.info("传感器数据入库成功: lightId={}, collectTime={}", dto.getLightId(), dto.getCollectTime());

        // 入库后立即触发告警实时检测
        try {
            alertCheckService.checkAndGenerateAlert(entity);
        } catch (Exception e) {
            log.error("实时告警检测失败: lightId={}, error={}", dto.getLightId(), e.getMessage(), e);
        }

        return entity;
    }
}