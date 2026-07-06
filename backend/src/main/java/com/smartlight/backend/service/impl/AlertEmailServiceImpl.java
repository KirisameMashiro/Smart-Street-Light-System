package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.Alert;
import com.smartlight.backend.entity.User;
import com.smartlight.backend.mapper.UserMapper;
import com.smartlight.backend.service.AlertEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 告警邮件发送服务实现
 * 通过 luckycola.com.cn 的邮件 API 发送告警通知
 * 收件人从 system_config 中读取，或从所有 admin 用户的邮箱获取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEmailServiceImpl implements AlertEmailService {

    private final RestTemplate restTemplate;
    private final UserMapper userMapper;

    private static final String API_URL = "https://luckycola.com.cn/tools/customMail";

    @Value("${alert.email.cola-key:}")
    private String colaKey;

    @Value("${alert.email.smtp-email:}")
    private String smtpEmail;

    @Value("${alert.email.smtp-code:}")
    private String smtpCode;

    @Value("${alert.email.smtp-code-type:qq}")
    private String smtpCodeType;

    @Override
    public void sendAlertEmail(Alert alert) {
        List<String> toEmails = getTargetEmails();
        if (toEmails == null) return;

        String levelLabel = getLevelLabel(alert.getAlertLevel());
        String subject = "[Alert] " + levelLabel + " - Light #" + alert.getLightId();

        // 单条告警 HTML
        String htmlContent = buildSingleAlertHtml(levelLabel, alert);

        for (String toEmail : toEmails) {
            try {
                sendSingleEmail(toEmail, subject, htmlContent);
                log.info("Alert email sent: alertId={}, to={}", alert.getId(), toEmail);
            } catch (Exception e) {
                log.error("发送告警邮件失败: to={}, alertId={}, error={}",
                        toEmail, alert.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void sendBatchAlertEmail(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) return;

        List<String> toEmails = getTargetEmails();
        if (toEmails == null) return;

        // 统计各级别数量
        long urgentCount = alerts.stream().filter(a -> a.getAlertLevel() != null && a.getAlertLevel() >= 4).count();
        long seriousCount = alerts.stream().filter(a -> a.getAlertLevel() != null && a.getAlertLevel() == 3).count();
        long generalCount = alerts.stream().filter(a -> a.getAlertLevel() != null && a.getAlertLevel() == 2).count();

        String subject = "[Batch Alert] " + alerts.size() + " new alerts (Urgent: " + urgentCount + ")";
        String htmlContent = buildBatchAlertHtml(alerts, urgentCount, seriousCount, generalCount);

        for (String toEmail : toEmails) {
            try {
                sendSingleEmail(toEmail, subject, htmlContent);
                log.info("Batch alert email sent: count={}, to={}", alerts.size(), toEmail);
            } catch (Exception e) {
                log.error("发送批量告警邮件失败: to={}, count={}, error={}",
                        toEmail, alerts.size(), e.getMessage());
            }
        }
    }

    // ===================== 公共方法 =====================

    /**
     * 获取收件人邮箱列表（admin + operator 角色中有邮箱的用户）
     */
    private List<String> getTargetEmails() {
        if (colaKey.isEmpty() || smtpEmail.isEmpty() || smtpCode.isEmpty()) {
            log.warn("邮件服务配置不完整，跳过发送");
            return null;
        }
        List<User> usersWithEmail = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .isNotNull(User::getEmail)
                        .ne(User::getEmail, "")
                        .in(User::getRole, "admin", "operator"));
        if (usersWithEmail.isEmpty()) {
            log.warn("没有找到配置了邮箱的管理员或运维人员，跳过邮件发送");
            return null;
        }
        return usersWithEmail.stream()
                .map(User::getEmail)
                .collect(Collectors.toList());
    }

    private String getLevelLabel(Integer alertLevel) {
        if (alertLevel == null) return "Unknown";
        return switch (alertLevel) {
            case 4 -> "Urgent";
            case 3 -> "Serious";
            case 2 -> "General";
            case 1 -> "Info";
            default -> "Unknown";
        };
    }

    /**
     * 构建单条告警 HTML 内容
     */
    private String buildSingleAlertHtml(String levelLabel, Alert alert) {
        return String.format(
                "<div style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2 style='color: #e6a23c;'>Smart Street Light Alert</h2>" +
                "<table style='border-collapse: collapse; width: 100%%; max-width: 600px;'>" +
                "<tr><td style='padding: 8px; border: 1px solid #ddd; font-weight: bold;'>Level</td>" +
                "<td style='padding: 8px; border: 1px solid #ddd;'>%s</td></tr>" +
                "<tr><td style='padding: 8px; border: 1px solid #ddd; font-weight: bold;'>Light ID</td>" +
                "<td style='padding: 8px; border: 1px solid #ddd;'>%d</td></tr>" +
                "<tr><td style='padding: 8px; border: 1px solid #ddd; font-weight: bold;'>Message</td>" +
                "<td style='padding: 8px; border: 1px solid #ddd;'>%s</td></tr>" +
                "<tr><td style='padding: 8px; border: 1px solid #ddd; font-weight: bold;'>Time</td>" +
                "<td style='padding: 8px; border: 1px solid #ddd;'>%s</td></tr>" +
                "</table>" +
                "<p style='color: #999; font-size: 12px; margin-top: 20px;'>" +
                "This email is auto-generated by Smart Street Light System.</p></div>",
                levelLabel, alert.getLightId(),
                alert.getMessage(),
                alert.getCreateTime() != null ? alert.getCreateTime().toString() : "-");
    }

    /**
     * 构建批量告警 HTML 内容（所有告警在一个表格中）
     */
    private String buildBatchAlertHtml(List<Alert> alerts, long urgent, long serious, long general) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='font-family: Arial, sans-serif; padding: 20px;'>");
        sb.append("<h2 style='color: #e6a23c;'>Smart Street Light Alert Summary</h2>");

        // 统计摘要
        sb.append("<p>Total: <b>").append(alerts.size()).append("</b> alerts | ");
        sb.append("Urgent: <b style='color:red'>").append(urgent).append("</b> | ");
        sb.append("Serious: <b style='color:orange'>").append(serious).append("</b> | ");
        sb.append("General: <b>").append(general).append("</b></p>");

        // 告警列表表格
        sb.append("<table style='border-collapse: collapse; width: 100%%; max-width: 800px;'>");
        sb.append("<tr style='background: #f5f5f5;'>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd;'>#</th>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd;'>Level</th>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd;'>Light ID</th>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd;'>Message</th>");
        sb.append("<th style='padding: 8px; border: 1px solid #ddd;'>Time</th></tr>");

        int index = 1;
        for (Alert alert : alerts) {
            String level = getLevelLabel(alert.getAlertLevel());
            String color = switch (alert.getAlertLevel() != null ? alert.getAlertLevel() : 0) {
                case 4 -> "red";
                case 3 -> "orange";
                default -> "inherit";
            };
            sb.append("<tr>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(index++).append("</td>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd; color: ").append(color).append(";'><b>").append(level).append("</b></td>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(alert.getLightId()).append("</td>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(alert.getMessage()).append("</td>");
            sb.append("<td style='padding: 8px; border: 1px solid #ddd;'>").append(
                    alert.getCreateTime() != null ? alert.getCreateTime().toString() : "-").append("</td>");
            sb.append("</tr>");
        }
        sb.append("</table>");
        sb.append("<p style='color: #999; font-size: 12px; margin-top: 20px;'>");
        sb.append("This email is auto-generated by Smart Street Light System.</p></div>");

        return sb.toString();
    }

    /**
     * 发送邮件（通用方法）
     */
    private void sendSingleEmail(String toEmail, String subject, String htmlContent) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("ColaKey", colaKey);
        requestBody.put("tomail", toEmail);
        requestBody.put("fromTitle", "Smart Street Light System");
        requestBody.put("subject", subject);
        requestBody.put("content", htmlContent);
        requestBody.put("isTextContent", false);
        requestBody.put("smtpCode", smtpCode);
        requestBody.put("smtpEmail", smtpEmail);
        requestBody.put("smtpCodeType", smtpCodeType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        String response = restTemplate.postForObject(API_URL, request, String.class);
        if (response != null && !response.isEmpty()) {
            log.debug("Email API response: {}", response);
        }
    }
}