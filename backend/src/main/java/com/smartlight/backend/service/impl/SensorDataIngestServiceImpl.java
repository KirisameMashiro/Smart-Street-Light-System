package com.smartlight.backend.service.impl;

import com.smartlight.backend.dto.SensorDataDTO;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.service.AlertCheckService;
import com.smartlight.backend.service.CumulativeEnergyService;
import com.smartlight.backend.service.SensorDataIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 传感器数据入库服务实现（Redis + 异步批量写入优化版）
 * <p>
 * 写入流程：
 * 1. 从 Redis 读取上一周期功率，计算梯形积分耗电（替代 SELECT MySQL）
 * 2. 写入 Redis 最新数据缓存 {@code sensor:latest:{lightId}} (Hash)
 * 3. 写入 Redis 今日累计耗电 {@code energy:today} (Hash)
 * 4. 累加至 CumulativeEnergyService（内存，毫秒级）
 * 5. 放入 SensorDataBatchService 缓冲区，由定时任务批量 INSERT 到 MySQL
 * 6. 告警检测由批量写入阶段异步触发
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataIngestServiceImpl implements SensorDataIngestService {

    private final Optional<StringRedisTemplate> stringRedisTemplate;
    private final AlertCheckService alertCheckService;
    private final CumulativeEnergyService cumulativeEnergyService;
    private final SensorDataBatchService sensorDataBatchService;

    /** Redis Key 前缀：每盏路灯最新传感器数据 */
    private static final String KEY_SENSOR_LATEST_PREFIX = "sensor:latest:";
    /** Redis Key：今日累计耗电 */
    private static final String KEY_ENERGY_TODAY = "energy:today";
    /** 传感器数据缓存 TTL，超过此时间无上报则自动过期（单位：秒） */
    private static final long SENSOR_CACHE_TTL_SECONDS = 300;

    @Override
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

        // ===== ① 从 Redis 读取上一周期功率，计算梯形积分耗电 =====
        // 替代原来的 SELECT MySQL 操作（Redis 不可用时跳过）
        Double lastPower = getLastPowerFromRedis(dto.getLightId());

        if (lastPower != null && entity.getSamplingEnergy() == null) {
            if (entity.getPower() != null) {
                double avgPower = (lastPower + entity.getPower()) / 2;
                // 需要获取上一次采集时间来计算时间差
                LocalDateTime lastCollectTime = getLastCollectTimeFromRedis(dto.getLightId());
                if (lastCollectTime != null) {
                    long seconds = Duration.between(lastCollectTime, entity.getCollectTime()).getSeconds();
                    if (seconds > 0) {
                        double energyWh = avgPower * seconds / 3600;
                        entity.setSamplingEnergy(Math.round(energyWh * 1000.0) / 1000.0);
                    } else {
                        entity.setSamplingEnergy(0.0);
                    }
                } else {
                    entity.setSamplingEnergy(null);
                }
            } else {
                entity.setSamplingEnergy(null);
            }
        }

        // ===== ② 写入 Redis 最新传感器数据缓存 =====
        saveToRedis(dto.getLightId(), entity);

        // ===== ③ 写入 Redis 今日累计耗电 =====
        if (entity.getSamplingEnergy() != null && entity.getSamplingEnergy() > 0 && stringRedisTemplate.isPresent()) {
            stringRedisTemplate.get().opsForHash().increment(
                    KEY_ENERGY_TODAY, String.valueOf(dto.getLightId()), entity.getSamplingEnergy());
        }

        // ===== ④ 累加至内存 CumulativeEnergyService（纯内存操作） =====
        if (entity.getSamplingEnergy() != null && entity.getSamplingEnergy() > 0) {
            cumulativeEnergyService.accumulate(dto.getLightId(), entity.getSamplingEnergy());
        }

        // ===== ⑤ 放入批量缓冲区，由定时任务异步写入 MySQL =====
        sensorDataBatchService.enqueue(entity);

        log.debug("传感器数据已处理（Redis+缓冲区）: lightId={}, collectTime={}, power={}W, energy={}Wh",
                dto.getLightId(), entity.getCollectTime(), entity.getPower(), entity.getSamplingEnergy());

        return entity;
    }

    // ==================== Redis 读取方法 ====================

    /**
     * 从 Redis 读取上一周期功率值
     */
    private Double getLastPowerFromRedis(Long lightId) {
        if (stringRedisTemplate.isEmpty()) return null;
        Object power = stringRedisTemplate.get().opsForHash().get(
                KEY_SENSOR_LATEST_PREFIX + lightId, "power");
        if (power == null) return null;
        try {
            return Double.parseDouble(power.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 从 Redis 读取上一次采集时间
     */
    private LocalDateTime getLastCollectTimeFromRedis(Long lightId) {
        if (stringRedisTemplate.isEmpty()) return null;
        Object time = stringRedisTemplate.get().opsForHash().get(
                KEY_SENSOR_LATEST_PREFIX + lightId, "collectTime");
        if (time == null) return null;
        try {
            return LocalDateTime.parse(time.toString());
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Redis 写入方法 ====================

    /**
     * 将传感器最新数据写入 Redis
     */
    private void saveToRedis(Long lightId, SensorData entity) {
        if (stringRedisTemplate.isEmpty()) return;
        String key = KEY_SENSOR_LATEST_PREFIX + lightId;

        // 使用 putAll 一次性写入所有字段
        stringRedisTemplate.get().opsForHash().putAll(key, Map.of(
                "lightId", String.valueOf(lightId),
                "illuminance", entity.getIlluminance() != null ? String.valueOf(entity.getIlluminance()) : "",
                "power", entity.getPower() != null ? String.valueOf(entity.getPower()) : "",
                "voltage", entity.getVoltage() != null ? String.valueOf(entity.getVoltage()) : "",
                "current", entity.getCurrent() != null ? String.valueOf(entity.getCurrent()) : "",
                "temperature", entity.getTemperature() != null ? String.valueOf(entity.getTemperature()) : "",
                "humidity", entity.getHumidity() != null ? String.valueOf(entity.getHumidity()) : "",
                "collectTime", entity.getCollectTime() != null ? entity.getCollectTime().toString() : "",
                "samplingEnergy", entity.getSamplingEnergy() != null ? String.valueOf(entity.getSamplingEnergy()) : ""
        ));

        // 设置过期时间，避免死数据长期占用内存
        stringRedisTemplate.get().expire(key, SENSOR_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
    }
}