package com.smartlight.backend.scheduler;

import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.entity.TimedStrategy.BrightnessSegment;
import com.smartlight.backend.entity.TimedStrategy.RegionGroup;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.TimedStrategyMapper;
import com.smartlight.backend.service.MqttPublishService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimedStrategyScheduler {

    private final TimedStrategyMapper timedStrategyMapper;
    private final LightMapper lightMapper;
    private final MqttPublishService mqttPublishService;
    private final Optional<StringRedisTemplate> stringRedisTemplate;

    /** Redis Key 前缀：每盏路灯最新传感器数据（Hash 结构） */
    private static final String KEY_SENSOR_LATEST_PREFIX = "sensor:latest:";

    /** 光照值滑动窗口大小，用于平滑滤波 */
    private static final int ILLUMINANCE_WINDOW_SIZE = 3;

    /** 每盏灯近几次光照值的滑动窗口 */
    private final ConcurrentHashMap<Long, LinkedList<Double>> illuminanceWindow = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 10000)
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

        // Step 2: 按优先级排序（timed 高于 default），每盏灯只由最高优先级策略控制
        activeStrategies.sort(Comparator.comparingInt(s -> "timed".equals(s.getType()) ? 1 : 0));
        Set<Long> controlledLights = new HashSet<>();
        for (TimedStrategy strategy : activeStrategies) {
            try {
                applyStrategyOn(strategy, controlledLights);
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
     *   <li><b>动态亮度</b>（useDynamicBrightness=true）：策略负责开关时机，亮度由每盏灯的
     *       实时光照传感器数据 + 策略自身的阈值分段配置动态计算。阈值配置为策略级独立管理，
     *       不再依赖全局阈值表，实现按区域/策略的精准控制。</li>
     * </ul>
     */
    private void applyStrategyOn(TimedStrategy strategy, Set<Long> controlledLights) {
        List<Light> lights = getLightsForStrategy(strategy);
        if (lights.isEmpty()) {
            return;
        }

        // ---------- 动态亮度预处理 ----------
        boolean useDynamic = Boolean.TRUE.equals(strategy.getUseDynamicBrightness());
        List<BrightnessSegment> segments = null;

        if (useDynamic) {
            segments = strategy.getBrightnessSegments();
            if (segments == null || segments.isEmpty()) {
                log.debug("策略「{}」启用动态亮度但未配置阈值分段，回退固定亮度", strategy.getName());
                useDynamic = false;
            }
        }

        int count = 0;
        int skipped = 0;
        for (Light light : lights) {
            if (Integer.valueOf(2).equals(light.getStatus())) {
                continue; // 故障灯跳过
            }
            if (isUnderManualProtection(light)) {
                continue; // 手动控制保护期内跳过
            }
            // 已被更高优先级策略控制，跳过
            if (controlledLights.contains(light.getId())) {
                skipped++;
                continue;
            }

            Integer targetStatus;
            Integer targetBrightness;

            if (useDynamic) {
                // 动态模式：查找灯所属的区域分组，优先使用分组级阈值
                RegionGroup matchedGroup = findMatchingGroup(strategy, light);
                Double offThreshold = (matchedGroup != null && matchedGroup.getLightOffThreshold() != null)
                    ? matchedGroup.getLightOffThreshold()
                    : strategy.getLightOffThreshold();
                List<BrightnessSegment> effectiveSegments = (matchedGroup != null
                        && matchedGroup.getBrightnessSegments() != null
                        && !matchedGroup.getBrightnessSegments().isEmpty())
                    ? matchedGroup.getBrightnessSegments()
                    : segments;

                Double rawIlluminance = getIlluminanceFromRedis(light.getId());
                if (rawIlluminance == null) {
                    // 无传感器数据 → 跳过该灯，等待下次检查
                    continue;
                }
                double illuminance = getSmoothedIlluminance(light.getId(), rawIlluminance);
                // 滞后区间防抖：正在亮灯时需光照高于阈值120%才关，已关灯时需低于阈值80%才开
                double hysteresisOff = (offThreshold != null) ? offThreshold * 1.20 : Double.MAX_VALUE;
                double hysteresisOn  = (offThreshold != null) ? offThreshold * 0.80 : 0;
                boolean currentlyOn = Integer.valueOf(1).equals(light.getStatus());
                if (currentlyOn && illuminance > hysteresisOff) {
                    targetBrightness = 0;
                    targetStatus = 0;
                } else if (!currentlyOn && illuminance > hysteresisOn) {
                    // 已关灯且光照不够暗 → 保持关灯
                    targetBrightness = 0;
                    targetStatus = 0;
                } else if (!currentlyOn && illuminance <= hysteresisOn) {
                    // 已关灯且光照够暗了 → 开灯
                    targetBrightness = matchSegmentBrightness(illuminance, effectiveSegments);
                    targetStatus = 1;
                } else {
                    // 正在亮灯且光照 <= hysteresisOff → 继续按动态亮度调节
                    targetBrightness = matchSegmentBrightness(illuminance, effectiveSegments);
                    targetStatus = 1;
                }
            } else {
                // 固定模式
                targetStatus = 1;
                targetBrightness = strategy.getBrightness();
            }

            boolean statusChanged = !targetStatus.equals(light.getStatus());
            boolean brightnessChanged = !targetBrightness.equals(light.getBrightness());

            if (statusChanged || brightnessChanged) {
                light.setStatus(targetStatus);
                light.setBrightness(targetBrightness);
                lightMapper.updateById(light);
                mqttPublishService.publishCombinedControl(light.getLightCode(), targetStatus, targetBrightness);
                count++;
            }
            controlledLights.add(light.getId());
        }

        if (count > 0) {
            log.info("策略「{}」已更新 {} 盏路灯（跳过 {} 盏已被更高优先级控制），模式: {}",
                    strategy.getName(), count, skipped, useDynamic ? "动态亮度" : "固定亮度");
        }
    }

    /**
     * 根据光照值和策略自身的阈值分段，匹配目标亮度百分比
     * <p>
     * 规则：{@code segments} 按 threshold 升序排列。
     * 光照越暗（值越小），匹配的亮度越高。
     * 遍历 segments，找到第一个 illuminance <= seg.threshold 的档位。
     * 若光照超出所有档位阈值（光照极亮），返回最高阈值档位的亮度（最低档亮度）。
     *
     * @param illuminance 实时光照值 (lux)
     * @param segments    策略自身的亮度分段配置（阈值升序）
     * @return 匹配的亮度百分比 (0-100)
     */
    private Integer matchSegmentBrightness(double illuminance, List<BrightnessSegment> segments) {
        BrightnessSegment matched = null;
        for (BrightnessSegment seg : segments) {
            if (illuminance <= seg.getThreshold()) {
                if (matched == null || seg.getThreshold() < matched.getThreshold()) {
                    matched = seg;
                }
            }
        }
        if (matched == null && !segments.isEmpty()) {
            matched = segments.stream()
                    .max((a, b) -> Double.compare(a.getThreshold(), b.getThreshold()))
                    .orElse(null);
        }
        return matched != null && matched.getBrightness() != null ? matched.getBrightness() : 100;
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
        return findMatchingGroup(strategy, light) != null
            || (strategy.getGroups() == null || strategy.getGroups().isEmpty());
    }

    /**
     * 查找路灯匹配的策略区域分组。
     * 返回第一个匹配的 RegionGroup，若策略未配置分组（覆盖全部）或没有匹配的分组则返回 null。
     */
    private RegionGroup findMatchingGroup(TimedStrategy strategy, Light light) {
        List<RegionGroup> groups = strategy.getGroups();
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        for (RegionGroup group : groups) {
            boolean districtMatch = group.getDistrict() == null || group.getDistrict().isEmpty()
                    || group.getDistrict().equals(light.getDistrict());
            boolean roadMatch = group.getRoads() == null || group.getRoads().isEmpty()
                    || (light.getRoad() != null && group.getRoads().contains(light.getRoad()));
            if (districtMatch && roadMatch) {
                return group;
            }
        }
        return null;
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

    /**
     * 滑动窗口平均滤波：用最近 N 次光照值的平均数做判断，消除传感器尖峰抖动。
     * 例如 450 → 38 → 450 这种剧烈跳变会被平滑为 (450+38+450)/3 ≈ 313。
     *
     * @param lightId 路灯ID
     * @param currentValue 当前从 Redis 读取的原始光照值
     * @return 滑动窗口内的平均值
     */
    private double getSmoothedIlluminance(Long lightId, double currentValue) {
        LinkedList<Double> window = illuminanceWindow.computeIfAbsent(lightId, k -> new LinkedList<>());
        window.addLast(currentValue);
        while (window.size() > ILLUMINANCE_WINDOW_SIZE) {
            window.removeFirst();
        }
        double sum = 0;
        for (Double v : window) {
            sum += v;
        }
        return sum / window.size();
    }
}
