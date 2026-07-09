package com.smartlight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlight.backend.config.MqttConfig;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.event.MqttReconnectedEvent;
import com.smartlight.backend.mapper.LightMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MQTT 发布服务 —— 向路灯设备发送控制命令。
 *
 * 所有方法内部 try-catch，MQTT 失败不会影响数据库操作。
 * MQTT 未启用或未连接时静默跳过并记录警告。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttPublishService {

    private final MqttConfig mqttConfig;
    private final LightMapper lightMapper;
    private final ObjectMapper objectMapper;

    @Value("${mqtt.enabled:false}")
    private boolean mqttEnabled;

    @Value("${mqtt.qos:1}")
    private int qos;

    // ==================== 公开方法 ====================

    /**
     * 发布开关命令（单灯）
     *
     * @param lightCode 路灯编号
     * @param status    0-关闭, 1-开启
     */
    public void publishSwitchControl(String lightCode, Integer status) {
        if (!canPublish(lightCode)) return;
        try {
            Map<String, Object> payload = buildPayload("switch", status, null, lightCode);
            doPublish(lightCode, payload);
            log.info("MQTT发布开关命令: lightCode={}, status={}", lightCode, status);
        } catch (Exception e) {
            log.error("MQTT发布开关命令失败: lightCode={}, status={}", lightCode, status, e);
        }
    }

    /**
     * 发布调光命令（单灯）
     *
     * @param lightCode  路灯编号
     * @param brightness 亮度 0-100
     */
    public void publishBrightnessControl(String lightCode, Integer brightness) {
        if (!canPublish(lightCode)) return;
        try {
            Map<String, Object> payload = buildPayload("brightness", null, brightness, lightCode);
            doPublish(lightCode, payload);
            log.info("MQTT发布调光命令: lightCode={}, brightness={}", lightCode, brightness);
        } catch (Exception e) {
            log.error("MQTT发布调光命令失败: lightCode={}, brightness={}", lightCode, brightness, e);
        }
    }

    /**
     * 发布组合命令（开关 + 调光，用于定时策略/阈值联动）
     *
     * @param lightCode  路灯编号
     * @param status     0-关闭, 1-开启
     * @param brightness 亮度 0-100
     */
    public void publishCombinedControl(String lightCode, Integer status, Integer brightness) {
        if (!canPublish(lightCode)) return;
        try {
            Map<String, Object> payload = buildPayload("set", status, brightness, lightCode);
            doPublish(lightCode, payload);
            log.info("MQTT发布组合命令: lightCode={}, status={}, brightness={}", lightCode, status, brightness);
        } catch (Exception e) {
            log.error("MQTT发布组合命令失败: lightCode={}, status={}, brightness={}", lightCode, status, brightness, e);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 检查是否可以发布：MQTT已启用、已连接、lightCode非空
     */
    private boolean canPublish(String lightCode) {
        if (!mqttEnabled) return false;
        if (lightCode == null || lightCode.isEmpty()) {
            log.warn("lightCode 为空，跳过 MQTT 发布");
            return false;
        }
        if (!mqttConfig.isMqttConnected()) {
            log.warn("MQTT 未连接，跳过发布到 lightCode={}", lightCode);
            return false;
        }
        return true;
    }

    /**
     * 构建统一的 JSON 消息体
     */
    private Map<String, Object> buildPayload(String command, Integer status, Integer brightness, String lightCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("command", command);
        payload.put("lightCode", lightCode);
        if (status != null) payload.put("status", status);
        if (brightness != null) payload.put("brightness", brightness);
        return payload;
    }

    /**
     * 执行 MQTT 发布
     */
    private void doPublish(String lightCode, Map<String, Object> payload) throws Exception {
        String topic = mqttConfig.getTopicPrefix() + "control/" + lightCode;
        byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        MqttMessage message = new MqttMessage(bytes);
        message.setQos(qos);
        message.setRetained(false);
        mqttConfig.getMqttClient().publish(topic, message);
    }

    // ==================== 状态同步 ====================

    /**
     * 检查 MQTT 发布服务当前是否可用
     */
    public boolean isAvailable() {
        return mqttEnabled && mqttConfig.isMqttConnected();
    }

    /**
     * 同步所有路灯的当前状态到物理设备。
     * 在 MQTT 重连后调用，确保数据库状态与设备一致。
     */
    public void syncAllLightStates() {
        if (!mqttEnabled) return;
        if (!mqttConfig.isMqttConnected()) {
            log.warn("MQTT 未连接，跳过全量状态同步");
            return;
        }

        try {
            List<Light> lights = lightMapper.selectList(null);
            int successCount = 0;
            int skipCount = 0;

            for (Light light : lights) {
                if (light.getLightCode() == null || light.getLightCode().isEmpty()) {
                    skipCount++;
                    continue;
                }
                try {
                    Map<String, Object> payload = buildPayload("set", light.getStatus(), light.getBrightness(), light.getLightCode());
                    doPublish(light.getLightCode(), payload);
                    successCount++;
                } catch (Exception e) {
                    log.error("同步失败: lightCode={}, error={}", light.getLightCode(), e.getMessage());
                }
            }

            log.info("MQTT 全量状态同步完成: 成功={}, 跳过(无编号)={}, 总计={}",
                    successCount, skipCount, lights.size());
        } catch (Exception e) {
            log.error("MQTT 全量状态同步失败: {}", e.getMessage(), e);
        }
    }

    // ==================== 事件监听 ====================

    /**
     * 监听 MQTT 重连事件，触发全量状态同步。
     * 通过事件机制避免了 MqttConfig ← → MqttPublishService 的循环依赖。
     */
    @EventListener
    public void onMqttReconnected(MqttReconnectedEvent event) {
        log.info("收到 MQTT 重连事件，开始同步路灯状态到设备...");
        syncAllLightStates();
    }

    // ==================== 定期同步（兜底方案） ====================

    /**
     * 每5分钟执行一次全量状态，确保数据库与物理设备最终一致。
     * 作为方案C兜底：即使重连同步因某些原因未触发，
     * 定期同步也能在5分钟内自动修复不一致。
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void periodicSync() {
        if (!mqttEnabled || !mqttConfig.isMqttConnected()) {
            return; // MQTT 不可用时跳过
        }
        log.debug("开始定期状态同步（每5分钟）...");
        syncAllLightStates();
    }
}