package com.smartlight.backend.config;

import com.smartlight.backend.websocket.AlertWebSocketHandler;
import com.smartlight.backend.websocket.AlertWebSocketInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * 注册 /ws/alert endpoint，使用原生 WebSocket（非 STOMP）
 * 前端通过 Vite proxy 将 /ws 路径转发到后端
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final AlertWebSocketInterceptor alertWebSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/ws/alert")
                .addInterceptors(alertWebSocketInterceptor)
                .setAllowedOrigins("*");
    }
}