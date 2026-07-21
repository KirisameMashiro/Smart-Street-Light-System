package com.smartlight.backend.service.impl;

import com.smartlight.backend.entity.PedestrianFlow;
import com.smartlight.backend.entity.PedestrianFlowHourlyVO;
import com.smartlight.backend.mapper.PedestrianFlowMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 人流量数据小时聚合写入服务（基于 Redis 队列）
 * <p>
 * 每 5 秒从 Redis 队列 flow:raw 批量拉取原始数据，
 * 按 (lightId, hourStart) 分组聚合后 UPSERT 到 pedestrian_flow_hourly 表。
 * <p>
 * 不再依赖 MySQL pedestrian_flow 表，降低数据库写入压力。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PedestrianFlowBatchService {

    private final PedestrianFlowMapper pedestrianFlowMapper;
    private final Optional<StringRedisTemplate> stringRedisTemplate;

    /** Redis Key：人流量原始数据队列 */
    private static final String KEY_FLOW_RAW_QUEUE = "flow:raw";
    /** 每次批量拉取的最大条数 */
    private static final int BATCH_SIZE = 500;

    @PreDestroy
    public void destroy() {
        flushFromRedis();
    }

    /**
     * 定时从 Redis 队列读取原始数据并聚合写入小时表
     * 每 5 秒执行一次
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void flushFromRedis() {
        if (stringRedisTemplate.isEmpty()) return;

        long start = System.currentTimeMillis();

        // 1. 从 Redis 队列批量拉取原始数据（右弹出）
        List<String> rawRecords = new ArrayList<>();
        for (int i = 0; i < BATCH_SIZE; i++) {
            String val = stringRedisTemplate.get().opsForList().rightPop(KEY_FLOW_RAW_QUEUE);
            if (val == null) break;
            rawRecords.add(val);
        }

        if (rawRecords.isEmpty()) return;

        // 2. 解析并按 (lightId, hourStart) 分组聚合
        Map<String, PedestrianFlowHourlyVO> aggregated = new HashMap<>();

        for (String record : rawRecords) {
            try {
                // 格式: "lightId,flowCount,collectTime"
                String[] parts = record.split(",", 3);
                if (parts.length < 3) continue;

                Long lightId = Long.parseLong(parts[0]);
                Integer flowCount = Integer.parseInt(parts[1]);
                LocalDateTime collectTime = LocalDateTime.parse(parts[2]);

                LocalDateTime hourStart = collectTime
                        .withMinute(0).withSecond(0).withNano(0);
                String key = lightId + "@" + hourStart;

                PedestrianFlowHourlyVO agg = aggregated.computeIfAbsent(key,
                        k -> new PedestrianFlowHourlyVO(lightId, hourStart));
                agg.accumulate(new PedestrianFlow(lightId, flowCount, collectTime));
            } catch (Exception e) {
                log.warn("解析 Redis 人流量原始数据失败: record={}, error={}", record, e.getMessage());
            }
        }

        // 3. 批量 UPSERT 到小时聚合表
        if (!aggregated.isEmpty()) {
            List<PedestrianFlowHourlyVO> hourlyList = new ArrayList<>(aggregated.values());
            try {
                pedestrianFlowMapper.upsertHourlyBatch(hourlyList);
                long elapsed = System.currentTimeMillis() - start;
                log.info("人流量小时聚合 flush 完成: 消费 {} 条原始数据, 聚合 {} 条, 耗时 {}ms",
                        rawRecords.size(), hourlyList.size(), elapsed);
            } catch (Exception e) {
                log.error("人流量小时聚合写入失败: {}, 数据已丢失 {} 条", e.getMessage(), rawRecords.size());
                // 从队列弹出的数据无法回退，这种情况下丢失部分数据是可接受的
            }
        }
    }
}