package com.smartlight.backend.controller;

import com.smartlight.backend.brightness.BrightnessCalculator;
import com.smartlight.backend.brightness.BrightnessResult;
import com.smartlight.backend.brightness.WeatherApiClient;
import com.smartlight.backend.brightness.WeatherApiClient.WeatherData;
import com.smartlight.backend.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能亮度推荐 API
 */
@RestController
@RequestMapping("/api/brightness")
@RequiredArgsConstructor
public class BrightnessController {

    private final WeatherApiClient weatherApiClient;

    /** 默认经纬度：上海 */
    private static final double DEFAULT_LAT = 31.23;
    private static final double DEFAULT_LON = 121.47;

    /**
     * 获取当前小时的亮度推荐
     * GET /api/brightness/now?roadLevel=主干道&lat=31.23&lon=121.47
     */
    @GetMapping("/now")
    public Result<Map<String, Object>> getCurrentBrightness(
            @RequestParam(defaultValue = "次干道") String roadLevel,
            @RequestParam(defaultValue = "31.23") double lat,
            @RequestParam(defaultValue = "121.47") double lon) {

        int eMin = BrightnessCalculator.getEminByRoadLevel(roadLevel);
        WeatherData weather = weatherApiClient.fetchWeatherCached(lat, lon);
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        BrightnessResult result = BrightnessCalculator.calculate(
                eMin, weather.cloudCover(), weather.rainLevel(), weather.snowLevel(),
                weather.visibility(), now, lat, lon);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roadLevel", roadLevel);
        map.put("eMin", result.getEMin());
        map.put("solarElevation", Math.round(result.getSolarElevation() * 10) / 10.0);
        map.put("periodLabel", result.getPeriodLabel());
        map.put("theoreticalLux", Math.round(result.getTheoreticalLux()));
        map.put("weatherCorrection", Math.round(result.getWeatherCorrection() * 100) / 100.0);
        map.put("environmentalLux", Math.round(result.getEnvironmentalLux()));
        map.put("recommendedBrightness", result.getRecommendedBrightness());
        map.put("weather", weather.description());
        map.put("cloudCover", weather.cloudCover());
        map.put("rainLevel", weather.rainLevel());
        map.put("visibility", weather.visibility());
        map.put("datetime", result.getDateTime().toString());

        return Result.success(map);
    }

    /**
     * 获取今日全天（0-23时）的亮度推荐预览
     * GET /api/brightness/today?roadLevel=主干道
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayPreview(
            @RequestParam(defaultValue = "次干道") String roadLevel,
            @RequestParam(defaultValue = "31.23") double lat,
            @RequestParam(defaultValue = "121.47") double lon) {

        int eMin = BrightnessCalculator.getEminByRoadLevel(roadLevel);
        WeatherData weather = weatherApiClient.fetchWeatherCached(lat, lon);

        LocalDateTime base = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).withHour(0);
        int[] hourlyBrightness = new int[24];
        String[] hourlyPeriod = new String[24];

        for (int h = 0; h < 24; h++) {
            LocalDateTime dt = base.withHour(h);
            BrightnessResult r = BrightnessCalculator.calculate(
                    eMin, weather.cloudCover(), weather.rainLevel(), weather.snowLevel(),
                    weather.visibility(), dt, lat, lon);
            hourlyBrightness[h] = r.getRecommendedBrightness();
            hourlyPeriod[h] = r.getPeriodLabel();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roadLevel", roadLevel);
        map.put("eMin", eMin);
        map.put("date", base.toLocalDate().toString());
        map.put("hourlyBrightness", hourlyBrightness);
        map.put("hourlyPeriod", hourlyPeriod);

        return Result.success(map);
    }
}
