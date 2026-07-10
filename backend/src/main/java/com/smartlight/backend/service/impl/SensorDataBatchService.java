package com.smartlight.backend.service.impl;

import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.service.AlertCheckService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 传感器数据批量写入服务
 * <p>
 * 传感器数据先进入内存缓冲区，然后由定时任务每 5 秒批量刷入 MySQL。
 * 大幅减少 MySQL 事务提交频率（从每秒数百次降至每 5 秒一次）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorDataBatchService {

    private final SensorDataMapper sensorDataMapper;
    private final AlertCheckService alertCheckService;

    /** 线程安全缓冲区 */
    private final BlockingQueue<SensorData> buffer = new LinkedBlockingQueue<>();

    /** 缓冲区告警阈值 */
    private static final int WARN_THRESHOLD = 2000;

    /** 将数据加入写入缓冲区 */
    public void enqueue(SensorData data) {
        if (data == null) return;
        buffer.offer(data);

        int size = buffer.size();
        if (size > WARN_THRESHOLD && size % 500 == 0) {
            log.warn("传感器数据缓冲区积压: {} 条，请检查消费速度", size);
        }
    }

    /** 获取当前缓冲区大小 */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * 定时批量刷入 MySQL
     * 每 5 秒执行一次，每次最多取 500 条
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void flushToDatabase() {
        List<SensorData> batch = new ArrayList<>();
        buffer.drainTo(batch, 500);

        if (batch.isEmpty()) return;

        long start = System.currentTimeMillis();

        // 批量 INSERT
        sensorDataMapper.insertBatch(batch);

        long elapsed = System.currentTimeMillis() - start;
        log.info("批量写入传感器数据: {} 条, 耗时 {}ms", batch.size(), elapsed);

        // 批量触发异步告警检测
        for (SensorData data : batch) {
            try {
                alertCheckService.checkAndGenerateAlert(data);
            } catch (Exception e) {
                log.error("批量告警检测失败: lightId={}, error={}", data.getLightId(), e.getMessage());
            }
        }
    }

    @PreDestroy
    public void destroy() {
        // 应用关闭前，将缓冲区剩余数据全部刷入数据库
        List<SensorData> remaining = new ArrayList<>();
        buffer.drainTo(remaining);
        if (!remaining.isEmpty()) {
            log.info("服务关闭前刷入剩余 {} 条传感器数据", remaining.size());
            try {
                sensorDataMapper.insertBatch(remaining);
            } catch (Exception e) {
                log.error("关闭前刷入失败: {}", e.getMessage());
            }
        }
    }
}