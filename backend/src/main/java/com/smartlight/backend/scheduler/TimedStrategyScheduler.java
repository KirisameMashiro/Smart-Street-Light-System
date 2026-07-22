package com.smartlight.backend.scheduler;

import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.TimedStrategyMapper;
import com.smartlight.backend.service.MqttPublishService;
import com.smartlight.backend.service.ThresholdControlService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimedStrategyScheduler {

    private final TimedStrategyMapper timedStrategyMapper;
    private final LightMapper lightMapper;
    private final MqttPublishService mqttPublishService;
    private final ThresholdControlService thresholdControlService;
    private final Optional<StringRedisTemplate> stringRedisTemplate;

    /** Redis Key 前缀：每盏路灯最新传感器数据（Hash 结构） */
    private static final String KEY_SENSOR_LATEST_PREFIX = "sensor:latest:";

    @PostConstruct
    public void onStartup() {
        log.info("系统启动，异步执行定时策略检查...");
        CompletableFuture.runAsync(this::executeStrategies);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void executeStrategies() {
        log.debug("定时策略调度器执行中...");

        List<TimedStrategy> all = timedStrategyMapper.selectList(
                new LambdaQueryWrapper<TimedStrategy>().eq(TimedStrategy::getEnabled, true)
        );

        if (all.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Step 1: 拆分为活跃策略和不活跃策略
        List<TimedStrategy> activeStrategies = new ArrayList<>();
        List<TimedStrategy> inactiveStrategies = new ArrayList<>();
        for (TimedStrategy s : all) {
            if (isStrategyActive(now, s)) {
                activeStrategies.add(s);
            } else {
                inactiveStrategies.add(s);
            }
        }

        // Step 2: 按优先级排序（timed 高于 default），然后执行所有活跃策略（开灯）
        activeStrategies.sort(Comparator.comparingInt(s -> "timed".equals(s.getType()) ? 1 : 0));
        for (TimedStrategy strategy : activeStrategies) {
            try {
                applyStrategyOn(strategy);
            } catch (Exception e) {
                log.error("执行策略「{}」开灯时发生错误", strategy.getName(), e);
            }
        }

        // Step 3: 处理不活跃策略的关灯 — 仅当灯不受其他活跃策略保护时才关
        for (TimedStrategy strategy : inactiveStrategies) {
            try {
                applyStrategyOffIfUnprotected(strategy, activeStrategies);
            } catch (Exception e) {
                log.error("执行策略「{}」关灯检查时发生错误", strategy.getName(), e);
            }
        }
    }

    private boolean isStrategyActive(LocalDateTime now, TimedStrategy strategy) {
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
     * 执行策略的开灯逻辑
     * <p>
     * 支持两种模式：
     * <ul>
     *   <li><b>固定亮度</b>（useDynamicBrightness=false，默认）：使用策略配置的固定 brightness 值</li>
     *   <li><b>动态亮度</b>（useDynamicBrightness=true）：策略负责开关时机，亮度由实时光照传感器数据
     *        + 阈值分段配置动态计算。若全局阈值开关关闭或无传感器数据，回退到固定亮度</li>
     * </ul>
     */
    private void applyStrategyOn(TimedStrategy strategy) {
        List<Light> lights = getLightsForStrategy(strategy);
        if (lights.isEmpty()) {
            return;
        }

        // ---------- 动态亮度预处理 ----------
        boolean useDynamic = Boolean.TRUE.equals(strategy.getUseDynamicBrightness());

        if (useDynamic) {
            // 全局阈值配置作为动态亮度的数据源
            var thresholdConfig = thresholdControlService.getConfig();
            if (!Boolean.TRUE.equals(thresholdConfig.getEnabled())) {
                log.debug("策略「{}」启用动态亮度但全局阈值已关闭，回退固定亮度", strategy.getName());
                useDynamic = false;
            } else if (thresholdConfig.getSegments() == null || thresholdConfig.getSegments().isEmpty()) {
                log.debug("策略「{}」启用动态亮度但阈值分段为空，回退固定亮度", strategy.getName());
                useDynamic = false;
            }
        }

        int count = 0;
        for (Light light : lights) {
            if (Integer.valueOf(2).equals(light.getStatus())) {
                continue; // 故障灯跳过
            }
            if (isUnderManualProtection(light)) {
                continue; // 手动控制保护期内跳过
            }

            Integer targetStatus = 1;
            Integer targetBrightness;

            if (useDynamic) {
                // 动态模式：读取实时光照，计算等效亮度
                Double illuminance = getIlluminanceFromRedis(light.getId());
                if (illuminance == null) {
                    // 无传感器数据 → 回退到策略固定亮度
                    targetBrightness = strategy.getBrightness();
                } else {
                    targetBrightness = thresholdControlService.findMatchingBrightness(illuminance);
                }
            } else {
                // 固定模式
                targetBrightness = strategy.getBrightness();
            }

            if (!Integer.valueOf(1).equals(light.getStatus()) || !targetBrightness.equals(light.getBrightness())) {
                light.setStatus(targetStatus);
                light.setBrightness(targetBrightness);
                lightMapper.updateById(light);
                mqttPublishService.publishCombinedControl(light.getLightCode(), targetStatus, targetBrightness);
                count++;
            }
        }

        if (count > 0) {
            log.info("策略「{}」已开启 {} 盏路灯，模式: {}",
                    strategy.getName(), count, useDynamic ? "动态亮度" : "固定亮度");
        }
    }

    /**
     * 不活跃策略的关灯逻辑：仅关闭那些不被任何活跃策略覆盖的路灯
     */
    private void applyStrategyOffIfUnprotected(TimedStrategy inactiveStrategy, List<TimedStrategy> activeStrategies) {
        List<Light> lights = getLightsForStrategy(inactiveStrategy);
        if (lights.isEmpty()) {
            return;
        }

        int count = 0;
        for (Light light : lights) {
            if (Integer.valueOf(2).equals(light.getStatus())) {
                continue;
            }
            if (isUnderManualProtection(light)) {
                continue;
            }

            // 检查是否有其他活跃策略保护这盏灯
            boolean protectedByOther = false;
            for (TimedStrategy active : activeStrategies) {
                if (strategyCoversLight(active, light)) {
                    protectedByOther = true;
                    break;
                }
            }

            if (protectedByOther) {
                continue; // 有其他活跃策略在管理这盏灯，不关
            }

            if (!Integer.valueOf(0).equals(light.getStatus())) {
                light.setStatus(0);
                light.setBrightness(0);
                lightMapper.updateById(light);
                mqttPublishService.publishCombinedControl(light.getLightCode(), 0, 0);
                count++;
            }
        }

        if (count > 0) {
            log.info("策略「{}」已关闭 {} 盏路灯（无其他活跃策略保护）", inactiveStrategy.getName(), count);
        }
    }

    /**
     * 判断某策略是否覆盖指定的路灯
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

    private List<Light> getLightsForStrategy(TimedStrategy strategy) {
        LambdaQueryWrapper<Light> wrapper = new LambdaQueryWrapper<>();

        List<TimedStrategy.RegionGroup> groups = strategy.getGroups();
        if (groups != null && !groups.isEmpty()) {
            // 过滤掉 district 和 roads 均为空的无意义分组
            List<TimedStrategy.RegionGroup> validGroups = groups.stream()
                .filter(g -> (g.getDistrict() != null && !g.getDistrict().isEmpty())
                          || (g.getRoads() != null && !g.getRoads().isEmpty()))
                .toList();
            if (!validGroups.isEmpty()) {
                wrapper.and(w -> {
                    boolean first = true;
                    for (TimedStrategy.RegionGroup group : validGroups) {
                        String district = group.getDistrict();
                        List<String> roads = group.getRoads();
                        boolean hasDistrict = district != null && !district.isEmpty();
                        boolean hasRoads = roads != null && !roads.isEmpty();

                        if (hasDistrict && hasRoads) {
                            if (first) {
                                w.eq(Light::getDistrict, district).in(Light::getRoad, roads);
                                first = false;
                            } else {
                                w.or(sub -> sub.eq(Light::getDistrict, district).in(Light::getRoad, roads));
                            }
                        } else if (hasDistrict) {
                            if (first) {
                                w.eq(Light::getDistrict, district);
                                first = false;
                            } else {
                                w.or(sub -> sub.eq(Light::getDistrict, district));
                            }
                        } else if (hasRoads) {
                            if (first) {
                                w.in(Light::getRoad, roads);
                                first = false;
                            } else {
                                w.or(sub -> sub.in(Light::getRoad, roads));
                            }
                        }
                    }
                });
            }
        }

        return lightMapper.selectList(wrapper);
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
            return true;
        }
        long elapsedMinutes = ChronoUnit.MINUTES.between(updateTime, LocalDateTime.now());
        if (elapsedMinutes >= 30) {
            light.setManualControl(false);
            lightMapper.updateById(light);
            log.info("手动控制超时释放: lightId={}, 已过 {} 分钟", light.getId(), elapsedMinutes);
            return false;
        }
        return true;
    }

    /**
     * 从 Redis 读取某盏灯的最新光照值
     * <p>
     * 实时传感器数据通过 SensorDataIngestService 写入 Redis Hash
     * {@code sensor:latest:{lightId}}，阈值为 {@code illuminance}。
     *
     * @param lightId 路灯ID
     * @return 光照值 (lux)，无数据返回 null
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
