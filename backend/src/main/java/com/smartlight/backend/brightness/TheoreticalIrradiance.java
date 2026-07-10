package com.smartlight.backend.brightness;

/**
 * 理论照度转换 — 太阳高度角 → 地面自然光照度 (lux)
 * 基于 ASHRAE Clear Sky 模型的简化版本
 */
public class TheoreticalIrradiance {

    /** 太阳常数简化值 (lux) */
    private static final double LUX_SOLAR_CONSTANT = 120_000.0;

    /** 民用曙暮光照度 (lux) — 太阳在地平线下 0°~6° */
    private static final double CIVIL_TWILIGHT_LUX = 3.4;

    /** 航海曙暮光照度 (lux) — 太阳在地平线下 6°~12° */
    private static final double NAUTICAL_TWILIGHT_LUX = 0.008;

    /**
     * 根据太阳高度角计算理论自然照度
     *
     * @param elevationDeg 太阳高度角（度）
     * @return 理论照度 (lux)，范围 0 ~ 120000
     */
    public static double calcIrradiance(double elevationDeg) {
        // 深夜：太阳高度角 ≤ -12°，完全黑暗
        if (elevationDeg <= -12.0) {
            return 0.0;
        }

        // 航海曙暮光：-12° < h ≤ -6°
        if (elevationDeg <= -6.0) {
            // 从 0.008 lux 线性过渡到 3.4 lux
            double ratio = (elevationDeg + 12.0) / 6.0; // 0→1
            return NAUTICAL_TWILIGHT_LUX + (CIVIL_TWILIGHT_LUX - NAUTICAL_TWILIGHT_LUX) * ratio;
        }

        // 民用曙暮光：-6° < h ≤ 0°
        if (elevationDeg <= 0.0) {
            // 从 3.4 lux 过渡到 ASHRAE 模型在 h=0° 时的值
            // h=0° 时 sin(0)=0，直接用 smooth transition
            double ratio = (elevationDeg + 6.0) / 6.0; // 0→1
            return CIVIL_TWILIGHT_LUX + (LUX_SOLAR_CONSTANT * Math.sin(Math.toRadians(0.5)) - CIVIL_TWILIGHT_LUX) * ratio;
        }

        // 白天：h > 0°，使用简化 ASHRAE 晴空模型
        // Lstd = 120000 × sin(h)，考虑了大气路径长度
        double sinH = Math.sin(Math.toRadians(elevationDeg));
        return LUX_SOLAR_CONSTANT * sinH;
    }

    /**
     * 判断当前是否为白天（太阳在地平线以上）
     */
    public static boolean isDaytime(double elevationDeg) {
        return elevationDeg > 0.0;
    }

    /**
     * 获取时段标签
     */
    public static String getPeriodLabel(double elevationDeg) {
        if (elevationDeg > 6.0) return "日间";
        if (elevationDeg > 0.0) return "日出";
        if (elevationDeg > -6.0) return "日落/黄昏";
        if (elevationDeg > -12.0) return "傍晚";
        return "深夜";
    }
}
