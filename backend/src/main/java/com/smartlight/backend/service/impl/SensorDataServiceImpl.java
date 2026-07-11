package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.service.LightService;
import com.smartlight.backend.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 传感器数据查询服务（Redis 缓存优先版）
 * <p>
 * - {@link #getLatestByLightId(Long)}：先查 Redis（毫秒级），Miss 再查 MySQL
 * - {@link #getAllLatest()}：一次 Redis pipeline 获取所有路灯最新数据，替代 N+1 查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataServiceImpl extends ServiceImpl<SensorDataMapper, SensorData> implements SensorDataService {

    private final Optional<StringRedisTemplate> stringRedisTemplate;
    private final LightService lightService;

    /** Redis Key 前缀：每盏路灯最新传感器数据 */
    private static final String KEY_SENSOR_LATEST_PREFIX = "sensor:latest:";
    /** Redis Key：今日累计耗电 */
    private static final String KEY_ENERGY_TODAY = "energy:today";

    @Override
    public IPage<SensorData> getPage(int pageNum, int pageSize, Long lightId,
                                     String startTime, String endTime) {
        List<Map<String, Object>> allRecords = this.getBaseMapper().selectFromHourlyPage(lightId, startTime, endTime);

        int total = allRecords.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        List<SensorData> pageRecords = allRecords.subList(start, end).stream()
                .map(this::mapRowToSensorData)
                .collect(Collectors.toList());

        Page<SensorData> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(pageRecords);
        return page;
    }

    private SensorData mapRowToSensorData(Map<String, Object> map) {
        SensorData data = new SensorData();
        data.setLightId(map.get("lightId") != null ? ((Number) map.get("lightId")).longValue() : null);
        data.setCollectTime(map.get("collectTime") != null ? ((LocalDateTime) map.get("collectTime")) : null);
        data.setIlluminance(map.get("illuminance") != null ? ((Number) map.get("illuminance")).doubleValue() : null);
        data.setPower(map.get("power") != null ? ((Number) map.get("power")).doubleValue() : null);
        data.setVoltage(map.get("voltage") != null ? ((Number) map.get("voltage")).doubleValue() : null);
        data.setCurrent(map.get("current") != null ? ((Number) map.get("current")).doubleValue() : null);
        data.setTemperature(map.get("temperature") != null ? ((Number) map.get("temperature")).doubleValue() : null);
        data.setHumidity(map.get("humidity") != null ? ((Number) map.get("humidity")).doubleValue() : null);
        data.setSamplingEnergy(map.get("samplingEnergy") != null ? ((Number) map.get("samplingEnergy")).doubleValue() : null);
        return data;
    }

    @Override
    public SensorData getLatestByLightId(Long lightId) {
        if (lightId == null) return null;

        // ① 优先从 Redis 读取（Redis 不可用时直接查 MySQL）
        SensorData cached = stringRedisTemplate.isPresent() ? getFromRedis(lightId) : null;
        if (cached != null) {
            return cached;
        }

        // ② Redis Miss 或 Redis 不可用，回退到 MySQL
        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensorData::getLightId, lightId)
               .orderByDesc(SensorData::getCollectTime)
               .last("LIMIT 1");
        return this.getOne(wrapper);
    }

    @Override
    public SensorData getAverageData(Long lightId, LocalDateTime startTime, LocalDateTime endTime) {
        // 平均值查询走 MySQL（需要聚合计算）
        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensorData::getLightId, lightId)
               .ge(SensorData::getCollectTime, startTime)
               .le(SensorData::getCollectTime, endTime);
        List<SensorData> list = this.list(wrapper);

        if (list.isEmpty()) {
            return null;
        }

        SensorData avg = new SensorData();
        avg.setLightId(lightId);

        avg.setIlluminance(list.stream()
                .filter(d -> d.getIlluminance() != null)
                .mapToDouble(SensorData::getIlluminance).average().orElse(0));
        avg.setPower(list.stream()
                .filter(d -> d.getPower() != null)
                .mapToDouble(SensorData::getPower).average().orElse(0));
        avg.setVoltage(list.stream()
                .filter(d -> d.getVoltage() != null)
                .mapToDouble(SensorData::getVoltage).average().orElse(0));
        avg.setCurrent(list.stream()
                .filter(d -> d.getCurrent() != null)
                .mapToDouble(SensorData::getCurrent).average().orElse(0));
        avg.setTemperature(list.stream()
                .filter(d -> d.getTemperature() != null)
                .mapToDouble(SensorData::getTemperature).average().orElse(0));
        avg.setHumidity(list.stream()
                .filter(d -> d.getHumidity() != null)
                .mapToDouble(SensorData::getHumidity).average().orElse(0));
        avg.setSamplingEnergy(list.stream()
                .filter(d -> d.getSamplingEnergy() != null)
                .mapToDouble(SensorData::getSamplingEnergy).average().orElse(0));
        return avg;
    }

    @Override
    public Map<Long, SensorData> getAllLatest() {
        Map<Long, SensorData> result = new HashMap<>();

        // 获取所有路灯（从缓存或 MySQL）
        List<Light> allLights = lightService.getCachedList();
        if (allLights.isEmpty()) return result;

        // Redis 不可用时直接从 MySQL 获取
        if (stringRedisTemplate.isEmpty()) {
            fillMissingFromDb(allLights, result);
            return result;
        }

        // 从 Redis pipeline 批量获取所有路灯的最新传感器数据
        List<String> keys = allLights.stream()
                .map(l -> KEY_SENSOR_LATEST_PREFIX + l.getId())
                .collect(Collectors.toList());

        List<Object> redisResults = stringRedisTemplate.get().executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    for (String key : keys) {
                        byte[] rawKey = key.getBytes();
                        connection.hashCommands().hGetAll(rawKey);
                    }
                    return null;
                });

        for (int i = 0; i < allLights.size(); i++) {
            Long lightId = allLights.get(i).getId();
            Object raw = (i < redisResults.size()) ? redisResults.get(i) : null;

            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> entryMap = (Map<Object, Object>) raw;
                if (!entryMap.isEmpty()) {
                    SensorData data = mapToSensorData(lightId, entryMap);
                    if (data != null) {
                        result.put(lightId, data);
                    }
                }
            }
        }

        // 如果 Redis 中只有部分数据，从 MySQL 补全缺失的
        if (result.size() < allLights.size()) {
            fillMissingFromDb(allLights, result);
        }

        return result;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 从 Redis 读取单盏路灯最新传感器数据
     */
    private SensorData getFromRedis(Long lightId) {
        if (stringRedisTemplate.isEmpty()) return null;
        String key = KEY_SENSOR_LATEST_PREFIX + lightId;
        Map<Object, Object> entries = stringRedisTemplate.get().opsForHash().entries(key);
        if (entries.isEmpty()) return null;
        return mapToSensorData(lightId, entries);
    }

    /**
     * 将 Redis Hash 数据转换为 SensorData 实体
     */
    private SensorData mapToSensorData(Long lightId, Map<Object, Object> map) {
        SensorData data = new SensorData();
        data.setLightId(lightId);
        data.setIlluminance(getDouble(map, "illuminance"));
        data.setPower(getDouble(map, "power"));
        data.setVoltage(getDouble(map, "voltage"));
        data.setCurrent(getDouble(map, "current"));
        data.setTemperature(getDouble(map, "temperature"));
        data.setHumidity(getDouble(map, "humidity"));
        data.setSamplingEnergy(getDouble(map, "samplingEnergy"));
        data.setCollectTime(getLocalDateTime(map, "collectTime"));
        return data;
    }

    private Double getDouble(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDateTime getLocalDateTime(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null || val.toString().isEmpty()) return null;
        try {
            return LocalDateTime.parse(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 MySQL 补全 Redis 中缺失的传感器数据
     */
    private void fillMissingFromDb(List<Light> allLights, Map<Long, SensorData> result) {
        for (Light light : allLights) {
            if (result.containsKey(light.getId())) continue;

            try {
                LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(SensorData::getLightId, light.getId())
                       .orderByDesc(SensorData::getCollectTime)
                       .last("LIMIT 1");
                SensorData dbData = this.getOne(wrapper);
                if (dbData != null) {
                    result.put(light.getId(), dbData);
                }
            } catch (Exception e) {
                log.warn("补全传感器数据失败: lightId={}", light.getId(), e);
            }
        }
    }
}