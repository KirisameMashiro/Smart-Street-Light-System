package com.smartlight.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlight.backend.dto.SensorDataDTO;
import com.smartlight.backend.event.MqttReconnectedEvent;
import com.smartlight.backend.service.SensorDataIngestService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    private final SensorDataIngestService sensorDataIngestService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.topic-prefix}")
    private String topicPrefix;

    @Value("${mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${mqtt.qos:1}")
    private int qos;

    private MqttClient mqttClient;
    private final ThreadPoolTaskScheduler taskScheduler;
    private volatile boolean connecting = false;

    private static final int MAX_RETRY_DELAY_SECONDS = 60;

    @PostConstruct
    public void init() {
        if (!mqttEnabled) {
            log.info("MQTT 未启用 (mqtt.enabled=false)，跳过连接");
            return;
        }
        // 异步启动连接，不阻塞应用启动
        taskScheduler.submit(this::connectWithRetry);
    }

    /**
     * 连接 MQTT Broker，失败时自动重试
     */
    private synchronized void connectWithRetry() {
        if (connecting) return;
        connecting = true;

        try {
            if (mqttClient != null) {
                try {
                    mqttClient.disconnect();
                    mqttClient.close();
                } catch (MqttException ignored) {}
            }

            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);

            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    log.info("MQTT 连接{}: serverURI={}", reconnect ? "重连成功" : "成功", serverURI);
                    try {
                        String sensorTopic = topicPrefix + "sensor/+";
                        mqttClient.subscribe(sensorTopic, qos);
                        log.info("MQTT 已订阅主题: {}", sensorTopic);
                    } catch (MqttException e) {
                        log.error("MQTT 订阅失败: {}", e.getMessage());
                    }
                    connecting = false;
                    // 重连成功后，发布事件让监听器同步路灯状态
                    if (reconnect) {
                        log.info("MQTT 重连成功，发布重连事件...");
                        eventPublisher.publishEvent(new MqttReconnectedEvent(this));
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT 连接断开: {}, 将自动重连...", cause.getMessage());
                    // Paho 的 setAutomaticReconnect(true) 会处理重连，
                    // 但如果重连也失败，我们兜底重试
                    scheduleRetry(MAX_RETRY_DELAY_SECONDS);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            mqttClient.connect(options);

            log.info("MQTT 连接中: broker={}", brokerUrl);
            connecting = false;

        } catch (MqttException e) {
            log.warn("MQTT 连接失败: broker={}, error={}, 将在 {} 秒后重试...",
                    brokerUrl, e.getMessage(), MAX_RETRY_DELAY_SECONDS);
            connecting = false;
            scheduleRetry(MAX_RETRY_DELAY_SECONDS);
        }
    }

    private void scheduleRetry(int delaySeconds) {
        taskScheduler.schedule(() -> connectWithRetry(),
                java.time.Instant.now().plusSeconds(delaySeconds));
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("MQTT 收到消息: topic={}, payload={}", topic, payload);

            SensorDataDTO dto = objectMapper.readValue(payload, SensorDataDTO.class);

            if (dto.getLightId() == null) {
                log.warn("MQTT 消息缺少 lightId 字段，丢弃: {}", payload);
                return;
            }

            sensorDataIngestService.ingest(dto);
            log.info("MQTT 传感器数据已入库: lightId={}, topic={}", dto.getLightId(), topic);
        } catch (Exception e) {
            log.error("MQTT 消息处理失败: topic={}, error={}", topic, e.getMessage(), e);
        }
    }

    // ==================== 暴露给 MqttPublishService 的 getter ====================

    /**
     * 获取 MqttClient 实例（用于发布消息）
     */
    public MqttClient getMqttClient() {
        return mqttClient;
    }

    /**
     * 检查 MQTT 是否已连接
     */
    public boolean isMqttConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    /**
     * 获取 Topic 前缀
     */
    public String getTopicPrefix() {
        return topicPrefix;
    }

    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("MQTT 连接已关闭");
            } catch (MqttException e) {
                log.error("MQTT 关闭连接失败: {}", e.getMessage());
            }
        }
    }
}