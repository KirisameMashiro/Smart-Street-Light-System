package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 传感器数据实体类
 */
@Data
@TableName("sensor_data")
public class SensorData {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    /** 数据采集时间 */
    private LocalDateTime collectTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
