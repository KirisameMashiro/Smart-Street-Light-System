package com.smartlight.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlight.backend.entity.Alert;
import com.smartlight.backend.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警 WebSocket 推送处理器
 * 管理在线客户端连接，广播新告警消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AlertService alertService;

    /** 在线客户端 session 集合 */
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("WebSocket 客户端连接: id={}, remote={}", session.getId(), session.getRemoteAddress());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("WebSocket 客户端断开: id={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        log.error("WebSocket 传输错误: id={}, error={}", session.getId(), exception.getMessage());
    }

    /**
     * 推送单条告警到所有在线客户端
     */
    public void pushAlert(Alert alert) {
        if (sessions.isEmpty()) return;
        try {
            long unhandledCount = alertService.countUnhandled();
            Map<String, Object> payload = Map.of(
                    "type", "new_alert",
                    "data", Map.of(
                            "id", alert.getId(),
                            "lightId", alert.getLightId(),
                            "alertType", alert.getAlertType(),
                            "alertLevel", alert.getAlertLevel(),
                            "message", alert.getMessage(),
                            "status", alert.getStatus(),
                            "createTime", alert.getCreateTime() != null ? alert.getCreateTime().toString() : null,
                            "unhandledCount", unhandledCount
                    )
            );
            broadcastMessage(objectMapper.writeValueAsString(payload));
            log.info("WebSocket 单条告警推送完成: alertId={}", alert.getId());
        } catch (Exception e) {
            log.error("WebSocket 单条告警推送失败: alertId={}, error={}", alert.getId(), e.getMessage(), e);
        }
    }

    /**
     * 推送合并告警消息（时间窗口内多条告警合并为一条摘要）
     * @param total   告警总数
     * @param groups  按类型+级别分组的摘要列表 [{alertType, alertLevel, count, lightIds, sampleMessage}]
     */
    public void pushMergedAlert(int total, List<Map<String, Object>> groups) {
        if (sessions.isEmpty() || groups == null || groups.isEmpty()) return;
        try {
            long unhandledCount = alertService.countUnhandled();
            Map<String, Object> payload = Map.of(
                    "type", "merged_alert",
                    "data", Map.of(
                            "total", total,
                            "unhandledCount", unhandledCount,
                            "groups", groups
                    )
            );
            broadcastMessage(objectMapper.writeValueAsString(payload));
            log.info("WebSocket 合并告警推送完成: total={}, groups={}", total, groups.size());
        } catch (Exception e) {
            log.error("WebSocket 合并告警推送失败: total={}, error={}", total, e.getMessage(), e);
        }
    }

    /** 广播 JSON 字符串到所有在线客户端 */
    private void broadcastMessage(String json) {
        TextMessage textMessage = new TextMessage(json);
        int sent = 0;
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                    sent++;
                } catch (IOException e) {
                    log.error("WebSocket 消息发送失败: sessionId={}, error={}", session.getId(), e.getMessage());
                    sessions.remove(session.getId());
                }
            }
        }
        if (sent > 0) {
            log.debug("WebSocket 广播完成: 已发送 {}/{} 个客户端", sent, sessions.size());
        }
    }
}