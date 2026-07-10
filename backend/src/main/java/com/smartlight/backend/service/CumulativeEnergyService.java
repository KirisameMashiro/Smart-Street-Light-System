package com.smartlight.backend.service;

import com.smartlight.backend.mapper.SensorDataMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 今日累计耗电内存累加器
 * 入库时实时累加，避免每次都 SUM 数据库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CumulativeEnergyService {

    private final SensorDataMapper sensorDataMapper;

    /** lightId → 今日累计 Wh */
    private final ConcurrentHashMap<Long, Double> todayEnergy = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reloadFromDB();
    }

    /** 从数据库加载今天的累计值（重启恢复） */
    private void reloadFromDB() {
        try {
            var rows = sensorDataMapper.selectTodayEnergySum();
            todayEnergy.clear();
            for (var row : rows) {
                Long lightId = ((Number) row.get("lightId")).longValue();
                Double energy = ((Number) row.get("totalEnergy")).doubleValue();
                todayEnergy.put(lightId, energy);
            }
            log.info("累计耗电初始化完成：已从数据库加载 {} 个路灯的今日数据", todayEnergy.size());
        } catch (Exception e) {
            log.error("累计耗电初始化失败: {}", e.getMessage());
        }
    }

    /** 入库一条数据时累加（由 SensorDataIngestServiceImpl 调用） */
    public void accumulate(Long lightId, double energyWh) {
        if (energyWh <= 0) return;
        todayEnergy.merge(lightId, energyWh, Double::sum);
    }

    /** 获取指定路灯今日累计耗电 (Wh) */
    public double getTodayEnergy(Long lightId) {
        return Math.round(todayEnergy.getOrDefault(lightId, 0.0) * 1000.0) / 1000.0;
    }

    /** 获取所有路灯今日累计耗电 */
    public Map<Long, Double> getAllTodayEnergy() {
        Map<Long, Double> result = new HashMap<>();
        for (var entry : todayEnergy.entrySet()) {
            result.put(entry.getKey(), Math.round(entry.getValue() * 1000.0) / 1000.0);
        }
        return result;
    }

    /** 每天零点重置计数器 */
    @Scheduled(cron = "0 0 0 * * *")
    public void dailyReset() {
        log.info("累计耗电计数器每日重置");
        todayEnergy.clear();
    }
}
