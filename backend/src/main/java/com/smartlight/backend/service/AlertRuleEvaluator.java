package com.smartlight.backend.service;

import com.smartlight.backend.entity.AlertRule;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 告警规则评估引擎
 * 解析 alert_rule.threshold 字符串表达式，与传感器数据比较，判定是否触发告警
 * <p>
 * 支持的表达式语法：
 * - 简单比较: 电压<50V, 温度>37°C, 电流=0
 * - 复合OR条件: 电压<50V或电流=0
 * - 额定值相关: 功率>额定值*1.2
 * - 时间相关: 关闭时间>48h
 * - 带时间约束: 照度<10000lux(白天)
 */
@Slf4j
@Component
public class AlertRuleEvaluator {

    // 匹配简单条件: 字段名 + 操作符 + 数值 + 可选单位
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "(电压|温度|电流|功率|照度|湿度|亮度|关闭时间)" +
            "([><]=?|=)" +
            "(额定值|\\d+(\\.\\d+)?)" +
            "([*×]\\d+(\\.\\d+)?)?" +
            "(V|°C|A|W|lux|%RH|%|h)?"
    );

    /**
     * 评估规则是否触发
     *
     * @param rule       告警规则
     * @param sensorData 最新传感器数据（可为null）
     * @param light      路灯信息
     * @return 触发时返回评估结果，不触发返回null
     */
    public EvaluateResult evaluate(AlertRule rule, SensorData sensorData, Light light) {
        if (rule == null || rule.getThreshold() == null || rule.getThreshold().isEmpty()) {
            return null;
        }

        String threshold = rule.getThreshold().trim();

        // 检查时间约束
        TimeConstraint timeConstraint = parseTimeConstraint(threshold);
        if (timeConstraint != TimeConstraint.NONE && !isWithinTimeConstraint(timeConstraint)) {
            return null; // 当前时间不满足约束，直接跳过
        }
        // 移除时间约束后缀便于后续解析
        String cleanThreshold = removeTimeConstraint(threshold);

        // 处理 OR 复合条件
        if (cleanThreshold.contains("或")) {
            String[] parts = cleanThreshold.split("或");
            for (String part : parts) {
                EvaluateResult result = evaluateCondition(part.trim(), sensorData, light, rule);
                if (result != null && result.triggered) {
                    return result; // OR: 任一条件触发即可
                }
            }
            return null;
        }

        // 单一条件
        return evaluateCondition(cleanThreshold, sensorData, light, rule);
    }

    /**
     * 评估单个条件
     */
    private EvaluateResult evaluateCondition(String expression, SensorData sensorData, Light light, AlertRule rule) {
        // 特殊处理: 关闭时间>Nh
        if (expression.startsWith("关闭时间")) {
            return evaluateStatusTime(expression, light, rule);
        }

        Matcher matcher = CONDITION_PATTERN.matcher(expression);
        if (!matcher.find()) {
            log.warn("无法解析阈值表达式: {}", expression);
            return null;
        }

        String fieldName = matcher.group(1);       // 电压/温度/电流等
        String operator = matcher.group(2);         // < > = <= >=
        String valueStr = matcher.group(3);         // 50 / 额定值
        String multiplierStr = matcher.group(4);    // *1.2 或 null

        // 计算实际阈值
        double thresholdValue;
        if ("额定值".equals(valueStr)) {
            thresholdValue = light.getRatedPower() != null ? light.getRatedPower().doubleValue() : 0;
            if (multiplierStr != null) {
                String multNum = multiplierStr.replaceAll("[*×]", "");
                thresholdValue *= Double.parseDouble(multNum);
            }
        } else {
            thresholdValue = Double.parseDouble(valueStr);
            if (multiplierStr != null) {
                String multNum = multiplierStr.replaceAll("[*×]", "");
                thresholdValue *= Double.parseDouble(multNum);
            }
        }

        // 获取传感器实际值
        Double actualValue = getFieldValue(fieldName, sensorData);
        if (actualValue == null) {
            return null; // 传感器无此数据，无法判定
        }

        // 执行比较
        boolean triggered = compare(actualValue, operator, thresholdValue);
        if (!triggered) {
            return null;
        }

        // 生成告警消息
        String message = generateMessage(fieldName, operator, thresholdValue, actualValue,
                expression, sensorData, light);

        EvaluateResult result = new EvaluateResult();
        result.triggered = true;
        result.alertType = mapAlertType(rule.getRuleType());
        result.alertLevel = mapAlertLevel(rule.getRuleType());
        result.message = message;

        return result;
    }

    /**
     * 评估"关闭时间>Nh"特殊规则
     */
    private EvaluateResult evaluateStatusTime(String expression, Light light, AlertRule rule) {
        Matcher m = Pattern.compile("关闭时间([><]=?|=)(\\d+(\\.\\d+)?)\\s*h?").matcher(expression);
        if (!m.find() || light == null) {
            return null;
        }

        String operator = m.group(1);
        double hours = Double.parseDouble(m.group(2));

        // 只有关闭状态的才检查
        if (light.getStatus() == null || light.getStatus() != 0) {
            return null;
        }

        // 计算关闭时长
        LocalDateTime updateTime = light.getUpdateTime();
        if (updateTime == null) {
            return null;
        }
        long closedHours = ChronoUnit.HOURS.between(updateTime, LocalDateTime.now());

        boolean triggered = compare((double) closedHours, operator, hours);
        if (!triggered) {
            return null;
        }

        EvaluateResult result = new EvaluateResult();
        result.triggered = true;
        result.alertType = mapAlertType(rule.getRuleType());
        result.alertLevel = mapAlertLevel(rule.getRuleType());
        result.message = String.format("%s: 路灯已连续关闭 %d 小时，超过阈值 %.0f 小时，请确认是否需要开启",
                light.getLightName() != null ? light.getLightName() : light.getLightCode(),
                closedHours, hours);
        return result;
    }

    /**
     * 从 SensorData 中获取字段值
     */
    private Double getFieldValue(String fieldName, SensorData sensorData) {
        if (sensorData == null) return null;
        return switch (fieldName) {
            case "电压" -> sensorData.getVoltage();
            case "温度" -> sensorData.getTemperature();
            case "电流" -> sensorData.getCurrent();
            case "功率" -> sensorData.getPower();
            case "照度" -> sensorData.getIlluminance();
            case "湿度" -> sensorData.getHumidity();
            // "亮度" 暂不支持（sensor_data 表无此字段，Light.brightness 是控制值非采集值）
            default -> null;
        };
    }

    /**
     * 执行数值比较
     */
    private boolean compare(double actual, String operator, double threshold) {
        return switch (operator) {
            case ">" -> actual > threshold;
            case ">=" -> actual >= threshold;
            case "<" -> actual < threshold;
            case "<=" -> actual <= threshold;
            case "=" -> Math.abs(actual - threshold) < 0.001; // 浮点容差
            default -> false;
        };
    }

    /**
     * 解析时间约束 (白天)/(夜间)
     */
    private TimeConstraint parseTimeConstraint(String threshold) {
        if (threshold.contains("(白天)")) return TimeConstraint.DAYTIME;
        if (threshold.contains("(夜间)")) return TimeConstraint.NIGHTTIME;
        return TimeConstraint.NONE;
    }

    private String removeTimeConstraint(String threshold) {
        return threshold.replaceAll("\\((白天|夜间)\\)", "").trim();
    }

    private boolean isWithinTimeConstraint(TimeConstraint constraint) {
        int currentHour = LocalDateTime.now().getHour();
        return switch (constraint) {
            case DAYTIME -> currentHour >= 10 && currentHour <= 16;
            case NIGHTTIME -> currentHour >= 20 || currentHour <= 6;
            case NONE -> true;
        };
    }

    /**
     * 生成告警消息
     */
    private String generateMessage(String fieldName, String operator, double threshold,
                                   double actual, String expression,
                                   SensorData sensorData, Light light) {
        String lightName = light != null && light.getLightName() != null
                ? light.getLightName()
                : (light != null ? light.getLightCode() : "路灯#" + (sensorData != null ? sensorData.getLightId() : "?"));

        String chineseOp = switch (operator) {
            case ">" -> "超过";
            case ">=" -> "达到或超过";
            case "<" -> "低于";
            case "<=" -> "低于或等于";
            case "=" -> "等于";
            default -> operator;
        };

        return String.format("%s%s预警: 实测%s%.2f%s, %s阈值%.0f%s, 请检查设备状态",
                lightName,
                getFieldLabel(fieldName),
                fieldName,
                actual,
                getUnit(fieldName),
                chineseOp,
                threshold,
                getUnit(fieldName));
    }

    private String getFieldLabel(String fieldName) {
        return switch (fieldName) {
            case "电压" -> "异常";
            case "温度" -> "过高";
            case "电流" -> "异常";
            case "功率" -> "过高";
            case "照度" -> "偏低";
            default -> "异常";
        };
    }

    private String getUnit(String fieldName) {
        return switch (fieldName) {
            case "电压" -> "V";
            case "温度" -> "°C";
            case "电流" -> "A";
            case "功率" -> "W";
            case "照度" -> "lux";
            case "湿度" -> "%";
            default -> "";
        };
    }

    /**
     * 规则类型 → 告警类型 映射
     */
    private Integer mapAlertType(String ruleType) {
        return switch (ruleType != null ? ruleType : "") {
            case "light_fault" -> 5;   // 通讯故障/设备故障
            case "env_warning" -> 4;    // 过热
            case "power_overload" -> 1; // 过流
            default -> 6;               // 其他
        };
    }

    /**
     * 规则类型 → 告警级别 映射
     */
    private Integer mapAlertLevel(String ruleType) {
        return switch (ruleType != null ? ruleType : "") {
            case "light_fault" -> 4;     // 紧急
            case "sensor_abnormal" -> 3; // 严重
            case "env_warning" -> 2;     // 一般
            case "power_overload" -> 2;  // 一般
            case "low_brightness" -> 2;  // 一般
            default -> 2;                // 默认一般
        };
    }

    /**
     * 评估结果
     */
    public static class EvaluateResult {
        public boolean triggered;
        public Integer alertType;
        public Integer alertLevel;
        public String message;
    }

    private enum TimeConstraint {
        NONE, DAYTIME, NIGHTTIME
    }
}