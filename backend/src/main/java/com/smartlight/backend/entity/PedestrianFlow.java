package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人流量原始数据实体类
 * 对应 pedestrian_flow 表
 */
@Data
@TableName("pedestrian_flow")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PedestrianFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联路灯ID */
    private Long lightId;

    /** 人流量计数 */
    private Integer flowCount;

    /** 数据采集时间 */
    private LocalDateTime collectTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}