package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 人流量原始数据实体类
 * 对应 pedestrian_flow 表
 */
@Data
@TableName("pedestrian_flow")
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
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

    // ========== 小时聚合扩展字段（仅查询返回，不持久化） ==========

    /** 小时最高人流量（来自 pedestrian_flow_hourly.max_flow） */
    @TableField(exist = false)
    private Integer maxFlow;

    /** 小时最低人流量（来自 pedestrian_flow_hourly.min_flow） */
    @TableField(exist = false)
    private Integer minFlow;

    /** 小时累计人流量（来自 pedestrian_flow_hourly.total_flow） */
    @TableField(exist = false)
    private Integer totalFlow;

    /** 采样次数（来自 pedestrian_flow_hourly.data_count） */
    @TableField(exist = false)
    private Integer dataCount;

    /** 快速构造：供小时聚合解析使用 */
    public PedestrianFlow(Long lightId, Integer flowCount, LocalDateTime collectTime) {
        this.lightId = lightId;
        this.flowCount = flowCount;
        this.collectTime = collectTime;
    }
}