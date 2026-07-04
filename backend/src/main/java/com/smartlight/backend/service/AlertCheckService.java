package com.smartlight.backend.service;

import com.smartlight.backend.entity.SensorData;

/**
 * 告警自动检测服务
 */
public interface AlertCheckService {

    /**
     * 对单个路灯最新传感器数据执行即时规则检查（MQTT/HTTP消息到达时调用）
     * @param sensorData 最新传感器数据
     */
    void checkAndGenerateAlert(SensorData sensorData);

    /**
     * 定时扫描：遍历所有路灯，对每个路灯最新数据执行规则检查（@Scheduled 调用）
     */
    void scheduledCheck();
}