package com.smartlight.backend.scheduler;

import com.smartlight.backend.brightness.BrightnessCalculator;
import com.smartlight.backend.brightness.BrightnessResult;
import com.smartlight.backend.brightness.WeatherApiClient;
import com.smartlight.backend.brightness.WeatherApiClient.WeatherData;
import com.smartlight.backend.entity.BrightnessRecommendation;
import com.smartlight.backend.mapper.BrightnessRecommendationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 逐时亮度推荐定时调度器
 * 每整点（5分处）执行：计算推荐亮度 → 存库（仅预测，不控制路灯）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrightnessScheduler {

    private final BrightnessRecommendationMapper recommendationMapper;
    private final WeatherApiClient weatherApiClient;

    /** 默认使用上海经纬度 */
    private static final double DEFAULT_LAT = 31.23;
    private static final double DEFAULT_LON = 121.47;

    /**
     * 每小时第5分钟执行
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void hourlyBrightnessRecommendation() {
        log.info("=== 逐时亮度推荐计算开始 ===");
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        // 获取天气数据
        WeatherData weather = weatherApiClient.fetchWeatherCached(DEFAULT_LAT, DEFAULT_LON);
        log.info("当前天气: {} (云量:{}%, 降雨:{}, 能见度:{}m)",
                weather.description(), weather.cloudCover(), weather.rainLevel(), weather.visibility());

        // 对三种道路等级分别计算推荐亮度
        record RoadConfig(String level, int eMin) {}
        RoadConfig[] configs = {
                new RoadConfig("主干道", BrightnessCalculator.EMIN_MAIN_ROAD),
                new RoadConfig("次干道", BrightnessCalculator.EMIN_SECONDARY_ROAD),
                new RoadConfig("园区道路", BrightnessCalculator.EMIN_PARK_ROAD),
        };

        for (RoadConfig config : configs) {
            try {
                BrightnessResult result = BrightnessCalculator.calculate(
                        config.eMin(),
                        weather.cloudCover(),
                        weather.rainLevel(),
                        weather.snowLevel(),
                        weather.visibility(),
                        now,
                        DEFAULT_LAT, DEFAULT_LON
                );
                log.info("{}: {}", config.level(), result);

                // 仅存库，不控制路灯
                saveResult(result, config.level(), weather);

            } catch (Exception e) {
                log.error("{} 亮度推荐计算失败", config.level(), e);
            }
        }

        log.info("=== 逐时亮度推荐计算结束 ===");
    }

    private void saveResult(BrightnessResult result, String roadLevel, WeatherData weather) {
        BrightnessRecommendation rec = new BrightnessRecommendation();
        rec.setCalcHour(result.getDateTime());
        rec.setRoadLevel(roadLevel);
        rec.setSolarElevation(result.getSolarElevation());
        rec.setTheoreticalLux(result.getTheoreticalLux());
        rec.setWeatherCorrection(result.getWeatherCorrection());
        rec.setEnvironmentalLux(result.getEnvironmentalLux());
        rec.setEMin(result.getEMin());
        rec.setRecommendedBrightness(result.getRecommendedBrightness());
        rec.setPeriodLabel(result.getPeriodLabel());
        rec.setCloudCover(weather.cloudCover());
        rec.setRainLevel(weather.rainLevel());
        rec.setWeatherDesc(weather.description());
        rec.setLatitude(result.getLatitude());
        rec.setLongitude(result.getLongitude());
        rec.setCreateTime(LocalDateTime.now());
        recommendationMapper.insert(rec);
    }
}
