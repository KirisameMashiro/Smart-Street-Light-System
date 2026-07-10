package com.smartlight.backend.brightness;

/**
 * 天气修正系数计算 — 云量/降雨/雾况 → 衰减系数 Wcorr ∈ [0.1, 1.0]
 * <p>
 * 计算逻辑：
 *   1. 云量覆盖线性衰减（0~100% → 1.0~0.4）
 *   2. 降雨等级叠加衰减
 *   3. 能见度/雾分级折减
 *   4. 夜间仅保留能见度修正
 *   5. 结果钳位至 [0.1, 1.0]
 */
public class WeatherCorrector {

    /** 降雨等级枚举 */
    private static final double RAIN_LIGHT   = 0.08;
    private static final double RAIN_MODERATE = 0.15;
    private static final double RAIN_HEAVY   = 0.25;
    private static final double RAIN_STORM   = 0.35;

    /** 降雪等级 */
    private static final double SNOW_LIGHT   = 0.10;
    private static final double SNOW_MODERATE = 0.20;
    private static final double SNOW_HEAVY   = 0.30;

    /**
     * 计算天气修正系数
     *
     * @param cloudCover  云量百分比 0-100
     * @param rainLevel   降雨等级: "none" / "light" / "moderate" / "heavy" / "storm"
     * @param snowLevel   降雪等级: "none" / "light" / "moderate" / "heavy"
     * @param visibility  能见度（米），≥10000 表示无雾
     * @param isNight     是否为夜间
     * @return Wcorr ∈ [0.1, 1.0]
     */
    public static double calcCorrection(int cloudCover, String rainLevel, String snowLevel,
                                        int visibility, boolean isNight) {
        double factor = 1.0;

        // 1. 云量衰减（0~100%）：白天全量衰减，夜间仅作为参考
        if (!isNight) {
            factor -= (cloudCover / 100.0) * 0.6; // 多云→0.4
        }

        // 2. 降雨衰减
        double rainDecay = getRainDecay(rainLevel);
        factor -= rainDecay;

        // 3. 降雪衰减
        double snowDecay = getSnowDecay(snowLevel);
        factor -= snowDecay;

        // 4. 能见度/雾衰减
        double fogDecay = getFogDecay(visibility);
        factor -= fogDecay;

        // 5. 夜间：天然光照已归零，能见度修正用于安全余量
        //    但不要让夜间系数过低（最低保留 0.3）
        if (isNight) {
            factor = Math.max(factor, 0.3);
        }

        // 钳位至 [0.1, 1.0]
        if (factor < 0.1) factor = 0.1;
        if (factor > 1.0) factor = 1.0;

        return factor;
    }

    private static double getRainDecay(String rainLevel) {
        if (rainLevel == null) return 0.0;
        return switch (rainLevel.toLowerCase()) {
            case "light"    -> RAIN_LIGHT;
            case "moderate" -> RAIN_MODERATE;
            case "heavy"    -> RAIN_HEAVY;
            case "storm"    -> RAIN_STORM;
            default         -> 0.0;
        };
    }

    private static double getSnowDecay(String snowLevel) {
        if (snowLevel == null) return 0.0;
        return switch (snowLevel.toLowerCase()) {
            case "light"    -> SNOW_LIGHT;
            case "moderate" -> SNOW_MODERATE;
            case "heavy"    -> SNOW_HEAVY;
            default         -> 0.0;
        };
    }

    /**
     * 能见度 → 雾衰减系数
     * 能见度 ≥ 10000m → 无衰减
     * 1000~10000m    → 轻雾/霾
     * 200~1000m      → 中等雾
     * <200m          → 浓雾
     */
    private static double getFogDecay(int visibility) {
        if (visibility >= 10000) return 0.0;
        if (visibility >= 5000)  return 0.05;
        if (visibility >= 1000)  return 0.10;
        if (visibility >= 500)   return 0.20;
        if (visibility >= 200)   return 0.30;
        return 0.40; // 浓雾 < 200m
    }
}
