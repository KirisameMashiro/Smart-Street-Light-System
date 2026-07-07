package com.smartlight.backend.scheduler;

import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.TimedStrategyMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimedStrategyScheduler {

    private final TimedStrategyMapper timedStrategyMapper;
    private final LightMapper lightMapper;

    @Scheduled(cron = "0 * * * * ?")
    public void executeStrategies() {
        log.debug("定时策略调度器执行中...");
        
        List<TimedStrategy> enabledStrategies = timedStrategyMapper.selectList(
                new LambdaQueryWrapper<TimedStrategy>().eq(TimedStrategy::getEnabled, true)
        );
        
        LocalDateTime now = LocalDateTime.now();
        
        for (TimedStrategy strategy : enabledStrategies) {
            try {
                if (isStrategyActive(now, strategy)) {
                    applyStrategyOn(strategy);
                } else {
                    applyStrategyOff(strategy);
                }
            } catch (Exception e) {
                log.error("执行策略「{}」时发生错误", strategy.getName(), e);
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
                count++;
            }
        }
        
        if (count > 0) {
            log.info("策略「{}」已开启 {} 盏路灯", strategy.getName(), count);
        }
    }

    private void applyStrategyOff(TimedStrategy strategy) {
        List<Light> lights = getLightsForStrategy(strategy);
        if (lights.isEmpty()) {
            return;
        }
        
        int count = 0;
        for (Light light : lights) {
            if (light.getStatus() == 2) {
                continue;
            }
            if (light.getStatus() != 0) {
                light.setStatus(0);
                light.setBrightness(0);
                lightMapper.updateById(light);
                count++;
            }
        }
        
        if (count > 0) {
            log.info("策略「{}」已关闭 {} 盏路灯", strategy.getName(), count);
        }
    }

    private List<Light> getLightsForStrategy(TimedStrategy strategy) {
        LambdaQueryWrapper<Light> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(strategy.getDistrict())) {
            wrapper.eq(Light::getDistrict, strategy.getDistrict());
        }
        if (StringUtils.hasText(strategy.getRoad())) {
            wrapper.eq(Light::getRoad, strategy.getRoad());
        }
        
        return lightMapper.selectList(wrapper);
    }
}
