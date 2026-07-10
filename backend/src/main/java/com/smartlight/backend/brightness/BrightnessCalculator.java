package com.smartlight.backend.brightness;

import java.time.LocalDateTime;

/**
 * 逐时推荐亮度计算器 — 整合太阳模型 + 天气修正 + 三层安全边界校验
 */
public class BrightnessCalculator {

    /** 道路等级 → 最低安全照度 Emin (lux) */
    public static final int EMIN_MAIN_ROAD     = 20;  // 主干道
    public static final int EMIN_SECONDARY_ROAD = 15;  // 次干道
    public static final int EMIN_PARK_ROAD      = 10;  // 园区道路

    /** 灯具最低亮度档位（%）— 非关闭，最低节能档 */
    private static final int BRIGHTNESS_MIN = 5;

    /** 灯具最高亮度档位（%）*/
    private static final int BRIGHTNESS_MAX = 100;

    /** 默认位置：上海 */
    private static final double DEFAULT_LAT = 31.23;
    private static final double DEFAULT_LON = 121.47;

    /**
     * 一站式亮度推荐计算
     *
     * @param eMin      道路最低安全照度 (lux)
     * @param cloudCover 云量 0-100
     * @param rainLevel  降雨等级
     * @param snowLevel  降雪等级
     * @param visibility 能见度（米）
     * @param dateTime   目标时间
     * @param lat        纬度
     * @param lon        经度
     * @return 推荐结果
     */
    public static BrightnessResult calculate(int eMin, int cloudCover, String rainLevel, String snowLevel,
                                             int visibility, LocalDateTime dateTime, double lat, double lon) {
        // Step 1: 计算太阳高度角
        double elevation = SolarPosition.calculateElevation(lat, lon, dateTime);

        // Step 2: 理论照度 Lstd
        double lStd = TheoreticalIrradiance.calcIrradiance(elevation);

        // Step 3: 天气修正系数 Wcorr
        boolean isNight = elevation <= -12.0 || lStd < 0.01;
        double wCorr = WeatherCorrector.calcCorrection(cloudCover, rainLevel, snowLevel, visibility, isNight);

        // Step 4: 等效环境光照 Lenv
        double lEnv = lStd * wCorr;

        // Step 5: 基础亮度计算 Bcalc = k × (Emin - Lenv),  k = 100/Emin
        double k = 100.0 / eMin;
        double bCalc = k * (eMin - lEnv);

        // Step 6: 三层边界强制校验
        int brightness;

        if (lEnv >= eMin) {
            // 校验1: 自然光够亮 → 最低亮度/节能
            brightness = BRIGHTNESS_MIN;
        } else if (bCalc < BRIGHTNESS_MIN) {
            // 校验2: 计算亮度太低 → 强制提升至安全照度对应档位
            brightness = BRIGHTNESS_MIN;
        } else if (bCalc > BRIGHTNESS_MAX) {
            // 校验3: 计算亮度超出上限 → 压至最大值
            brightness = BRIGHTNESS_MAX;
        } else {
            brightness = (int) Math.round(bCalc);
        }

        String period = TheoreticalIrradiance.getPeriodLabel(elevation);

        return new BrightnessResult(
                dateTime, elevation, lStd, wCorr, lEnv, eMin,
                bCalc, brightness, period, lat, lon
        );
    }

    /**
     * 使用上海默认经纬度
     */
    public static BrightnessResult calculate(int eMin, int cloudCover, String rainLevel, String snowLevel,
                                             int visibility, LocalDateTime dateTime) {
        return calculate(eMin, cloudCover, rainLevel, snowLevel, visibility, dateTime, DEFAULT_LAT, DEFAULT_LON);
    }

    /**
     * 根据道路等级字符串获取 Emin
     */
    public static int getEminByRoadLevel(String roadLevel) {
        if (roadLevel == null) return EMIN_SECONDARY_ROAD;
        return switch (roadLevel) {
            case "主干道" -> EMIN_MAIN_ROAD;
            case "次干道" -> EMIN_SECONDARY_ROAD;
            case "园区道路" -> EMIN_PARK_ROAD;
            default -> EMIN_SECONDARY_ROAD;
        };
    }
}
