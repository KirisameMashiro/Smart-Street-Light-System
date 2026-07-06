package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.CarbonStats;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.SystemConfig;
import com.smartlight.backend.mapper.CarbonStatsMapper;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.mapper.SystemConfigMapper;
import com.smartlight.backend.service.CarbonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonServiceImpl implements CarbonService {

    /**
     * 启动时自动补全所有历史日期的碳减排统计
     */
    @PostConstruct
    public void initHistoryStats() {
        log.info("碳减排统计初始化：开始补全历史数据...");
        try {
            List<Map<String, Object>> dates = sensorDataMapper.selectDistinctDates();
            if (dates == null || dates.isEmpty()) {
                log.info("碳减排统计初始化：无传感器数据，跳过");
                return;
            }
            int total = 0;
            for (Map<String, Object> row : dates) {
                Object dateObj = row.get("statDate");
                if (dateObj == null) continue;
                String dateStr;
                if (dateObj instanceof java.sql.Date) {
                    dateStr = ((java.sql.Date) dateObj).toLocalDate().toString();
                } else {
                    dateStr = dateObj.toString();
                }
                total += computeDailyStats(dateStr);
            }
            log.info("碳减排统计初始化完成：共补全 {} 天的数据，写入 {} 条记录", dates.size(), total);
        } catch (Exception e) {
            log.error("碳减排统计初始化失败", e);
        }
    }

    /**
     * 每天凌晨 2:30 自动计算昨天的碳减排统计
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void autoComputeDailyStats() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        log.info("自动碳减排日统计：开始计算 {}", yesterday);
        try {
            int count = computeDailyStats(yesterday);
            log.info("自动碳减排日统计完成：{} 共写入 {} 条记录", yesterday, count);
        } catch (Exception e) {
            log.error("自动碳减排日统计失败：{}", yesterday, e);
        }
    }

    private final CarbonStatsMapper carbonStatsMapper;
    private final SensorDataMapper sensorDataMapper;
    private final LightMapper lightMapper;
    private final SystemConfigMapper systemConfigMapper;

    @Override
    public Map<String, Object> getSummary() {
        Map<String, Object> stats = carbonStatsMapper.selectSummary();
        if (stats == null) stats = new HashMap<>();

        double saved = ((Number) stats.getOrDefault("totalSaved", 0.0)).doubleValue();
        double co2 = ((Number) stats.getOrDefault("totalCO2", 0.0)).doubleValue();
        double rate = ((Number) stats.getOrDefault("avgRate", 0.0)).doubleValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("savedEnergy", Math.round(saved * 10.0) / 10.0);
        result.put("reducedCo2", Math.round(co2 * 10.0) / 10.0);
        result.put("energySavingRate", Math.round(rate * 10.0) / 10.0);
        return result;
    }

    @Override
    public List<Map<String, Object>> getTrend(String type, String period) {
        List<Map<String, Object>> result = new ArrayList<>();

        if ("month".equals(type) && period != null && !period.isEmpty()) {
            // 月度每日趋势
            String[] parts = period.split("-");
            if (parts.length == 2) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                LocalDate startDate = LocalDate.of(year, month, 1);
                LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

                List<Map<String, Object>> dailyStats = carbonStatsMapper.selectDailyStats(startDate, endDate);
                if (dailyStats != null) {
                    for (Map<String, Object> row : dailyStats) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        Object dateObj = row.get("statDate");
                        String dateStr;
                        if (dateObj instanceof java.sql.Date) {
                            dateStr = ((java.sql.Date) dateObj).toLocalDate().toString();
                        } else {
                            dateStr = dateObj != null ? dateObj.toString() : "";
                        }
                        item.put("period", dateStr);
                        item.put("savedEnergy", ((Number) row.getOrDefault("savedEnergy", 0)).doubleValue());
                        item.put("reducedCo2", ((Number) row.getOrDefault("co2Reduction", 0)).doubleValue());
                        result.add(item);
                    }
                }
            }
        } else if ("year".equals(type) && period != null && !period.isEmpty()) {
            // 年度每月趋势
            int year = Integer.parseInt(period);
            List<Map<String, Object>> monthlyStats = carbonStatsMapper.selectYearlyMonthlyStats(year);
            if (monthlyStats != null) {
                for (Map<String, Object> row : monthlyStats) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("month", row.get("month"));
                    item.put("savedEnergy", ((Number) row.getOrDefault("savedEnergy", 0)).doubleValue());
                    item.put("reducedCo2", ((Number) row.getOrDefault("co2Reduction", 0)).doubleValue());
                    result.add(item);
                }
            }
        } else {
            // 兼容旧参数: 返回全部月度汇总
            List<Map<String, Object>> monthlyStats = carbonStatsMapper.selectYearlyMonthlyStats(LocalDate.now().getYear());
            if (monthlyStats != null) {
                for (Map<String, Object> row : monthlyStats) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("month", row.get("month"));
                    item.put("savedEnergy", ((Number) row.getOrDefault("savedEnergy", 0)).doubleValue());
                    item.put("reducedCo2", ((Number) row.getOrDefault("co2Reduction", 0)).doubleValue());
                    result.add(item);
                }
            }
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getRoadCompare() {
        List<Map<String, Object>> roadStats = carbonStatsMapper.selectStatsByRoad();
        if (roadStats == null) return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : roadStats) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("road", row.get("road"));
            item.put("savedEnergy", ((Number) row.getOrDefault("savedEnergy", 0)).doubleValue());
            result.add(item);
        }
        return result;
    }

    @Override
    public Map<String, Object> getBaseline() {
        String[] keys = {"energy_baseline_power", "energy_baseline_hours", "co2_factor"};
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            SystemConfig config = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
            String value = (config != null && config.getConfigValue() != null) ? config.getConfigValue() : "";
            switch (key) {
                case "energy_baseline_power" ->
                        result.put("basePower", value.isEmpty() ? 250 : Double.parseDouble(value));
                case "energy_baseline_hours" ->
                        result.put("dailyHours", value.isEmpty() ? 12 : Double.parseDouble(value));
                case "co2_factor" ->
                        result.put("emissionFactor", value.isEmpty() ? 0.997 : Double.parseDouble(value));
            }
        }
        return result;
    }

    @Override
    public boolean updateBaseline(String key, String value) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (config != null) {
            config.setConfigValue(value);
            systemConfigMapper.updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            systemConfigMapper.insert(config);
        }
        log.info("基准配置已更新: {}={}", key, value);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int computeDailyStats(String dateStr) {
        LocalDate statDate = (dateStr != null && !dateStr.isEmpty())
                ? LocalDate.parse(dateStr)
                : LocalDate.now().minusDays(1);

        List<Map<String, Object>> dailyEnergy = sensorDataMapper.selectDailyEnergyPerLight();
        if (dailyEnergy == null || dailyEnergy.isEmpty()) {
            log.warn("computeDailyStats: {} 无传感器数据，跳过", statDate);
            return 0;
        }

        LocalDate targetDate = statDate;
        List<Map<String, Object>> filtered = dailyEnergy.stream()
                .filter(row -> {
                    Object dateObj = row.get("statDate");
                    if (dateObj == null) return false;
                    if (dateObj instanceof java.sql.Date) {
                        return targetDate.equals(((java.sql.Date) dateObj).toLocalDate());
                    }
                    return targetDate.equals(dateObj.toString());
                })
                .collect(Collectors.toList());
        if (filtered.isEmpty()) {
            log.warn("computeDailyStats: {} 无传感器数据，跳过", statDate);
            return 0;
        }

        double baselinePower = getConfigDouble("energy_baseline_power", 250);
        double baselineHours = getConfigDouble("energy_baseline_hours", 12);
        double co2Factor = getConfigDouble("co2_factor", 0.997);

        List<Light> lights = lightMapper.selectList(null);
        Map<Long, String> lightRoadMap = new HashMap<>();
        Map<String, Long> roadCounter = new HashMap<>();
        for (Light light : lights) {
            String road = light.getRoad();
            lightRoadMap.put(light.getId(), road);
            if (road != null) {
                roadCounter.merge(road, 1L, Long::sum);
            }
        }

        Map<String, Double> roadActualEnergy = new HashMap<>();
        double totalActualEnergy = 0.0;
        for (Map<String, Object> row : filtered) {
            Long lightId = ((Number) row.get("lightId")).longValue();
            double energy = ((Number) row.get("dailyEnergyKwh")).doubleValue();
            String road = lightRoadMap.get(lightId);
            if (road != null) {
                roadActualEnergy.merge(road, energy, Double::sum);
            }
            totalActualEnergy += energy;
        }

        carbonStatsMapper.delete(new LambdaQueryWrapper<CarbonStats>()
                .eq(CarbonStats::getStatDate, statDate));

        int inserted = 0;
        for (Map.Entry<String, Double> entry : roadActualEnergy.entrySet()) {
            String road = entry.getKey();
            double actual = entry.getValue();
            long count = roadCounter.getOrDefault(road, 0L);

            double baseline = baselinePower * count * baselineHours * 1 / 1000;
            double saved = Math.max(0, baseline - actual);
            double co2 = saved * co2Factor;
            double rate = baseline > 0 ? Math.round((saved / baseline) * 100 * 10.0) / 10.0 : 0;

            CarbonStats cs = new CarbonStats();
            cs.setStatDate(statDate);
            cs.setRoad(road);
            cs.setLightCount((int) count);
            cs.setBaselineEnergy(Math.round(baseline * 10.0) / 10.0);
            cs.setActualEnergy(Math.round(actual * 10.0) / 10.0);
            cs.setSavedEnergy(Math.round(saved * 10.0) / 10.0);
            cs.setCo2Reduction(Math.round(co2 * 10.0) / 10.0);
            cs.setSavingRate(rate);
            carbonStatsMapper.insert(cs);
            inserted++;
        }

        long totalLights = lights.size();
        double totalBaseline = baselinePower * totalLights * baselineHours * 1 / 1000;
        double totalSaved = Math.max(0, totalBaseline - totalActualEnergy);
        double totalCO2 = totalSaved * co2Factor;
        double totalRate = totalBaseline > 0
                ? Math.round((totalSaved / totalBaseline) * 100 * 10.0) / 10.0
                : 0;

        CarbonStats summary = new CarbonStats();
        summary.setStatDate(statDate);
        summary.setRoad(null);
        summary.setLightCount((int) totalLights);
        summary.setBaselineEnergy(Math.round(totalBaseline * 10.0) / 10.0);
        summary.setActualEnergy(Math.round(totalActualEnergy * 10.0) / 10.0);
        summary.setSavedEnergy(Math.round(totalSaved * 10.0) / 10.0);
        summary.setCo2Reduction(Math.round(totalCO2 * 10.0) / 10.0);
        summary.setSavingRate(totalRate);
        carbonStatsMapper.insert(summary);
        inserted++;

        log.info("computeDailyStats: {} 已完成，共写入 {} 条记录", statDate, inserted);
        return inserted;
    }

    private double getConfigDouble(String key, double defaultValue) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (config == null || config.getConfigValue() == null) return defaultValue;
        try {
            return Double.parseDouble(config.getConfigValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}