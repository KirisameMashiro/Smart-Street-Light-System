package com.smartlight.backend.controller;

import com.smartlight.backend.brightness.BrightnessCalculator;
import com.smartlight.backend.brightness.BrightnessResult;
import com.smartlight.backend.brightness.SolarPosition;
import com.smartlight.backend.brightness.WeatherApiClient;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.service.LightService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/ai/predict")
public class PredictController {

    private static final double DEFAULT_LAT = 31.23;
    private static final double DEFAULT_LON = 121.47;

    private final LightService lightService;
    private final WeatherApiClient weatherApiClient;

    /** 预测模式启用的路灯ID集合 */
    private final Set<Long> predictionActiveLights = new HashSet<>();

    public PredictController(LightService lightService, WeatherApiClient weatherApiClient) {
        this.lightService = lightService;
        this.weatherApiClient = weatherApiClient;
    }

    /**
     * 获取未来24小时逐时推荐亮度（太阳能模型 + 天气修正 + 安全边界校验）
     */
    @GetMapping("/result")
    public Result<List<Map<String, Object>>> getResult(@RequestParam Long lightId) {
        Light light = lightService.getById(lightId);
        if (light == null) {
            return Result.error("路灯不存在");
        }

        String roadLevel = classifyRoadLevel(light.getRoad());
        int eMin = BrightnessCalculator.getEminByRoadLevel(roadLevel);
        double lat = resolveLat(light);
        double lon = resolveLon(light);
        WeatherApiClient.WeatherData weather = weatherApiClient.fetchWeatherCached(lat, lon);
        LocalDateTime now = LocalDateTime.now();

        List<Map<String, Object>> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            LocalDateTime targetTime = now.plusHours(h);
            int cloudCover = weather != null ? weather.cloudCover() : 50;
            String rainLevel = weather != null ? weather.rainLevel() : "none";
            String snowLevel = weather != null ? weather.snowLevel() : "none";
            int visibility = weather != null ? weather.visibility() : 10000;

            BrightnessResult br = BrightnessCalculator.calculate(
                    eMin, cloudCover, rainLevel, snowLevel, visibility,
                    targetTime, lat, lon);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("hour", targetTime.getHour());
            item.put("brightness", br.getRecommendedBrightness());
            item.put("confidence", calcConfidence(weather, targetTime, lat, lon));
            hours.add(item);
        }
        return Result.success(hours);
    }

    /**
     * 启用预测模式 — 立即计算当前亮度并设置路灯
     */
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Long lightId = ((Number) body.get("lightId")).longValue();
        Light light = lightService.getById(lightId);
        if (light == null) {
            return Result.error("路灯不存在");
        }

        predictionActiveLights.add(lightId);

        String roadLevel = classifyRoadLevel(light.getRoad());
        int eMin = BrightnessCalculator.getEminByRoadLevel(roadLevel);
        double lat = resolveLat(light);
        double lon = resolveLon(light);
        WeatherApiClient.WeatherData weather = weatherApiClient.fetchWeatherCached(lat, lon);
        LocalDateTime now = LocalDateTime.now();

        int cloudCover = weather != null ? weather.cloudCover() : 50;
        String rainLevel = weather != null ? weather.rainLevel() : "none";
        String snowLevel = weather != null ? weather.snowLevel() : "none";
        int visibility = weather != null ? weather.visibility() : 10000;

        BrightnessResult br = BrightnessCalculator.calculate(
                eMin, cloudCover, rainLevel, snowLevel, visibility,
                now, lat, lon);

        lightService.setBrightness(lightId, br.getRecommendedBrightness());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lightId", lightId);
        result.put("active", true);
        result.put("appliedBrightness", br.getRecommendedBrightness());
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
     * 效果对比：太阳能模型预测 vs 固定阈值模式
     */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam Long lightId) {
        Light light = lightService.getById(lightId);
        if (light == null) {
            return Result.error("路灯不存在");
        }

        double ratedPower = light.getRatedPower() != null ? light.getRatedPower() : 150.0;
        String roadLevel = classifyRoadLevel(light.getRoad());
        int eMin = BrightnessCalculator.getEminByRoadLevel(roadLevel);
        double lat = resolveLat(light);
        double lon = resolveLon(light);
        WeatherApiClient.WeatherData weather = weatherApiClient.fetchWeatherCached(lat, lon);
        LocalDateTime now = LocalDateTime.now();

        double predictEnergy = 0;
        double fixedEnergy = 0;
        double totalConfidence = 0;
        for (int h = 0; h < 24; h++) {
            LocalDateTime targetTime = now.plusHours(h);
            int cloudCover = weather != null ? weather.cloudCover() : 50;
            String rainLevel = weather != null ? weather.rainLevel() : "none";
            String snowLevel = weather != null ? weather.snowLevel() : "none";
            int visibility = weather != null ? weather.visibility() : 10000;

            BrightnessResult br = BrightnessCalculator.calculate(
                    eMin, cloudCover, rainLevel, snowLevel, visibility,
                    targetTime, lat, lon);

            predictEnergy += ratedPower * (br.getRecommendedBrightness() / 100.0) / 1000.0;
            fixedEnergy += ratedPower * 0.8 / 1000.0;
            totalConfidence += calcConfidence(weather, targetTime, lat, lon);
        }

        double savedEnergy = Math.round((fixedEnergy - predictEnergy) * 100.0) / 100.0;
        int confidence = (int) Math.round(totalConfidence / 24.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lightId", lightId);
        result.put("predictEnergy", Math.round(predictEnergy * 100.0) / 100.0);
        result.put("fixedEnergy", Math.round(fixedEnergy * 100.0) / 100.0);
        result.put("savedEnergy", Math.max(0, savedEnergy));
        result.put("accuracy", confidence);
        return Result.success(result);
    }

    /** 从路灯实体获取纬度，未设置则回退到默认（上海） */
    private double resolveLat(Light light) {
        return light.getLatitude() != null ? light.getLatitude() : DEFAULT_LAT;
    }

    /** 从路灯实体获取经度，未设置则回退到默认（上海） */
    private double resolveLon(Light light) {
        return light.getLongitude() != null ? light.getLongitude() : DEFAULT_LON;
    }

    /**
     * 根据路段名称推断道路等级
     */
    private String classifyRoadLevel(String road) {
        if (road == null) return "次干道";
        if (road.contains("人民") || road.contains("南京")) return "主干道";
        if (road.contains("滨江")) return "园区道路";
        return "次干道";
    }

    /**
     * 计算置信度 — 基础分 + 天气不确定性扣分 + 晨昏过渡扣分
     * <p>
     * 基础分：真实 API=90，模拟=75，无数据=60
     * 天气扣分：云量 0~-3，降雨 0~-5，雾/低能见度 0~-3
     * 晨昏扣分：太阳高度角 0°~6° 扣2分，-6°~0° 扣1分
     * 下限 60
     */
    private int calcConfidence(WeatherApiClient.WeatherData weather,
                               LocalDateTime targetTime, double lat, double lon) {
        if (weather == null) return 60;

        boolean isMock = weather.description() != null && weather.description().contains("模拟");
        int baseScore = isMock ? 75 : 90;
        int weatherPenalty = 0;

        if (!isMock) {
            // 云量惩罚：云越多，辐照度修正的不确定性越大
            weatherPenalty += Math.round(weather.cloudCover() / 100.0f * 3);

            // 降雨惩罚
            String rain = weather.rainLevel();
            if ("light".equals(rain)) weatherPenalty += 1;
            else if ("moderate".equals(rain)) weatherPenalty += 2;
            else if ("heavy".equals(rain)) weatherPenalty += 3;
            else if ("storm".equals(rain)) weatherPenalty += 5;

            // 雾/低能见度惩罚
            if (weather.visibility() < 1000) weatherPenalty += 3;
            else if (weather.visibility() < 5000) weatherPenalty += 2;
            else if (weather.visibility() < 10000) weatherPenalty += 1;
        }

        // 晨昏过渡惩罚：太阳低角度时大气折射更难建模
        double elevation = SolarPosition.calculateElevation(lat, lon, targetTime);
        int twilightPenalty = 0;
        if (elevation >= 0 && elevation < 6) twilightPenalty = 2;
        else if (elevation >= -6 && elevation < 0) twilightPenalty = 1;

        return Math.max(baseScore - weatherPenalty - twilightPenalty, 60);
    }
}
