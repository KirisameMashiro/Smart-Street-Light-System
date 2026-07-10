package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 亮度推荐结果实体
 */
@Data
@TableName("brightness_recommendation")
public class BrightnessRecommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 计算的目标小时（精确到小时） */
    private LocalDateTime calcHour;

    /** 道路等级 */
    private String roadLevel;

    /** 太阳高度角（度） */
    private Double solarElevation;

    /** 理论基准照度 Lstd (lux) */
    private Double theoreticalLux;

    /** 天气修正系数 Wcorr */
    private Double weatherCorrection;

    /** 等效环境光照 Lenv (lux) */
    private Double environmentalLux;

    /** 道路最低安全照度 Emin (lux) */
    private Integer eMin;

    /** 推荐亮度 0-100 */
    private Integer recommendedBrightness;

    /** 时段标签 */
    private String periodLabel;

    /** 云量 */
    private Integer cloudCover;

    /** 降雨等级 */
    private String rainLevel;

    /** 天气描述 */
    private String weatherDesc;

    /** 纬度 */
    private Double latitude;

    /** 经度 */
    private Double longitude;

    /** 创建时间 */
    private LocalDateTime createTime;
}
