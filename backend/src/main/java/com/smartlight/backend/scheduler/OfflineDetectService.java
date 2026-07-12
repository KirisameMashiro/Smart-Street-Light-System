package com.smartlight.backend.scheduler;

import com.smartlight.backend.entity.Alert;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.mapper.AlertMapper;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.service.AlertPushService;
import com.smartlight.backend.service.MqttPublishService;
import com.smartlight.backend.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 失联故障检测服务
 * <p>
 * 定时扫描所有路灯，检查 Redis 中最新传感器数据的收集时间。
 * 如果超过配置的 {@code offline_fault_timeout} 秒未收到数据，将该路灯标记为故障(2)并生成告警。
 * 当失联路灯重新上报数据后，自动恢复为在线状态(1)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineDetectService {

    private final LightMapper lightMapper;
    private final AlertMapper alertMapper;
    private final AlertPushService alertPushService;
    private final MqttPublishService mqttPublishService;
    private final SystemConfigService systemConfigService;
    private final Optional<StringRedisTemplate> stringRedisTemplate;

    /** Redis Key 前缀：每盏路灯最新传感器数据 */
    private static final String KEY_SENSOR_LATEST_PREFIX = "sensor:latest:";

    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void checkOfflineLights() {
        String timeoutStr = systemConfigService.getConfigValue("offline_fault_timeout");
        long timeoutSeconds = timeoutStr != null ? Long.parseLong(timeoutStr) : 300;
        if (timeoutSeconds <= 0) return;

        List<Light> allLights = lightMapper.selectList(null);
        int faultCount = 0;
        int restoreCount = 0;

        for (Light light : allLights) {
            if (Boolean.TRUE.equals(light.getManualControl())) {
                continue;
            }

            String collectTimeStr = getCollectTimeFromRedis(light.getId());

            if (collectTimeStr == null) {
                // 无任何缓存数据 → 标记为故障
                if (light.getStatus() != 2) {
                    markAsFault(light, timeoutSeconds);
                    faultCount++;
                }
            } else {
                try {
                    LocalDateTime collectTime = LocalDateTime.parse(collectTimeStr);
                    long elapsed = ChronoUnit.SECONDS.between(collectTime, LocalDateTime.now());

                    if (elapsed > timeoutSeconds) {
                        if (light.getStatus() != 2) {
                            markAsFault(light, timeoutSeconds);
                            faultCount++;
                        }
                    } else if (light.getStatus() == 2) {
                        restoreFromFault(light);
                        restoreCount++;
                    }
                } catch (Exception e) {
                    log.warn("解析 collectTime 失败: lightId={}, value={}", light.getId(), collectTimeStr, e);
                }
            }
        }

        if (faultCount > 0 || restoreCount > 0) {
            log.info("失联检测完成: 标记故障 {} 盏, 恢复在线 {} 盏", faultCount, restoreCount);
        }
    }

    /**
     * 标记路灯为故障状态
     */
    private void markAsFault(Light light, long timeoutSeconds) {
        light.setStatus(2);
        light.setBrightness(0);
        lightMapper.updateById(light);

        mqttPublishService.publishCombinedControl(light.getLightCode(), 0, 0);

        // 生成告警
        Alert alert = new Alert();
        alert.setLightId(light.getId());
        alert.setAlertType(5); // 通讯故障
        alert.setAlertLevel(4); // 紧急
        alert.setMessage(String.format(
                "路灯 %s 已失联超过 %d 秒，已自动标记为故障",
                light.getLightName() != null ? light.getLightName() : light.getLightCode(),
                timeoutSeconds));
        alert.setStatus(0);
        alertMapper.insert(alert);

        alertPushService.enqueueAlert(alert);

        log.warn("失联故障: lightId={}, code={}, 已失联超过 {} 秒", light.getId(), light.getLightCode(), timeoutSeconds);
    }

    /**
     * 将路灯从故障恢复为在线状态
     */
    private void restoreFromFault(Light light) {
        light.setStatus(1);
        // 不设亮度，由阈值联动在下一周期决定实际亮度
        lightMapper.updateById(light);

        log.info("失联恢复: lightId={}, code={} 已重新上线", light.getId(), light.getLightCode());
    }

    /**
     * 从 Redis 读取某盏灯最新传感器数据的 collectTime
     */
    private String getCollectTimeFromRedis(Long lightId) {
        if (stringRedisTemplate.isEmpty()) {
            return null;
        }
        String key = KEY_SENSOR_LATEST_PREFIX + lightId;
        Object val = stringRedisTemplate.get().opsForHash().get(key, "collectTime");
        if (val == null) return null;
        String str = val.toString();
        return str.isEmpty() ? null : str;
    }
}