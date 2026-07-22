package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.smartlight.backend.handler.IntegerListTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@TableName(value = "broadcast_strategy", autoResultMap = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BroadcastStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField(value = "broadcast_id")
    private Long broadcastId;

    @TableField(value = "start_time")
    private LocalTime startTime;

    @TableField(value = "end_time")
    private LocalTime endTime;

    @TableField(value = "repeat_type")
    private String repeatType;

    @TableField(value = "custom_days", typeHandler = IntegerListTypeHandler.class)
    private List<Integer> customDays;

    private Integer enabled;

    /** 是否启用人数流量条件 */
    @TableField("enable_flow")
    private Boolean enableFlow;

    /** 人数流量条件: gt-大于, lt-小于 */
    @TableField("flow_condition")
    private String flowCondition;

    /** 人数流量阈值 */
    @TableField("flow_threshold")
    private Integer flowThreshold;

    /** 播放间隔（分钟）：0 表示持续，>0 表示最小间隔 */
    @TableField("play_interval")
    private Integer playInterval;

    private String description;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String broadcastTitle;
}