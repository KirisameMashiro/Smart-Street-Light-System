package com.smartlight.backend.scheduler;

import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.TimedStrategyMapper;
import com.smartlight.backend.service.MqttPublishService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimedStrategyScheduler {

    private final TimedStrategyMapper timedStrategyMapper;
    private final LightMapper lightMapper;
    private final MqttPublishService mqttPublishService;

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

        // Step 2: 先执行所有活跃策略（开灯）
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

    private void applyStrategyOn(TimedStrategy strategy) {
        List<Light> lights = getLightsForStrategy(strategy);
        if (lights.isEmpty()) {
            return;
        }

        int count = 0;
        for (Light light : lights) {
            if (light.getStatus() == 2) {
                continue;
            }
            if (light.getStatus() != 1 || !strategy.getBrightness().equals(light.getBrightness())) {
                light.setStatus(1);
                light.setBrightness(strategy.getBrightness());
                lightMapper.updateById(light);
                mqttPublishService.publishCombinedControl(light.getLightCode(), 1, strategy.getBrightness());
                count++;
            }
        }

        if (count > 0) {
            log.info("策略「{}」已开启 {} 盏路灯", strategy.getName(), count);
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
            if (light.getStatus() == 2) {
                continue;
            }
            if (Boolean.TRUE.equals(light.getManualControl())) {
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

            if (light.getStatus() != 0) {
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
        if (StringUtils.hasText(strategy.getDistrict()) && !strategy.getDistrict().equals(light.getDistrict())) {
            return false;
        }
        if (strategy.getRoads() != null && !strategy.getRoads().isEmpty()) {
            if (light.getRoad() == null || !strategy.getRoads().contains(light.getRoad())) {
                return false;
            }
        }
        return true;
    }

    private List<Light> getLightsForStrategy(TimedStrategy strategy) {
        LambdaQueryWrapper<Light> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(strategy.getDistrict())) {
            wrapper.eq(Light::getDistrict, strategy.getDistrict());
        }
        if (strategy.getRoads() != null && !strategy.getRoads().isEmpty()) {
            wrapper.in(Light::getRoad, strategy.getRoads());
        }

        return lightMapper.selectList(wrapper);
    }
}
