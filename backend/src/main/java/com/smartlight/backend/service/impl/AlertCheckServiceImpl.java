package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.*;
import com.smartlight.backend.mapper.AlertMapper;
import com.smartlight.backend.mapper.AlertRuleMapper;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.service.AlertCheckService;
import com.smartlight.backend.service.AlertEmailService;
import com.smartlight.backend.service.AlertPushService;
import com.smartlight.backend.service.AlertRuleEvaluator;
import com.smartlight.backend.service.AlertRuleEvaluator.EvaluateResult;
import com.smartlight.backend.websocket.AlertWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警自动检测服务实现
 * <p>
 * 两个检测入口：
 * 1. checkAndGenerateAlert() — 实时检测（MQTT/HTTP 消息到达后立即对单个路灯检测）
 * 2. scheduledCheck() — 定时扫描（@Scheduled 定时遍历所有路灯）
 * <p>
 * 告警去重：同一路灯 + 同一告警类型，在 dedupWindowMinutes 内已有未处理告警则跳过
 * <p>
 * 推送逻辑已委托给 AlertPushService：不立即推送，放入缓冲区按周期合并推送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertCheckServiceImpl implements AlertCheckService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertMapper alertMapper;
    private final SensorDataMapper sensorDataMapper;
    private final LightMapper lightMapper;
    private final AlertRuleEvaluator alertRuleEvaluator;
    private final AlertPushService alertPushService;

    @Value("${alert.dedup.minutes:30}")
    private int dedupWindowMinutes;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndGenerateAlert(SensorData sensorData) {
        if (sensorData == null || sensorData.getLightId() == null) {
            return;
        }

        Light light = lightMapper.selectById(sensorData.getLightId());
        if (light == null) {
            log.warn("实时检测: lightId={} 对应的路灯不存在，跳过", sensorData.getLightId());
            return;
        }

        List<AlertRule> enabledRules = alertRuleMapper.selectList(
                new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getEnabled, true));

        for (AlertRule rule : enabledRules) {
            EvaluateResult result = alertRuleEvaluator.evaluate(rule, sensorData, light);
            if (result != null && result.triggered) {
                if (isDuplicate(sensorData.getLightId(), result.alertType)) {
                    log.debug("告警去重: lightId={}, alertType={}, 窗口内已有未处理告警，跳过",
                            sensorData.getLightId(), result.alertType);
                    continue;
                }
                saveAndEnqueue(sensorData.getLightId(), result, rule);
            }
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${alert.check.interval:60000}",
               initialDelayString = "${alert.check.initial-delay:30000}")
    @Transactional(rollbackFor = Exception.class)
    public void scheduledCheck() {
        log.debug("定时告警检查开始...");

        List<Light> lights = lightMapper.selectList(null);
        List<AlertRule> enabledRules = alertRuleMapper.selectList(
                new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getEnabled, true));

        if (enabledRules.isEmpty()) {
            log.debug("没有启用的告警规则，跳过检查");
            return;
        }

        int alertCount = 0;
        for (Light light : lights) {
            SensorData latest = sensorDataMapper.selectLatestByLightId(light.getId());
            if (latest == null) continue;

            for (AlertRule rule : enabledRules) {
                EvaluateResult result = alertRuleEvaluator.evaluate(rule, latest, light);
                if (result != null && result.triggered) {
                    if (isDuplicate(light.getId(), result.alertType)) {
                        log.debug("告警去重: lightId={}, alertType={}, 跳过", light.getId(), result.alertType);
                        continue;
                    }
                    Alert alert = saveAlert(light.getId(), result, rule);
                    if (alert != null) {
                        alertPushService.enqueueAlert(alert);
                        alertCount++;
                    }
                }
            }
        }

        if (alertCount > 0) {
            log.info("定时告警检查完成: 生成 {} 条新告警（已入推送缓冲区）", alertCount);
        }
    }

    /**
     * 告警去重检查：同一路灯 + 同一告警类型在 dedupWindowMinutes 分钟内已有未处理告警
     */
    private boolean isDuplicate(Long lightId, Integer alertType) {
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(dedupWindowMinutes);
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getLightId, lightId)
                .eq(Alert::getAlertType, alertType)
                .eq(Alert::getStatus, 0)
                .ge(Alert::getCreateTime, windowStart);
        return alertMapper.selectCount(wrapper) > 0;
    }

    /**
     * 保存告警并返回实体
     */
    private Alert saveAlert(Long lightId, EvaluateResult result, AlertRule rule) {
        Alert alert = new Alert();
        alert.setLightId(lightId);
        alert.setAlertType(result.alertType);
        alert.setAlertLevel(result.alertLevel);
        alert.setMessage(result.message);
        alert.setStatus(0);

        alertMapper.insert(alert);
        log.info("告警已生成: id={}, lightId={}, type={}, level={}, message={}",
                alert.getId(), lightId, result.alertType, result.alertLevel, result.message);
        return alert;
    }

    /**
     * 保存告警并放入合并推送缓冲区（代替之前的直接推送+邮件）
     */
    private void saveAndEnqueue(Long lightId, EvaluateResult result, AlertRule rule) {
        Alert alert = saveAlert(lightId, result, rule);
        alertPushService.enqueueAlert(alert);
    }
}