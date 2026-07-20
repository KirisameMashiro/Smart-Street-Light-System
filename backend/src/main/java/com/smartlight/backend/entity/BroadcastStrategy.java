package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Data
@TableName("broadcast_strategy")
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

    @TableField(value = "custom_days")
    private List<Integer> customDays;

    private Integer enabled;

    private String description;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String broadcastTitle;
}