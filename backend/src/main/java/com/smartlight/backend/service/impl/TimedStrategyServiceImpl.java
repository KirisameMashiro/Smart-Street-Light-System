package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.TimedStrategy;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.TimedStrategyMapper;
import com.smartlight.backend.service.TimedStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimedStrategyServiceImpl implements TimedStrategyService {

    private final TimedStrategyMapper timedStrategyMapper;
    private final LightMapper lightMapper;

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
    public TimedStrategy getById(Long id) {
        return timedStrategyMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(TimedStrategy strategy) {
        validateStrategy(strategy);
        return timedStrategyMapper.insert(strategy) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(TimedStrategy strategy) {
        validateStrategy(strategy);
        return timedStrategyMapper.updateById(strategy) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
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
        
        for (Light light : lights) {
            light.setStatus(1);
            light.setBrightness(strategy.getBrightness());
            lightMapper.updateById(light);
        }
        
        log.info("策略「{}」已立即执行，调整了 {} 盏路灯", strategy.getName(), lights.size());
    }

    private void turnOffLightsForStrategy(TimedStrategy strategy) {
        List<Light> lights = getLightsForStrategy(strategy);
        if (lights.isEmpty()) {
            return;
        }
        
        int count = 0;
        for (Light light : lights) {
            if (light.getStatus() != 0) {
                light.setStatus(0);
                light.setBrightness(0);
                lightMapper.updateById(light);
                count++;
            }
        }
        
        if (count > 0) {
            log.info("策略「{}」已停用，关闭了 {} 盏路灯", strategy.getName(), count);
        }
    }

    private boolean isStrategyActive(LocalDateTime now, TimedStrategy strategy) {
        if (!strategy.getEnabled()) {
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
        
        if (StringUtils.hasText(strategy.getDistrict())) {
            wrapper.eq(Light::getDistrict, strategy.getDistrict());
        }
        if (StringUtils.hasText(strategy.getRoad())) {
            wrapper.eq(Light::getRoad, strategy.getRoad());
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

            List<TimedStrategy> existing = timedStrategyMapper.selectList(
                    new LambdaQueryWrapper<TimedStrategy>()
                            .eq(TimedStrategy::getType, "default")
                            .eq(TimedStrategy::getEnabled, true)
            );

            for (TimedStrategy other : existing) {
                if (other.getId() != null && other.getId().equals(strategy.getId())) {
                    continue;
                }

                List<Integer> otherWeekdays = other.getWeekdays();
                if (otherWeekdays == null) {
                    otherWeekdays = List.of();
                }

                Set<Integer> conflict = weekdays.stream()
                        .filter(otherWeekdays::contains)
                        .collect(Collectors.toSet());

                if (!conflict.isEmpty()) {
                    throw new IllegalArgumentException("星期冲突：策略「" + other.getName() + "」已占用星期 " + conflict);
                }
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
    }
}