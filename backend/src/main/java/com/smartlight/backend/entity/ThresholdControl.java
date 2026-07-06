package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("threshold_control")
public class ThresholdControl {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Boolean enabled;

    private Double lightOnThreshold;

    private Double lightOffThreshold;

    private Integer lowBrightness;

    private Integer midBrightness;

    private Integer highBrightness;

    private Integer detectionPeriod;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}