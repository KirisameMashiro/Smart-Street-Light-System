package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.SystemConfig;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.mapper.SystemConfigMapper;
import com.smartlight.backend.service.CarbonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 碳减排核算服务实现
 * <p>
 * 计算逻辑：
 * - 节电量 = 基准功率(W) × 路灯数量 × 日均照明时长(h) × 天数 / 1000 − 实际总能耗(kWh)
 * - CO₂减排量 = 节电量(kWh) × 碳排放因子(kg CO₂/kWh)
 * - 节能率 = 节电量 / 基准能耗 × 100%
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonServiceImpl implements CarbonService {

    private final SensorDataMapper sensorDataMapper;
    private final LightMapper lightMapper;
    private final SystemConfigMapper systemConfigMapper;

    @Override
    public Map<String, Object> getSummary() {
        // 读取基准配置
        double baselinePower = getConfigDouble("energy_baseline_power", 250);    // W
        double baselineHours = getConfigDouble("energy_baseline_hours", 12);     // h/day
        double co2Factor = getConfigDouble("co2_factor", 0.997);                // kg CO₂/kWh

        // 统计路灯数量及路段
        List<Light> lights = lightMapper.selectList(null);
        long lightCount = lights.stream().filter(l -> l.getStatus() != null && l.getStatus() != 0).count();
        if (lightCount == 0) lightCount = lights.size();

        // 获取实际总能耗
        Map<String, Object> energyResult = sensorDataMapper.selectTotalEnergy();
        double actualEnergy = energyResult != null && energyResult.get("totalEnergy") != null
                ? ((Number) energyResult.get("totalEnergy")).doubleValue()
                : 0.0;

        // 获取数据时间跨度（天数）
        Map<String, Object> spanResult = sensorDataMapper.selectDataSpanDays();
        int days = spanResult != null && spanResult.get("days") != null
                ? ((Number) spanResult.get("days")).intValue() : 1;

        // 计算基准能耗 = 基准功率(W) × 路灯数 × 日均时长(h) × 天数 / 1000
        double baselineEnergy = baselinePower * lightCount * baselineHours * days / 1000;

        // 节电量
        double savedEnergy = Math.max(0, baselineEnergy - actualEnergy);
        // CO₂减排量
        double reducedCO2 = savedEnergy * co2Factor;
        // 节能率
        double savingRate = baselineEnergy > 0 ? (savedEnergy / baselineEnergy) * 100 : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalSavedPower", Math.round(savedEnergy * 10) / 10.0);
        result.put("totalReducedCO2", Math.round(reducedCO2 * 10) / 10.0);
        result.put("energySavingRate", Math.round(savingRate * 10) / 10.0);
        result.put("actualEnergy", Math.round(actualEnergy * 10) / 10.0);
        result.put("baselineEnergy", Math.round(baselineEnergy * 10) / 10.0);
        result.put("lightCount", lightCount);

        log.info("碳减排汇总: 基准能耗={}kWh, 实际能耗={}kWh, 节电={}kWh, 减排={}kg, 节能率={}%",
                baselineEnergy, actualEnergy, savedEnergy, reducedCO2, savingRate);

        return result;
    }

    @Override
    public List<Map<String, Object>> getTrend(String period) {
        List<Map<String, Object>> monthlyData = sensorDataMapper.selectMonthlyEnergy();

        if (monthlyData == null || monthlyData.isEmpty()) {
            // 无数据时返回空列表
            return new ArrayList<>();
        }

        // 读取基准配置
        double baselinePower = getConfigDouble("energy_baseline_power", 250);
        double baselineHours = getConfigDouble("energy_baseline_hours", 12);
        double co2Factor = getConfigDouble("co2_factor", 0.997);

        // 统计正常的路灯数量
        List<Light> lights = lightMapper.selectList(null);
        long activeCount = lights.stream().filter(l -> l.getStatus() != null && l.getStatus() != 0).count();
        if (activeCount == 0) activeCount = lights.size();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : monthlyData) {
            String month = (String) row.get("month");
            double actualEnergy = ((Number) row.get("totalEnergy")).doubleValue();

            // 当月基准能耗（按30天算）
            double monthlyBaseline = baselinePower * activeCount * baselineHours * 30 / 1000;
            double savedEnergy = Math.max(0, monthlyBaseline - actualEnergy);
            double reducedCO2 = savedEnergy * co2Factor;
            double savingRate = monthlyBaseline > 0 ? (savedEnergy / monthlyBaseline) * 100 : 0;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month);
            item.put("savedEnergy", Math.round(savedEnergy * 10) / 10.0);
            item.put("reducedCO2", Math.round(reducedCO2 * 10) / 10.0);
            item.put("actualEnergy", Math.round(actualEnergy * 10) / 10.0);
            item.put("savingRate", Math.round(savingRate * 10) / 10.0);
            result.add(item);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getRoadCompare() {
        // 按路段统计实际能耗
        List<Map<String, Object>> roadEnergy = sensorDataMapper.selectEnergyByRoad();
        // 统计路段路灯数
        List<Map<String, Object>> roadCounts = sensorDataMapper.selectLightCountByRoad();

        // 转Map便于查找
        Map<String, Long> countMap = new HashMap<>();
        if (roadCounts != null) {
            for (Map<String, Object> rc : roadCounts) {
                countMap.put((String) rc.get("road"), ((Number) rc.get("lightCount")).longValue());
            }
        }

        // 读取基准配置
        double baselinePower = getConfigDouble("energy_baseline_power", 250);
        double baselineHours = getConfigDouble("energy_baseline_hours", 12);

        List<Map<String, Object>> result = new ArrayList<>();
        if (roadEnergy != null) {
            for (Map<String, Object> re : roadEnergy) {
                String road = (String) re.get("road");
                double actualEnergy = ((Number) re.get("totalEnergy")).doubleValue();
                long count = countMap.getOrDefault(road, 0L);

                // 基准能耗
                double baselineEnergy = baselinePower * count * baselineHours * 30 / 1000;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("road", road);
                item.put("before", Math.round(baselineEnergy * 10) / 10.0);
                item.put("after", Math.round(actualEnergy * 10) / 10.0);
                item.put("lightCount", count);
                result.add(item);
            }
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getBaseline() {
        // 查询所有配置键
        String[] keys = {"energy_baseline_power", "energy_baseline_hours", "co2_factor"};
        List<Map<String, Object>> list = new ArrayList<>();

        for (String key : keys) {
            SystemConfig config = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("configKey", key);
            item.put("configValue", config != null ? config.getConfigValue() : "");
            item.put("description", config != null ? config.getDescription() : "");
            list.add(item);
        }

        return list;
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

    /**
     * 从数据库读取配置值，不存在则返回默认值
     */
    private double getConfigDouble(String key, double defaultValue) {
        SystemConfig config = systemConfigMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (config == null || config.getConfigValue() == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(config.getConfigValue());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}