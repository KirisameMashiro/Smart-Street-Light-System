package com.smartlight.backend.service;

import com.smartlight.backend.entity.Alert;

/**
 * 告警邮件发送服务
 */
public interface AlertEmailService {

    /**
     * 发送单个告警邮件（实时检测时使用）
     */
    void sendAlertEmail(Alert alert);

    /**
     * 批量发送聚合告警邮件（定时检测时使用，多条合成一封）
     */
    void sendBatchAlertEmail(java.util.List<Alert> alerts);
}