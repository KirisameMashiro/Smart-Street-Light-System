package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.ThresholdControl;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.ThresholdControlMapper;
import com.smartlight.backend.mapper.TimedStrategyMapper;
import com.smartlight.backend.service.MqttPublishService;
import com.smartlight.backend.service.SystemConfigService;
import com.smartlight.backend.service.ThresholdControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdControlServiceImpl implements ThresholdControlService {

    private final ThresholdControlMapper thresholdControlMapper;
    private final LightMapper lightMapper;
    private final TimedStrategyMapper timedStrategyMapper;
    private final MqttPublishService mqttPublishService;
    private final SystemConfigService systemConfigService;
    private final Optional<StringRedisTemplate> stringRedisTemplate;

    /** Redis Key 前缀：每盏路灯最新传感器数据 */
    /** 上次阈值联动执行时间（毫秒时间戳，用于动态周期控制） */
    private volatile long lastLinkageRunTime = 0;
    private static final String KEY_SENSOR_LATEST_PREFIX = "sensor:latest:";

    @Override
    public ThresholdControl getConfig() {
        List<ThresholdControl> list = thresholdControlMapper.selectList(null);
        if (list == null || list.isEmpty()) {
            ThresholdControl defaults = new ThresholdControl();
            defaults.setEnabled(false);
            defaults.setLightOffThreshold(100.0);
            defaults.setSegments(List.of(
                    seg(30, 100),
                    seg(60, 60),
                    seg(90, 30)));
            return defaults;
        }
        return list.get(0);
    }

    private static ThresholdControl.SegmentConfig seg(double threshold, int brightness) {
        ThresholdControl.SegmentConfig s = new ThresholdControl.SegmentConfig();
        s.setThreshold(threshold);
        s.setBrightness(brightness);
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveConfig(ThresholdControl config) {
        // 验证 segments：光照阈值越小 → 亮度必须越高（越暗越亮）
        List<ThresholdControl.SegmentConfig> segs = config.getSegments();
        if (segs != null && segs.size() > 1) {
            for (int i = 1; i < segs.size(); i++) {
                ThresholdControl.SegmentConfig prev = segs.get(i - 1);
                ThresholdControl.SegmentConfig curr = segs.get(i);
                if (curr.getThreshold() > prev.getThreshold()
                        && curr.getBrightness() != null && prev.getBrightness() != null
                        && curr.getBrightness() > prev.getBrightness()) {
                    throw new IllegalArgumentException(
                            "调光档位不合法：光照阈值 " + curr.getThreshold()
                                    + " > " + prev.getThreshold()
                                    + "，但亮度 " + curr.getBrightness()
                                    + "% > " + prev.getBrightness()
                                    + "%。越暗应越亮，高阈值的亮度不应高于低阈值");
                }
            }
        }

        List<ThresholdControl> list = thresholdControlMapper.selectList(null);
        if (list == null || list.isEmpty()) {
            config.setId(null);
            return thresholdControlMapper.insert(config) > 0;
        } else {
            ThresholdControl existing = list.get(0);
            config.setId(existing.getId());
            return thresholdControlMapper.updateById(config) > 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(boolean enabled) {
        ThresholdControl config = getConfig();
        config.setEnabled(enabled);
        if (config.getId() == null) {
            return thresholdControlMapper.insert(config) > 0;
        } else {
            return thresholdControlMapper.updateById(config) > 0;
        }
    }

    /**
     * 定时执行光照联动控制（每 5 秒轮询一次，实际执行间隔由 system_config 动态控制）
     * <p>
     * 读取所有路灯的最新光照传感器数据，根据阈值配置自动调节每盏路灯的开关与亮度。
     * {@code manualControl = true} 的路灯完全跳过，不受自动联动影响。
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void autoLinkageControl() {
        // 从 system_config 读取配置的判定周期，默认 60 秒
        String intervalStr = systemConfigService.getConfigValue("threshold_check_interval");
        long intervalMillis = (intervalStr != null ? Long.parseLong(intervalStr) : 60) * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastLinkageRunTime < intervalMillis) {
            return;
        }
        lastLinkageRunTime = now;

        ThresholdControl config = getConfig();
        if (!config.getEnabled()) {
            return;
        }

        Double lightOff = config.getLightOffThreshold();
        List<ThresholdControl.SegmentConfig> segments = config.getSegments();

        if (lightOff == null) return;
        if (segments == null || segments.isEmpty()) {
            return;
        }

        List<Light> allLights = lightMapper.selectList(null);

        // 预计算：当前受活跃定时策略控制的路灯ID集合（一轮只查一次DB）
        Set<Long> strategyControlledLightIds = computeStrategyControlledLightIds(allLights);
        if (!strategyControlledLightIds.isEmpty()) {
            log.debug("阈值联动: {} 盏路灯受定时策略保护，本轮跳过", strategyControlledLightIds.size());
        }

        int adjustedCount = 0;
        for (Light light : allLights) {
            // 手动控制保护期内跳过（超时后自动释放）
            if (isUnderManualProtection(light)) {
                continue;
            }

            // 受活跃定时策略控制的路灯跳过，定时策略优先级高于阈值联动
            if (strategyControlledLightIds.contains(light.getId())) {
                continue;
            }

            // 故障状态的路灯不应被阈值联动改变状态或调光
            if (Integer.valueOf(2).equals(light.getStatus())) {
                continue;
            }

            // 从 Redis 读取最新光照传感器数据（实时数据已缓存于 Redis，不再写入 sensor_data 表）
            Double illuminance = getIlluminanceFromRedis(light.getId());
            if (illuminance == null) {
                continue;
            }
            Integer targetStatus;
            Integer targetBrightness;

            if (illuminance > lightOff) {
                targetStatus = 0;
                targetBrightness = 0;
            } else {
                targetStatus = 1;
                targetBrightness = findMatchingBrightness(illuminance, segments);
            }

            boolean changed = false;
            if (!Integer.valueOf(targetStatus).equals(light.getStatus())) {
                light.setStatus(targetStatus);
                changed = true;
            }
            if (!targetBrightness.equals(light.getBrightness())) {
                light.setBrightness(targetBrightness);
                changed = true;
            }

            if (changed) {
                lightMapper.updateById(light);
                mqttPublishService.publishCombinedControl(light.getLightCode(), targetStatus, targetBrightness);
                adjustedCount++;
                log.debug("阈值联动: lightId={}, 光照={}lux, 状态={}, 亮度={}%",
                        light.getId(), illuminance, targetStatus, targetBrightness);
            }
        }

        if (adjustedCount > 0) {
            log.info("阈值联动: 已调节 {} 盏路灯", adjustedCount);
        }
    }

    /**
     * 判断路灯是否处于手动控制保护期内
     * <p>
     * manualControl=true 的路灯在 30 分钟内不被自动化任务调节，
     * 超时后自动释放（清除 manualControl 标记），恢复自动控制。
     */
    private boolean isUnderManualProtection(Light light) {
        if (!Boolean.TRUE.equals(light.getManualControl())) {
            return false;
        }
        LocalDateTime updateTime = light.getUpdateTime();
        if (updateTime == null) {
            // 无时间信息则保守跳过
            return true;
        }
        long elapsedMinutes = ChronoUnit.MINUTES.between(updateTime, LocalDateTime.now());
        if (elapsedMinutes >= 30) {
            // 超时，自动释放
            light.setManualControl(false);
            lightMapper.updateById(light);
            log.info("手动控制超时释放: lightId={}, 已过 {} 分钟", light.getId(), elapsedMinutes);
            return false;
        }
        return true;
    }

    /**
     * 预计算当前受活跃定时策略控制的路灯ID集合
     * <p>
     * 一轮阈值联动只查一次DB，遍历所有启用策略，将当前活跃
     * 且覆盖范围内的路灯ID全部收入Set中，后续 O(1) 判定。
     */
    private Set<Long> computeStrategyControlledLightIds(List<Light> allLights) {
        List<TimedStrategy> enabledStrategies = timedStrategyMapper.selectList(
                new LambdaQueryWrapper<TimedStrategy>().eq(TimedStrategy::getEnabled, true)
        );
        if (enabledStrategies.isEmpty()) {
            return Collections.emptySet();
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> ids = new HashSet<>();
        for (TimedStrategy strategy : enabledStrategies) {
            if (!isTimedStrategyActive(now, strategy)) {
                continue;
            }
            for (Light light : allLights) {
                if (strategyCoversLight(strategy, light)) {
                    ids.add(light.getId());
                }
            }
        }
        return ids;
    }

    /**
     * 判断定时策略当前是否处于活跃时段
     */
    private boolean isTimedStrategyActive(LocalDateTime now, TimedStrategy strategy) {
        LocalTime currentTime = now.toLocalTime();
        LocalTime startTime = strategy.getStartTime();
        LocalTime endTime = strategy.getEndTime();

        boolean timeMatch;
        if (startTime.isBefore(endTime)) {
            timeMatch = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        } else {
            timeMatch = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
        }

        if (!timeMatch) {
            return false;
        }

        if ("timed".equals(strategy.getType())) {
            if (strategy.getStartDate() == null || strategy.getEndDate() == null) {
                return false;
            }
            LocalDate currentDate = now.toLocalDate();
            return !currentDate.isBefore(strategy.getStartDate()) && !currentDate.isAfter(strategy.getEndDate());
        }

        if ("default".equals(strategy.getType())) {
            List<Integer> weekdays = strategy.getWeekdays();
            if (weekdays == null || weekdays.isEmpty()) {
                return false;
            }
            int currentDayOfWeek = now.getDayOfWeek().getValue();
            return weekdays.contains(currentDayOfWeek);
        }

        return false;
    }

    /**
     * 判断定时策略是否覆盖指定的路灯
     */
    private boolean strategyCoversLight(TimedStrategy strategy, Light light) {
        List<TimedStrategy.RegionGroup> groups = strategy.getGroups();
        if (groups == null || groups.isEmpty()) {
            return true;
        }
        for (TimedStrategy.RegionGroup group : groups) {
            boolean districtMatch = group.getDistrict() == null || group.getDistrict().isEmpty()
                    || group.getDistrict().equals(light.getDistrict());
            boolean roadMatch = group.getRoads() == null || group.getRoads().isEmpty()
                    || (light.getRoad() != null && group.getRoads().contains(light.getRoad()));
            if (districtMatch && roadMatch) {
                return true;
            }
        }
        return false;
    }

    private Integer findMatchingBrightness(double illuminance, List<ThresholdControl.SegmentConfig> segments) {
        ThresholdControl.SegmentConfig matched = null;
        for (ThresholdControl.SegmentConfig seg : segments) {
            if (illuminance <= seg.getThreshold()) {
                // 选 threshold 最小的档位（最严格匹配），即最暗场景对应最亮灯光
                if (matched == null || seg.getThreshold() < matched.getThreshold()) {
                    matched = seg;
                }
            }
        }
        // 光照超出所有档位阈值 → 选 threshold 最大的档位（环境相对最亮，只需最低亮度）
        if (matched == null && !segments.isEmpty()) {
            matched = segments.stream()
                    .max((a, b) -> Double.compare(a.getThreshold(), b.getThreshold()))
                    .orElse(null);
        }
        return matched != null && matched.getBrightness() != null ? matched.getBrightness() : 100;
    }

    /**
     * 从 Redis 读取某盏灯的最新光照值
     * <p>
     * 实时传感器数据通过 SensorDataIngestService 写入 Redis Hash
     * {@code sensor:latest:{lightId}}，阈值为 {@code illuminance}。
     */
    private Double getIlluminanceFromRedis(Long lightId) {
        if (stringRedisTemplate.isEmpty()) {
            return null;
        }
        String key = KEY_SENSOR_LATEST_PREFIX + lightId;
        Map<Object, Object> entries = stringRedisTemplate.get().opsForHash().entries(key);
        if (entries.isEmpty()) return null;

        Object val = entries.get("illuminance");
        if (val == null) return null;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}