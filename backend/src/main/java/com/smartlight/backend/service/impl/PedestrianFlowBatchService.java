package com.smartlight.backend.service.impl;

import com.smartlight.backend.entity.PedestrianFlow;
import com.smartlight.backend.entity.PedestrianFlowHourlyVO;
import com.smartlight.backend.mapper.PedestrianFlowMapper;
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
 * 人流量数据小时聚合写入服务
 * <p>
 * 每 5 秒将缓冲区中的人流量数据做小时级聚合，然后批量 UPSERT 到
 * pedestrian_flow_hourly 表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PedestrianFlowBatchService {

    private final PedestrianFlowMapper pedestrianFlowMapper;

    /** 小时聚合缓冲区：key = "lightId@hourStart" */
    private final Map<String, PedestrianFlowHourlyVO> hourlyBuffer = new HashMap<>();

    private static final String HOURLY_KEY_SEPARATOR = "@";

    /**
     * 将一条人流量数据加入小时聚合缓冲区
     */
    public void enqueue(PedestrianFlow data) {
        if (data == null || data.getCollectTime() == null) return;

        LocalDateTime hourStart = data.getCollectTime()
                .withMinute(0).withSecond(0).withNano(0);
        String key = data.getLightId() + HOURLY_KEY_SEPARATOR + hourStart;

        PedestrianFlowHourlyVO agg = hourlyBuffer.computeIfAbsent(key,
                k -> new PedestrianFlowHourlyVO(data.getLightId(), hourStart));
        agg.accumulate(data);
    }

    public int getBufferSize() {
        return hourlyBuffer.size();
    }

    /**
     * 定时批量 UPSERT 小时聚合数据
     * 每 5 秒执行一次
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void flushToDatabase() {
        if (hourlyBuffer.isEmpty()) return;

        long start = System.currentTimeMillis();

        // 把当前缓冲区数据快照出来处理，防止并发修改
        List<PedestrianFlowHourlyVO> hourlyList = new ArrayList<>(hourlyBuffer.values());
        try {
            pedestrianFlowMapper.upsertHourlyBatch(hourlyList);
            hourlyBuffer.clear();

            long elapsed = System.currentTimeMillis() - start;
            log.info("人流量小时聚合 flush 完成: {} 条, 耗时 {}ms", hourlyList.size(), elapsed);
        } catch (Exception e) {
            log.error("人流量小时聚合写入失败: {}, 缓冲区保留待下次重试", e.getMessage());
            // 不 clear，下次 flush 会重试
        }
    }

    @PreDestroy
    public void destroy() {
        if (hourlyBuffer.isEmpty()) return;

        List<PedestrianFlowHourlyVO> remaining = new ArrayList<>(hourlyBuffer.values());
        log.info("关闭前刷入剩余 {} 条人流小时聚合数据", remaining.size());
        try {
            pedestrianFlowMapper.upsertHourlyBatch(remaining);
            hourlyBuffer.clear();
        } catch (Exception e) {
            log.error("关闭前刷入失败: {}", e.getMessage());
        }
    }
}