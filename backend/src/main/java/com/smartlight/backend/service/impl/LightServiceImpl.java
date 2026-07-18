package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.service.LightService;
import com.smartlight.backend.service.MqttPublishService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 路灯管理服务（含 Redis 缓存）
 * <p>
 * - 全量路灯列表缓存到 Redis，启动时自动加载，增删改时自动失效<br>
 * - 分页查询（带筛选条件）直接查 MySQL，缓存收益小<br>
 * - 行政区/路段/设备类型列表从全量缓存中派生
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LightServiceImpl extends ServiceImpl<LightMapper, Light> implements LightService {

    private final MqttPublishService mqttPublishService;
    private final Optional<StringRedisTemplate> stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** Redis Key：全量路灯列表缓存 */
    private static final String KEY_LIGHT_ALL = "light:all";
    /** 缓存 TTL（秒）：路灯元数据变化不频繁，5 分钟足够 */
    private static final long LIGHT_CACHE_TTL_SECONDS = 300;

    @PostConstruct
    public void initCache() {
        log.info("初始化路灯缓存...");
        if (stringRedisTemplate.isEmpty()) {
            log.info("Redis 不可用，跳过缓存初始化");
            return;
        }
        try {
            List<Light> lights = baseMapper.selectList(null);
            if (lights != null && !lights.isEmpty()) {
                stringRedisTemplate.get().opsForValue().set(
                        KEY_LIGHT_ALL,
                        objectMapper.writeValueAsString(lights),
                        LIGHT_CACHE_TTL_SECONDS,
                        TimeUnit.SECONDS);
                log.info("路灯缓存初始化完成: {} 条", lights.size());
            }
        } catch (Exception e) {
            log.error("路灯缓存初始化失败: {}", e.getMessage());
        }
    }

    @Override
    public IPage<Light> getPage(int pageNum, int pageSize, String keyword, Integer status,
                                String district, String road, String deviceType) {
        // 分页查询带筛选条件，缓存命中率低，直接查 MySQL
        Page<Light> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Light> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Light::getLightName, keyword)
                              .or()
                              .like(Light::getLightCode, keyword)
                              .or()
                              .like(Light::getLocation, keyword));
        }

        if (status != null) {
            wrapper.eq(Light::getStatus, status);
        }

        if (StringUtils.hasText(district)) {
            wrapper.eq(Light::getDistrict, district);
        }

        if (StringUtils.hasText(road)) {
            wrapper.eq(Light::getRoad, road);
        }

        if (StringUtils.hasText(deviceType)) {
            wrapper.eq(Light::getDeviceType, deviceType);
        }

        wrapper.orderByDesc(Light::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSwitchStatus(List<Long> ids, Integer status) {
        List<Light> lights = this.listByIds(ids);
        for (Light light : lights) {
            light.setStatus(status);
            light.setManualControl(true);
            if (status == 0) {
                light.setBrightness(0);
            } else if (status == 1 && (light.getBrightness() == null || light.getBrightness() == 0)) {
                light.setBrightness(100);
            }
        }
        boolean result = this.updateBatchById(lights);

        // MQTT发布控制命令到每盏路灯
        for (Light light : lights) {
            mqttPublishService.publishSwitchControl(light.getLightCode(), light.getStatus());
        }

        // 更新缓存
        evictCache();

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setBrightness(Long id, Integer brightness) {
        Light light = this.getById(id);
        if (light == null) {
            return false;
        }
        light.setBrightness(brightness);
        if (brightness > 0) {
            light.setStatus(1);
        } else {
            light.setStatus(0);
        }
        // 人为调光优先级最高，标记为手动控制
        light.setManualControl(true);
        boolean result = this.updateById(light);

        // MQTT发布组合命令（含状态+亮度）
        mqttPublishService.publishCombinedControl(light.getLightCode(), light.getStatus(), brightness);

        // 更新缓存
        evictCache();

        return result;
    }

    @Override
    public long countByStatus(Integer status) {
        LambdaQueryWrapper<Light> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Light::getStatus, status);
        return this.count(wrapper);
    }

    @Override
    public List<String> getDistricts() {
        List<Light> lights = getCachedList();
        return lights.stream()
                .map(Light::getDistrict)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getRoads() {
        List<Light> lights = getCachedList();
        return lights.stream()
                .map(Light::getRoad)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getDeviceTypes() {
        List<Light> lights = getCachedList();
        return lights.stream()
                .map(Light::getDeviceType)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<Light> getCachedList() {
        // 优先从 Redis 读取全量路灯列表（Redis 不可用时直接查 MySQL）
        if (stringRedisTemplate.isPresent()) {
            try {
                String json = stringRedisTemplate.get().opsForValue().get(KEY_LIGHT_ALL);
                if (json != null) {
                    return objectMapper.readValue(json, new TypeReference<List<Light>>() {});
                }
            } catch (Exception e) {
                log.warn("从 Redis 读取路灯列表失败，回退到 MySQL: {}", e.getMessage());
            }
        }

        // Redis Miss 或 Redis 不可用，回退到 MySQL 并尝试重建缓存
        List<Light> lights = baseMapper.selectList(null);
        if (lights != null && !lights.isEmpty() && stringRedisTemplate.isPresent()) {
            try {
                stringRedisTemplate.get().opsForValue().set(
                        KEY_LIGHT_ALL,
                        objectMapper.writeValueAsString(lights),
                        LIGHT_CACHE_TTL_SECONDS,
                        TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("重建路灯缓存失败: {}", e.getMessage());
            }
        }
        return lights;
    }

    /**
     * 使路灯缓存失效（增删改操作后调用）
     */
    private void evictCache() {
        if (stringRedisTemplate.isEmpty()) {
            return;
        }
        try {
            stringRedisTemplate.get().delete(KEY_LIGHT_ALL);
            log.debug("路灯缓存已失效");
        } catch (Exception e) {
            log.warn("路灯缓存失效失败: {}", e.getMessage());
        }
    }
}