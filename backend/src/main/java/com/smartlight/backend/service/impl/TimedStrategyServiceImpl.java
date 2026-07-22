package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.TimedStrategyMapper;
import com.smartlight.backend.service.MqttPublishService;
import com.smartlight.backend.service.TimedStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimedStrategyServiceImpl implements TimedStrategyService {

    private final TimedStrategyMapper timedStrategyMapper;
    private final LightMapper lightMapper;
    private final MqttPublishService mqttPublishService;
    private final ApplicationContext applicationContext;

    @Override
    public IPage<TimedStrategy> getPage(int pageNum, int pageSize, String type, String name) {
        Page<TimedStrategy> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TimedStrategy> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isEmpty()) {
            wrapper.eq(TimedStrategy::getType, type);
        }
        if (name != null && !name.isEmpty()) {
            wrapper.like(TimedStrategy::getName, name);
        }
        wrapper.orderByDesc(TimedStrategy::getCreateTime);
        return timedStrategyMapper.selectPage(page, wrapper);
    }

    @Override
    public List<TimedStrategy> listAll() {
        return timedStrategyMapper.selectList(null);
    }

    @Override
    public List<TimedStrategy> listEnabled() {
        return timedStrategyMapper.selectList(
            new LambdaQueryWrapper<TimedStrategy>().eq(TimedStrategy::getEnabled, true)
        );
    }

    @Override
    public TimedStrategy getById(Long id) {
        return timedStrategyMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(TimedStrategy strategy) {
        strategy.setName(generateUniqueName(strategy.getName(), null));
        validateStrategy(strategy);
        try {
            return timedStrategyMapper.insert(strategy) > 0;
        } catch (DuplicateKeyException e) {
            strategy.setName(generateUniqueName(strategy.getName(), null));
            return timedStrategyMapper.insert(strategy) > 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(TimedStrategy strategy) {
        strategy.setName(generateUniqueName(strategy.getName(), strategy.getId()));
        validateStrategy(strategy);
        boolean updated;
        try {
            updated = timedStrategyMapper.updateById(strategy) > 0;
        } catch (DuplicateKeyException e) {
            strategy.setName(generateUniqueName(strategy.getName(), strategy.getId()));
            updated = timedStrategyMapper.updateById(strategy) > 0;
        }
        if (updated && Boolean.TRUE.equals(strategy.getEnabled())) {
            applyStrategyImmediately(strategy);
        }
        return updated;
    }

    private String generateUniqueName(String originalName, Long excludeId) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return originalName;
        }
        String baseName = originalName.trim();
        LambdaQueryWrapper<TimedStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(TimedStrategy::getName, baseName);
        if (excludeId != null) {
            wrapper.ne(TimedStrategy::getId, excludeId);
        }
        List<TimedStrategy> existing = timedStrategyMapper.selectList(wrapper);

        if (existing.stream().noneMatch(s -> baseName.equals(s.getName()))) {
            return baseName;
        }

        int suffix = 1;
        int maxAttempts = 1000;
        while (suffix <= maxAttempts) {
            String candidate = baseName + "-" + suffix;
            final String finalCandidate = candidate;
            if (existing.stream().noneMatch(s -> finalCandidate.equals(s.getName()))) {
                return candidate;
            }
            suffix++;
        }
        // 极端情况：回退到时间戳
        return baseName + "-" + System.currentTimeMillis();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        TimedStrategy strategy = getById(id);
        if (strategy != null && Boolean.TRUE.equals(strategy.getEnabled())) {
            turnOffLightsForStrategy(strategy);
        }
        return timedStrategyMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(Long id, boolean enabled) {
        TimedStrategy strategy = getById(id);
        if (strategy == null) {
            return false;
        }
        strategy.setEnabled(enabled);
        boolean updated = timedStrategyMapper.updateById(strategy) > 0;
        
        if (enabled) {
            applyStrategyImmediately(strategy);
        } else {
            turnOffLightsForStrategy(strategy);
        }
        
        return updated;
    }

    private void applyStrategyImmediately(TimedStrategy strategy) {
        LocalDateTime now = LocalDateTime.now();
        
        if (!isStrategyActive(now, strategy)) {
            return;
        }
        
        List<Light> lights = getLightsForStrategy(strategy);
        if (lights.isEmpty()) {
            log.info("策略「{}」未匹配到路灯", strategy.getName());
            return;
        }

        // ---------- 动态亮度预处理 ----------
        boolean useDynamic = Boolean.TRUE.equals(strategy.getUseDynamicBrightness());
        List<TimedStrategy.BrightnessSegment> segments = null;
        if (useDynamic) {
            segments = strategy.getBrightnessSegments();
            if (segments == null || segments.isEmpty()) {
                log.debug("策略「{}」启用动态亮度但未配置阈值分段，回退固定亮度", strategy.getName());
                useDynamic = false;
            }
        }

        int count = 0;
        for (Light light : lights) {
            if (Integer.valueOf(2).equals(light.getStatus())) continue; // 故障灯跳过
            if (Boolean.TRUE.equals(light.getManualControl())) continue; // 手动控制灯跳过

            Integer targetStatus;
            Integer targetBrightness;

            if (useDynamic) {
                // 动态模式：查找灯所属的区域分组，优先使用分组级阈值
                TimedStrategy.RegionGroup matchedGroup = findMatchingGroup(strategy, light);
                Double offThreshold = (matchedGroup != null && matchedGroup.getLightOffThreshold() != null)
                    ? matchedGroup.getLightOffThreshold()
                    : strategy.getLightOffThreshold();
                List<TimedStrategy.BrightnessSegment> effectiveSegments = (matchedGroup != null
                        && matchedGroup.getBrightnessSegments() != null
                        && !matchedGroup.getBrightnessSegments().isEmpty())
                    ? matchedGroup.getBrightnessSegments()
                    : segments;

                Double illuminance = getIlluminanceFromRedis(light.getId());
                if (illuminance == null) {
                    // 无传感器数据 → 跳过该灯，等待下次检查
                    continue;
                }
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

            if (!Integer.valueOf(1).equals(light.getStatus()) || !targetBrightness.equals(light.getBrightness())) {
                light.setStatus(targetStatus);
                light.setBrightness(targetBrightness);
                lightMapper.updateById(light);
                mqttPublishService.publishCombinedControl(light.getLightCode(), targetStatus, targetBrightness);
                count++;
            }
        }

        log.info("策略「{}」已立即执行，实际调整了 {} 盏路灯，模式: {}", 
                strategy.getName(), count, useDynamic ? "动态亮度" : "固定亮度");
    }

    private void turnOffLightsForStrategy(TimedStrategy strategy) {
        List<Light> lights = getLightsForStrategy(strategy);
        if (lights.isEmpty()) {
            return;
        }

        // 查询所有其他已启用的策略，用于保护检查
        List<TimedStrategy> otherActiveStrategies = timedStrategyMapper.selectList(
                new LambdaQueryWrapper<TimedStrategy>()
                        .eq(TimedStrategy::getEnabled, true)
                        .ne(TimedStrategy::getId, strategy.getId())
        );
        // 过滤出当前时间处于活跃时段的其他策略
        LocalDateTime now = LocalDateTime.now();
        List<TimedStrategy> activeOthers = otherActiveStrategies.stream()
                .filter(s -> isStrategyActive(now, s))
                .collect(Collectors.toList());

        int count = 0;
        for (Light light : lights) {
            if (Integer.valueOf(2).equals(light.getStatus())) continue; // 故障灯跳过
            if (Boolean.TRUE.equals(light.getManualControl())) continue; // 手动控制灯跳过

            // 检查是否有其他活跃策略保护这盏灯
            boolean protectedByOther = false;
            for (TimedStrategy active : activeOthers) {
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
                // MQTT发布组合命令（关灯）
                mqttPublishService.publishCombinedControl(light.getLightCode(), 0, 0);
                count++;
            }
        }

        if (count > 0) {
            log.info("策略「{}」已停用，关闭了 {} 盏路灯（无其他活跃策略保护）", strategy.getName(), count);
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

    /**
     * 查找路灯匹配的策略区域分组。
     * 返回第一个匹配的 RegionGroup，若策略未配置分组（覆盖全部）或没有匹配的分组则返回 null。
     */
    private TimedStrategy.RegionGroup findMatchingGroup(TimedStrategy strategy, Light light) {
        List<TimedStrategy.RegionGroup> groups = strategy.getGroups();
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        for (TimedStrategy.RegionGroup group : groups) {
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

    /**
     * 根据光照值和阈值分段，匹配目标亮度百分比
     */
    private Integer matchSegmentBrightness(double illuminance, List<TimedStrategy.BrightnessSegment> segments) {
        TimedStrategy.BrightnessSegment matched = null;
        for (TimedStrategy.BrightnessSegment seg : segments) {
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
     * 从 Redis 读取某盏灯的最新光照值
     */
    private Double getIlluminanceFromRedis(Long lightId) {
        try {
            String key = "sensor:latest:" + lightId;
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate = 
                org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils.qualifiedBeanOfType(
                    applicationContext, org.springframework.data.redis.core.StringRedisTemplate.class, "stringRedisTemplate");
            if (redisTemplate == null) {
                return null;
            }
            java.util.Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            if (entries.isEmpty()) return null;
            Object val = entries.get("illuminance");
            if (val == null) return null;
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            log.debug("从 Redis 读取光照值失败: lightId={}, error={}", lightId, e.getMessage());
            return null;
        }
    }

    private boolean isStrategyActive(LocalDateTime now, TimedStrategy strategy) {
        if (!Boolean.TRUE.equals(strategy.getEnabled())) {
            return false;
        }
        
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

    private void validateStrategy(TimedStrategy strategy) {
        if (strategy.getName() == null || strategy.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("策略名称不能为空");
        }
        if (strategy.getType() == null || strategy.getType().isEmpty()) {
            throw new IllegalArgumentException("策略类型不能为空");
        }
        if (!"default".equals(strategy.getType()) && !"timed".equals(strategy.getType())) {
            throw new IllegalArgumentException("策略类型必须为 'default' 或 'timed'");
        }
        if (strategy.getStartTime() == null) {
            throw new IllegalArgumentException("开始时间不能为空");
        }
        if (strategy.getEndTime() == null) {
            throw new IllegalArgumentException("结束时间不能为空");
        }
        if (strategy.getBrightness() == null || strategy.getBrightness() < 0 || strategy.getBrightness() > 100) {
            throw new IllegalArgumentException("亮度值必须在 0-100 之间");
        }

        if ("default".equals(strategy.getType())) {
            List<Integer> weekdays = strategy.getWeekdays();
            if (weekdays == null || weekdays.isEmpty()) {
                throw new IllegalArgumentException("默认策略必须选择适用星期");
            }
        }

        if ("timed".equals(strategy.getType())) {
            if (strategy.getStartDate() == null) {
                throw new IllegalArgumentException("时间段策略必须指定开始日期");
            }
            if (strategy.getEndDate() == null) {
                throw new IllegalArgumentException("时间段策略必须指定结束日期");
            }
            if (strategy.getEndDate().isBefore(strategy.getStartDate())) {
                throw new IllegalArgumentException("结束日期不能早于开始日期");
            }
        }

        // 查询所有已启用的策略，不限类型（跨类型也应检测冲突）
        List<TimedStrategy> existing = timedStrategyMapper.selectList(
                new LambdaQueryWrapper<TimedStrategy>()
                        .eq(TimedStrategy::getEnabled, true)
        );

        for (TimedStrategy other : existing) {
            if (other.getId() != null && other.getId().equals(strategy.getId())) {
                continue;
            }
            if (!isTimeConflict(strategy, other)) {
                continue;
            }
            if (!isRegionConflict(strategy, other)) {
                continue;
            }
            throw new IllegalArgumentException("策略冲突：策略「" + other.getName() + "」与当前策略的适用时间和区域均存在重叠");
        }
    }

    private boolean isTimeConflict(TimedStrategy a, TimedStrategy b) {
        int DAY_MINUTES = 24 * 60;

        int aStart = a.getStartTime().toSecondOfDay() / 60;
        int aEnd   = a.getEndTime().toSecondOfDay() / 60;
        int bStart = b.getStartTime().toSecondOfDay() / 60;
        int bEnd   = b.getEndTime().toSecondOfDay() / 60;

        // 转为 0–1440 分钟段列表（跨天拆为两段：[start, 1440) 和 [0, end)）
        List<int[]> segsA = new ArrayList<>();
        if (aStart >= aEnd) {
            segsA.add(new int[]{aStart, DAY_MINUTES});
            segsA.add(new int[]{0, aEnd});
        } else {
            segsA.add(new int[]{aStart, aEnd});
        }

        List<int[]> segsB = new ArrayList<>();
        if (bStart >= bEnd) {
            segsB.add(new int[]{bStart, DAY_MINUTES});
            segsB.add(new int[]{0, bEnd});
        } else {
            segsB.add(new int[]{bStart, bEnd});
        }

        // 判断任一段有交集
        boolean dailyOverlap = false;
        outer:
        for (int[] sa : segsA) {
            for (int[] sb : segsB) {
                if (sa[0] < sb[1] && sb[0] < sa[1]) {
                    dailyOverlap = true;
                    break outer;
                }
            }
        }
        if (!dailyOverlap) {
            return false;
        }

        if ("default".equals(a.getType()) && "default".equals(b.getType())) {
            List<Integer> aDays = a.getWeekdays() == null ? List.of() : a.getWeekdays();
            List<Integer> bDays = b.getWeekdays() == null ? List.of() : b.getWeekdays();
            return aDays.stream().anyMatch(bDays::contains);
        }

        if ("timed".equals(a.getType()) && "timed".equals(b.getType())) {
            return !a.getEndDate().isBefore(b.getStartDate()) && !b.getEndDate().isBefore(a.getStartDate());
        }

        // 跨类型：default(星期) vs timed(日期范围) → 检查日期范围内是否包含default的任意星期
        TimedStrategy defaultOne = "default".equals(a.getType()) ? a : b;
        TimedStrategy timedOne = "default".equals(a.getType()) ? b : a;
        return isDefaultTimedDateOverlap(defaultOne, timedOne);
    }

    /**
     * 判断 default 策略的星期与 timed 策略的日期范围是否有交集
     */
    private boolean isDefaultTimedDateOverlap(TimedStrategy defaultStrategy, TimedStrategy timedStrategy) {
        List<Integer> weekdays = defaultStrategy.getWeekdays();
        if (weekdays == null || weekdays.isEmpty()) return true;
        LocalDate start = timedStrategy.getStartDate();
        LocalDate end = timedStrategy.getEndDate();
        if (start == null || end == null) return true;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (weekdays.contains(d.getDayOfWeek().getValue())) {
                return true;
            }
            d = d.plusDays(1);
        }
        return false;
    }

    private boolean isRegionConflict(TimedStrategy a, TimedStrategy b) {
        List<TimedStrategy.RegionGroup> aGroups = a.getGroups();
        List<TimedStrategy.RegionGroup> bGroups = b.getGroups();

        // 都没有区域限制 → 全区域冲突
        if ((aGroups == null || aGroups.isEmpty()) && (bGroups == null || bGroups.isEmpty())) {
            return true;
        }
        // 一方无区域限制 → 冲突
        if (aGroups == null || aGroups.isEmpty() || bGroups == null || bGroups.isEmpty()) {
            return true;
        }

        // 逐组检查是否有重叠
        for (TimedStrategy.RegionGroup ag : aGroups) {
            for (TimedStrategy.RegionGroup bg : bGroups) {
                // 行政区重叠判断
                String ad = ag.getDistrict() == null ? "" : ag.getDistrict();
                String bd = bg.getDistrict() == null ? "" : bg.getDistrict();
                if (!ad.isEmpty() && !bd.isEmpty() && !ad.equals(bd)) {
                    continue; // 行政区不同，跳过
                }

                // 路段重叠判断
                List<String> ar = ag.getRoads() == null ? List.of() : ag.getRoads();
                List<String> br = bg.getRoads() == null ? List.of() : bg.getRoads();
                if (ar.isEmpty() || br.isEmpty()) {
                    return true; // 至少一方无路段限制 → 冲突
                }
                if (ar.stream().anyMatch(br::contains)) {
                    return true;
                }
            }
        }
        return false;
    }
}