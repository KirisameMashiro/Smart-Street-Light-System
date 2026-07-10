package com.smartlight.backend.brightness;

import java.time.LocalDateTime;

/**
 * 亮度推荐计算结果
 */
public class BrightnessResult {

    /** 计算的目标时间 */
    private final LocalDateTime dateTime;

    /** 太阳高度角（度） */
    private final double solarElevation;

    /** 理论基准照度 Lstd (lux) */
    private final double theoreticalLux;

    /** 天气修正系数 Wcorr */
    private final double weatherCorrection;

    /** 等效环境光照 Lenv = Lstd × Wcorr (lux) */
    private final double environmentalLux;

    /** 道路最低安全照度 Emin (lux) */
    private final int eMin;

    /** 计算中间值 Bcalc = k×(Emin-Lenv) */
    private final double rawBrightness;

    /** 最终推荐亮度 0-100（经过三层边界校验） */
    private final int recommendedBrightness;

    /** 时段标签：日间/日出/日落-黄昏/傍晚/深夜 */
    private final String periodLabel;

    /** 计算所用的纬度 */
    private final double latitude;

    /** 计算所用的经度 */
    private final double longitude;

    public BrightnessResult(LocalDateTime dateTime, double solarElevation, double theoreticalLux,
                            double weatherCorrection, double environmentalLux, int eMin,
                            double rawBrightness, int recommendedBrightness,
                            String periodLabel, double latitude, double longitude) {
        this.dateTime = dateTime;
        this.solarElevation = solarElevation;
        this.theoreticalLux = theoreticalLux;
        this.weatherCorrection = weatherCorrection;
        this.environmentalLux = environmentalLux;
        this.eMin = eMin;
        this.rawBrightness = rawBrightness;
        this.recommendedBrightness = recommendedBrightness;
        this.periodLabel = periodLabel;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public LocalDateTime getDateTime() { return dateTime; }
    public double getSolarElevation() { return solarElevation; }
    public double getTheoreticalLux() { return theoreticalLux; }
    public double getWeatherCorrection() { return weatherCorrection; }
    public double getEnvironmentalLux() { return environmentalLux; }
    public int getEMin() { return eMin; }
    public double getRawBrightness() { return rawBrightness; }
    public int getRecommendedBrightness() { return recommendedBrightness; }
    public String getPeriodLabel() { return periodLabel; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public String toString() {
        return String.format(
            "时间=%s | 时段=%s | 高度角=%.1f° | Lstd=%.0f lux | Wcorr=%.2f | Lenv=%.0f lux | Emin=%d | 亮度=%d%%",
            dateTime.toLocalDate() + " " + dateTime.toLocalTime().withSecond(0).withNano(0),
            periodLabel, solarElevation, theoreticalLux, weatherCorrection, environmentalLux,
            eMin, recommendedBrightness
        );
    }
}
