package com.smartlight.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlight.backend.dto.SensorDataDTO;
import com.smartlight.backend.service.SensorDataIngestService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 连接配置
 * 连接 EMQX Broker，订阅传感器数据 topic，将消息转发到 SensorDataIngestService
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    private final SensorDataIngestService sensorDataIngestService;
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

    @PostConstruct
    public void init() {
        if (!mqttEnabled) {
            log.info("MQTT 未启用 (mqtt.enabled=false)，跳过连接");
            return;
        }

        try {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT 连接断开: {}", cause.getMessage());
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // 发布完成回调，本项目仅订阅，无需处理
                }
            });

            mqttClient.connect(options);

            // 订阅传感器数据 topic（通配符 + 匹配所有路灯ID）
            String sensorTopic = topicPrefix + "sensor/+";
            mqttClient.subscribe(sensorTopic, qos);

            log.info("MQTT 连接成功: broker={}, topic={}", brokerUrl, sensorTopic);
        } catch (MqttException e) {
            log.error("MQTT 连接失败: broker={}, error={}", brokerUrl, e.getMessage());
            // 连接失败不阻塞应用启动
            mqttClient = null;
        }
    }

    /**
     * 处理收到的 MQTT 消息
     */
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