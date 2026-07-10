package com.smartlight.backend.service;

import com.smartlight.backend.entity.Alert;
import com.smartlight.backend.websocket.AlertWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 告警合并推送服务。
 * <p>
 * 告警不立即逐条推送，而是放入时间窗口缓冲区。
 * 每 {@code alert.push.interval} 秒触发一次合并推送：
 * - WebSocket：推送一条合并消息到所有在线客户端
 * - 邮件：调用 AlertEmailService.sendBatchAlertEmail() 发送汇总邮件
 * <p>
 * 推送周期从 application.properties 的 alert.push.interval 配置读取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertPushService {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final AlertEmailService alertEmailService;

    @Value("${alert.push.interval:5}")
    private int pushIntervalSeconds;

    /** 告警缓冲区（线程安全） */
    private final ConcurrentLinkedQueue<Alert> alertBuffer = new ConcurrentLinkedQueue<>();

    /** 定时调度器 */
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // ==================== 生命周期 ====================

    @PostConstruct
    public void init() {
        startScheduler();
    }

    @PreDestroy
    public void destroy() {
        running = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        // 销毁前推送缓冲区中剩余的告警
        flushBuffer();
    }

    // ==================== 公开方法 ====================

    /**
     * 将告警加入推送缓冲区（由 AlertCheckService 调用）
     */
    public void enqueueAlert(Alert alert) {
        if (alert == null) return;
        alertBuffer.offer(alert);
        log.debug("告警已入推送缓冲区: alertId={}, bufferSize={}", alert.getId(), alertBuffer.size());
    }

    // ==================== 内部方法 ====================

    private void startScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "alert-push-scheduler");
            t.setDaemon(true);
            return t;
        });
        running = true;
        scheduler.scheduleWithFixedDelay(this::flushBuffer,
                pushIntervalSeconds, pushIntervalSeconds, TimeUnit.SECONDS);
        log.info("告警合并推送调度器已启动: 周期={} 秒", pushIntervalSeconds);
    }

    /**
     * 合并推送缓冲区中的所有告警
     */
    private void flushBuffer() {
        if (alertBuffer.isEmpty()) return;

        // 取出缓冲区中的所有告警
        List<Alert> alerts = new ArrayList<>();
        Alert alert;
        while ((alert = alertBuffer.poll()) != null) {
            alerts.add(alert);
        }

        if (alerts.isEmpty()) return;

        int total = alerts.size();

        // 按 alertType + alertLevel 分组统计
        Map<String, List<Alert>> grouped = alerts.stream()
                .collect(Collectors.groupingBy(a ->
                        "T" + (a.getAlertType() != null ? a.getAlertType() : 0)
                                + "-L" + (a.getAlertLevel() != null ? a.getAlertLevel() : 0)));

        // 构建合并摘要
        List<Map<String, Object>> summaryList = new ArrayList<>();
        for (Map.Entry<String, List<Alert>> entry : grouped.entrySet()) {
            List<Alert> group = entry.getValue();
            Alert first = group.get(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("alertType", first.getAlertType());
            item.put("alertLevel", first.getAlertLevel());
            item.put("count", group.size());
            item.put("lightIds", group.stream()
                    .map(Alert::getLightId)
                    .distinct()
                    .collect(Collectors.toList()));
            item.put("sampleMessage", first.getMessage());
            summaryList.add(item);
        }

        log.info("告警合并推送: 共 {} 条告警, {} 个分组, 周期={}秒",
                total, summaryList.size(), pushIntervalSeconds);

        // 1. WebSocket 推送合并消息
        alertWebSocketHandler.pushMergedAlert(total, summaryList);

        // 2. 邮件推送合并消息（已有 sendBatchAlertEmail 方法）
        try {
            alertEmailService.sendBatchAlertEmail(alerts);
        } catch (Exception e) {
            log.error("合并告警邮件发送失败: count={}, error={}", total, e.getMessage());
        }
    }
}