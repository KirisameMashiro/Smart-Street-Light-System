package com.smartlight.backend.brightness;

import java.time.LocalDateTime;

/**
 * 太阳位置计算 — 赤纬/时角/高度角
 * 使用标准天文公式，零数据依赖
 */
public class SolarPosition {

    /**
     * 计算太阳赤纬（弧度）
     * δ = -23.44° × cos(360/365 × (N + 10))
     *
     * @param dayOfYear 一年中的第几天 (1-365)
     */
    public static double calcDeclinationRad(int dayOfYear) {
        double degrees = -23.44 * Math.cos(Math.toRadians(360.0 / 365.0 * (dayOfYear + 10)));
        return Math.toRadians(degrees);
    }

    /**
     * 计算太阳时角（弧度）
     * H = 15° × (真太阳时 - 12)
     *
     * @param longitude 经度（度）
     * @param dateTime  日期时间（本地时间）
     */
    public static double calcHourAngleRad(double longitude, LocalDateTime dateTime) {
        int dayOfYear = dateTime.getDayOfYear();

        // 均时差（分钟）
        double b = Math.toRadians(360.0 / 365.0 * (dayOfYear - 81));
        double eot = 9.87 * Math.sin(2 * b) - 7.53 * Math.cos(b) - 1.5 * Math.sin(b);

        // 当地标准子午线（中国标准时间 UTC+8，子午线为 120°E）
        double standardMeridian = 120.0;
        double timeOffset = (longitude - standardMeridian) * 4 + eot;

        // 真太阳时（小时）
        double solarTime = dateTime.getHour() + dateTime.getMinute() / 60.0
                + dateTime.getSecond() / 3600.0 + timeOffset / 60.0;

        double hourAngleDeg = 15.0 * (solarTime - 12);
        return Math.toRadians(hourAngleDeg);
    }

    /**
     * 计算太阳高度角（度）
     * h = arcsin(sin(lat)×sin(δ) + cos(lat)×cos(δ)×cos(H))
     *
     * @param latDeg      纬度（度）
     * @param declination 赤纬（弧度）
     * @param hourAngle   时角（弧度）
     * @return 太阳高度角（度），正值=太阳在地平线上，负值=在地平线下
     */
    public static double calcElevationDeg(double latDeg, double declination, double hourAngle) {
        double latRad = Math.toRadians(latDeg);
        double sinElev = Math.sin(latRad) * Math.sin(declination)
                + Math.cos(latRad) * Math.cos(declination) * Math.cos(hourAngle);
        // 浮点精度保护
        if (sinElev > 1.0) sinElev = 1.0;
        if (sinElev < -1.0) sinElev = -1.0;
        return Math.toDegrees(Math.asin(sinElev));
    }

    /**
     * 一站式计算：给定经纬度和时间，返回太阳高度角
     */
    public static double calculateElevation(double latDeg, double lonDeg, LocalDateTime dateTime) {
        int dayOfYear = dateTime.getDayOfYear();
        double declination = calcDeclinationRad(dayOfYear);
        double hourAngle = calcHourAngleRad(lonDeg, dateTime);
        return calcElevationDeg(latDeg, declination, hourAngle);
    }

    /**
     * 获取当前时间的太阳高度角（上海经纬度）
     */
    public static double getCurrentElevationShanghai() {
        return calculateElevation(31.23, 121.47, LocalDateTime.now());
    }
}
