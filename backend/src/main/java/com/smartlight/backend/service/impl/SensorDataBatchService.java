package com.smartlight.backend.service.impl;

import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.entity.SensorDataHourlyVO;
import com.smartlight.backend.mapper.SensorDataMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 传感器数据小时聚合写入服务
 * <p>
 * 每 5 秒将缓冲区中的传感器数据在内存中做小时级聚合，然后批量 UPSERT 到
 * {@code sensor_data_hourly} 表。不再写入原始 {@code sensor_data} 表，
 * MySQL 写入量从每天 2800 万行降至每天 2.4 万行。
 * <p>
 * 重启时最多丢失 5 秒缓冲数据（约 500 条采样），
 * 不影响碳排放计算（只看完整小时）和实时监控（数据已写入 Redis）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataBatchService {

    private final SensorDataMapper sensorDataMapper;

    /** 小时聚合缓冲区：key = "lightId@hourStart" */
    private final Map<String, SensorDataHourlyVO> hourlyBuffer = new HashMap<>();

    /** 聚合 key 分隔符 */
    private static final String HOURLY_KEY_SEPARATOR = "@";

    /**
     * 将一条传感器数据加入小时聚合缓冲区
     * <p>
     * 数据在 ingest() 阶段已写入 Redis，此处仅做内存聚合。
     */
    public void enqueue(SensorData data) {
        if (data == null || data.getCollectTime() == null) return;

        LocalDateTime hourStart = data.getCollectTime()
                .withMinute(0).withSecond(0).withNano(0);
        String key = data.getLightId() + HOURLY_KEY_SEPARATOR + hourStart;

        SensorDataHourlyVO agg = hourlyBuffer.computeIfAbsent(key,
                k -> new SensorDataHourlyVO(data.getLightId(), hourStart));
        agg.accumulate(data);
    }

    /** 获取当前聚合缓冲区中待 flush 的条目数 */
    public int getBufferSize() {
        return hourlyBuffer.size();
    }

    /**
     * 定时批量 UPSERT 小时聚合数据
     * 每 5 秒执行一次，将内存中累积的小时聚合数据写入 MySQL
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void flushToDatabase() {
        if (hourlyBuffer.isEmpty()) return;

        long start = System.currentTimeMillis();

        List<SensorDataHourlyVO> hourlyList = new ArrayList<>(hourlyBuffer.values());
        try {
            sensorDataMapper.upsertHourlyBatch(hourlyList);
            hourlyBuffer.clear();

            long elapsed = System.currentTimeMillis() - start;
            log.debug("小时聚合 flush 完成: {} 条, 耗时 {}ms", hourlyList.size(), elapsed);
        } catch (Exception e) {
            log.error("小时聚合写入失败: {}, 缓冲区保留待下次重试", e.getMessage());
            // 不 clear，下次 flush 会重试
        }
    }

    @PreDestroy
    public void destroy() {
        if (hourlyBuffer.isEmpty()) return;

        List<SensorDataHourlyVO> remaining = new ArrayList<>(hourlyBuffer.values());
        log.info("服务关闭前刷入剩余 {} 条小时聚合数据", remaining.size());
        try {
            sensorDataMapper.upsertHourlyBatch(remaining);
            hourlyBuffer.clear();
        } catch (Exception e) {
            log.error("关闭前刷入失败: {}", e.getMessage());
        }
    }
}