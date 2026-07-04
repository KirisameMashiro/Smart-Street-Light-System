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
     * 推送告警到所有在线客户端
     * @param alert 新生成的告警
     */
    public void pushAlert(Alert alert) {
        if (sessions.isEmpty()) {
            return;
        }

        try {
            // 未处理告警数量
            long unhandledCount = alertService.countUnhandled();

            // 构建推送消息
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

            String json = objectMapper.writeValueAsString(payload);
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
            log.info("WebSocket 告警推送完成: alertId={}, 已发送 {}/{} 个客户端", alert.getId(), sent, sessions.size());
        } catch (Exception e) {
            log.error("WebSocket 告警推送构建失败: alertId={}, error={}", alert.getId(), e.getMessage(), e);
        }
    }
}