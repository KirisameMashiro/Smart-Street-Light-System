package com.smartlight.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.config.AIConfig;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.service.LightService;
import com.smartlight.backend.service.SensorDataService;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/ai/predict")
public class PredictController {

    private final LightService lightService;
    private final SensorDataService sensorDataService;
    private final AIConfig aiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 预测模式启用的路灯ID集合 */
    private final Set<Long> predictionActiveLights = ConcurrentHashMap.newKeySet();

    public PredictController(LightService lightService, SensorDataService sensorDataService,
                             AIConfig aiConfig) {
        this.lightService = lightService;
        this.sensorDataService = sensorDataService;
        this.aiConfig = aiConfig;
    }

    /**
     * 获取未来24小时逐时推荐亮度（DeepSeek AI + 规则兜底）
     */
    @GetMapping("/result")
    public Result<List<Map<String, Object>>> getResult(@RequestParam Long lightId) {
        Light light = lightService.getById(lightId);
        if (light == null) {
            return Result.error("路灯不存在");
        }

        SensorData latest = sensorDataService.getLatestByLightId(lightId);
        LocalDateTime now = LocalDateTime.now();

        // 尝试调用 DeepSeek AI 生成预测
        AIPredictResult aiResult = callAIPredictor(light, latest, now);
        if (aiResult != null) {
            return Result.success(aiResult.toResponse());
        }

        // AI 失败 → 降级为规则预测
        List<Map<String, Object>> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            int targetHour = (now.getHour() + h) % 24;
            int brightness = calculatePredictedBrightness(targetHour, latest, light);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", targetHour);
            item.put("brightness", brightness);
            item.put("confidence", 70);
            hours.add(item);
        }
        return Result.success(hours);
    }

    /**
     * 启用预测模式（即时响应，用规则计算当前亮度）
     */
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Long lightId = ((Number) body.get("lightId")).longValue();
        Light light = lightService.getById(lightId);
        if (light == null) {
            return Result.error("路灯不存在");
        }

        predictionActiveLights.add(lightId);

        SensorData latest = sensorDataService.getLatestByLightId(lightId);
        int brightness = calculatePredictedBrightness(LocalDateTime.now().getHour(), latest, light);
        lightService.setBrightness(lightId, brightness);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lightId", lightId);
        result.put("active", true);
        result.put("appliedBrightness", brightness);
        return Result.success("预测模式已启用", result);
    }

    /**
     * 停用预测模式
     */
    @PostMapping("/stop")
    public Result<Map<String, Object>> stop(@RequestBody Map<String, Object> body) {
        Long lightId = ((Number) body.get("lightId")).longValue();
        predictionActiveLights.remove(lightId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lightId", lightId);
        result.put("active", false);
        return Result.success("预测模式已停用", result);
    }

    /**
     * 效果对比：预测模式 vs 固定阈值模式
     */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long lightId) {
        Light light = lightService.getById(lightId);
        if (light == null) {
            return Result.error("路灯不存在");
        }

        double ratedPower = light.getRatedPower() != null ? light.getRatedPower() : 150.0;
        SensorData latest = sensorDataService.getLatestByLightId(lightId);
        double predictEnergy = 0;
        double fixedEnergy = 0;
        boolean aiUsed = false;

        // 尝试用 AI 预测计算能耗
        AIPredictResult aiResult = callAIPredictor(light, latest, LocalDateTime.now());
        if (aiResult != null) {
            aiUsed = true;
            for (AIPredictResult.HourBrightness hb : aiResult.predictions) {
                predictEnergy += ratedPower * (hb.brightness / 100.0) / 1000.0;
                fixedEnergy += ratedPower * 0.8 / 1000.0;
            }
        } else {
            // 降级为规则
            for (int h = 0; h < 24; h++) {
                int predBrightness = calculatePredictedBrightness(h, latest, light);
                predictEnergy += ratedPower * (predBrightness / 100.0) / 1000.0;
                fixedEnergy += ratedPower * 0.8 / 1000.0;
            }
        }

        double savedEnergy = Math.round((fixedEnergy - predictEnergy) * 100.0) / 100.0;
        int accuracy = aiUsed ? 90 : 70;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lightId", lightId);
        result.put("predictEnergy", Math.round(predictEnergy * 100.0) / 100.0);
        result.put("fixedEnergy", Math.round(fixedEnergy * 100.0) / 100.0);
        result.put("savedEnergy", Math.max(0, savedEnergy));
        result.put("accuracy", accuracy);
        return Result.success(result);
    }

    // ==================== AI 预测 ====================

    /** 调用 DeepSeek API 生成 24 小时亮度预测，失败返回 null */
    private AIPredictResult callAIPredictor(Light light, SensorData latest, LocalDateTime now) {
        try {
            String prompt = buildPredictPrompt(light, latest, now);

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", aiConfig.getModel());
            reqBody.put("temperature", 0.2);
            reqBody.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一个路灯亮度预测系统。只返回JSON，不要加markdown代码块或任何额外文字。"),
                    Map.of("role", "user", "content", prompt)
            ));

            String json = objectMapper.writeValueAsString(reqBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiConfig.getBaseUrl() + "/chat/completions"))
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.get("choices").get(0).get("message").get("content").asText();

            return parseAIResponse(content, now.getHour());
        } catch (Exception e) {
            return null;
        }
    }

    /** 构建发给 DeepSeek 的 Prompt */
    private String buildPredictPrompt(Light light, SensorData latest, LocalDateTime now) {
        StringBuilder sb = new StringBuilder();
        sb.append("根据以下数据和规则，生成未来24小时每小时的推荐路灯亮度(0-100%)。\n\n");

        sb.append("【当前信息】\n");
        sb.append("- 时间：").append(now).append("\n");
        sb.append("- 路灯名称：").append(light.getLightName() != null ? light.getLightName() : "未知").append("\n");
        sb.append("- 额定功率：").append(light.getRatedPower() != null ? light.getRatedPower() : 150).append("W\n");

        if (latest != null && latest.getIlluminance() != null) {
            long minsAgo = Duration.between(latest.getCollectTime(), now).toMinutes();
            sb.append("- 最新照度：").append(latest.getIlluminance()).append(" lux（").append(minsAgo).append("分钟前）\n");
        }
        if (latest != null && latest.getTemperature() != null) {
            sb.append("- 温度：").append(latest.getTemperature()).append("°C\n");
        }
        if (latest != null && latest.getHumidity() != null) {
            sb.append("- 湿度：").append(latest.getHumidity()).append("%RH\n");
        }

        sb.append("\n【调光规则 — 必须严格遵守】\n");
        sb.append("1. 08:00-16:00（白天）：亮度必须为0，不做任何例外\n");
        sb.append("2. 17:00-19:00（傍晚）：亮度从30%逐步升到80%\n");
        sb.append("3. 20:00-05:00（夜间高峰）：亮度85%-95%\n");
        sb.append("4. 06:00-07:00（清晨）：亮度从60%逐步降到20%\n");

        sb.append("\n【输出格式】\n");
        sb.append("返回24个元素，对应小时0-23，从当前整点(").append(now.getHour()).append("时)开始：\n");
        sb.append("{\"predictions\":[{\"hour\":0,\"brightness\":85},{\"hour\":1,\"brightness\":88},...],\"reasoning\":\"简述预测逻辑\"}\n");

        return sb.toString();
    }

    /** 解析 DeepSeek 返回的 JSON，失败返回 null */
    private AIPredictResult parseAIResponse(String content, int nowHour) {
        try {
            // 去除可能的 markdown 代码块包裹
            String json = content.trim();
            if (json.startsWith("```")) {
                json = json.substring(json.indexOf('\n') + 1);
                if (json.endsWith("```")) {
                    json = json.substring(0, json.lastIndexOf("```")).trim();
                }
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.get("predictions");
            if (arr == null || !arr.isArray()) return null;

            AIPredictResult result = new AIPredictResult();
            result.reasoning = root.has("reasoning") ? root.get("reasoning").asText() : "";

            for (JsonNode item : arr) {
                int hour = item.get("hour").asInt();
                int brightness = item.get("brightness").asInt();
                // 验证：白天强制为0
                int realHour = (nowHour + hour) % 24;
                if (realHour >= 8 && realHour <= 16) {
                    brightness = 0;
                }
                brightness = Math.max(0, Math.min(100, brightness));
                result.predictions.add(new AIPredictResult.HourBrightness(hour, brightness));
            }

            return result.predictions.size() == 24 ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 规则兜底 ====================

    /**
     * 规则计算亮度（AI 不可用时的降级方案）
     */
    private int calculatePredictedBrightness(int hour, SensorData latest, Light light) {
        int brightness;
        if (hour >= 20 || hour <= 5) {
            brightness = 85;
        } else if (hour >= 6 && hour <= 7) {
            brightness = 60 - (hour - 6) * 20;
        } else if (hour >= 8 && hour <= 16) {
            brightness = 0;
        } else {
            brightness = 30 + (hour - 17) * 25;
        }

        if (hour >= 20 || hour <= 5) {
            brightness = Math.max(50, Math.min(100, brightness));
        } else {
            brightness = Math.max(0, Math.min(100, brightness));
        }

        return brightness;
    }

    // ==================== AI 预测结果封装 ====================

    private static class AIPredictResult {
        List<HourBrightness> predictions = new ArrayList<>();
        String reasoning = "";

        List<Map<String, Object>> toResponse() {
            List<Map<String, Object>> hours = new ArrayList<>();
            for (HourBrightness hb : predictions) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("hour", hb.hour);
                item.put("brightness", hb.brightness);
                item.put("confidence", 90);
                hours.add(item);
            }
            // 把推理依据附在第一个元素上
            if (!hours.isEmpty() && !reasoning.isEmpty()) {
                hours.get(0).put("reasoning", reasoning);
            }
            return hours;
        }

        static class HourBrightness {
            int hour;
            int brightness;
            HourBrightness(int hour, int brightness) {
                this.hour = hour;
                this.brightness = brightness;
            }
        }
    }
}
