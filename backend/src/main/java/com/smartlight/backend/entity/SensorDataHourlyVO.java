package com.smartlight.backend.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 传感器小时聚合数据 VO
 * 用于批量 UPSERT sensor_data_hourly 表。
 * 每行代表一盏灯一小时的聚合统计，
 * 通过滑动平均公式累计平均值，SUM 累加 totalEnergy。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataHourlyVO {

    private Long lightId;
    private LocalDateTime hourStart;
    private Double avgIlluminance;
    private Double avgPower;
    private Double avgVoltage;
    private Double avgCurrent;
    private Double avgTemperature;
    private Double avgHumidity;
    private Double totalEnergy;
    private Integer dataCount;
    private Double maxPower;
    private Double minPower;

    public SensorDataHourlyVO(Long lightId, LocalDateTime hourStart) {
        this.lightId = lightId;
        this.hourStart = hourStart;
        this.dataCount = 0;
        this.totalEnergy = 0.0;
        this.avgIlluminance = 0.0;
        this.avgPower = 0.0;
        this.avgVoltage = 0.0;
        this.avgCurrent = 0.0;
        this.avgTemperature = 0.0;
        this.avgHumidity = 0.0;
    }

    public void accumulate(SensorData data) {
        if (data == null) return;
        int n = this.dataCount;
        this.avgIlluminance = movingAvg(this.avgIlluminance, data.getIlluminance(), n);
        this.avgPower = movingAvg(this.avgPower, data.getPower(), n);
        this.avgVoltage = movingAvg(this.avgVoltage, data.getVoltage(), n);
        this.avgCurrent = movingAvg(this.avgCurrent, data.getCurrent(), n);
        this.avgTemperature = movingAvg(this.avgTemperature, data.getTemperature(), n);
        this.avgHumidity = movingAvg(this.avgHumidity, data.getHumidity(), n);
        if (data.getSamplingEnergy() != null) {
            this.totalEnergy += data.getSamplingEnergy();
        }
        double p = data.getPower() != null ? data.getPower() : 0.0;
        if (this.maxPower == null || p > this.maxPower) this.maxPower = p;
        if (this.minPower == null || (p > 0 && p < this.minPower)) this.minPower = p;
        this.dataCount = n + 1;
    }

    private static Double movingAvg(Double currentAvg, Double newVal, int n) {
        if (newVal == null) return currentAvg;
        double ca = (currentAvg != null) ? currentAvg : 0.0;
        return (ca * n + newVal) / (n + 1);
    }
}
