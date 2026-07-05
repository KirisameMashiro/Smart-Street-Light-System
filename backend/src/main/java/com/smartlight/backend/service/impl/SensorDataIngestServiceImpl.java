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
import java.time.Duration;

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
        entity.setSamplingEnergy(dto.getSamplingEnergy());
        entity.setCollectTime(dto.getCollectTime() != null ? dto.getCollectTime() : LocalDateTime.now());

        // 梯形积分法计算采样间隔耗电 (Wh)
        SensorData latest = sensorDataMapper.selectLatestByLightId(dto.getLightId());
        if (latest != null && entity.getSamplingEnergy() == null) {
            if (latest.getPower() != null && entity.getPower() != null) {
                double avgPower = (latest.getPower() + entity.getPower()) / 2;
                long seconds = Duration.between(latest.getCollectTime(), entity.getCollectTime()).getSeconds();
                if (seconds > 0) {
                    double energyWh = avgPower * seconds / 3600;
                    entity.setSamplingEnergy(Math.round(energyWh * 1000.0) / 1000.0);
                } else {
                    entity.setSamplingEnergy(0.0);
                }
            } else {
                entity.setSamplingEnergy(null);
            }
        }

        sensorDataMapper.insert(entity);
        log.info("传感器数据入库成功: lightId={}, collectTime={}, samplingEnergy={}Wh",
                dto.getLightId(), dto.getCollectTime(), entity.getSamplingEnergy());

        // 入库后立即触发告警实时检测
        try {
            alertCheckService.checkAndGenerateAlert(entity);
        } catch (Exception e) {
            log.error("实时告警检测失败: lightId={}, error={}", dto.getLightId(), e.getMessage(), e);
        }

        return entity;
    }
}