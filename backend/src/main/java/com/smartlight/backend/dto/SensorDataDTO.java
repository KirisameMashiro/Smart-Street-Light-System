package com.smartlight.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传感器数据上报 DTO（MQTT / HTTP 共用）
 * 字段与数据库 sensor_data 表对齐
 */
@Data
public class SensorDataDTO {

    /** 关联路灯ID */
    private Long lightId;

    /** 光照强度 (lux) */
    private Double illuminance;

    /** 当前功率 (W) */
    private Double power;

    /** 电压 (V) */
    private Double voltage;

    /** 电流 (A) */
    private Double current;

    /** 温度 (°C) */
    private Double temperature;

    /** 湿度 (%RH) */
    private Double humidity;

    /** 采样间隔耗电 (Wh) */
    private Double samplingEnergy;

    /** 数据采集时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectTime;
}