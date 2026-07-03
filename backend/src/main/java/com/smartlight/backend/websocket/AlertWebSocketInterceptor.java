package com.smartlight.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 记录连接日志，可在此处做来源校验
 */
@Slf4j
@Component
public class AlertWebSocketInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        log.info("WebSocket 握手请求: uri={}, remote={}", request.getURI(), request.getRemoteAddress());
        // 允许所有连接
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket 握手失败: uri={}, error={}", request.getURI(), exception.getMessage());
        } else {
            log.info("WebSocket 握手成功: uri={}", request.getURI());
        }
    }
}