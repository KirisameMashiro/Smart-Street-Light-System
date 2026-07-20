package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartlight.backend.handler.LongListTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "broadcast", autoResultMap = true)
public class Broadcast {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    @TableField(value = "light_ids", typeHandler = LongListTypeHandler.class)
    private List<Long> lightIds;

    @TableField(value = "light_codes")
    private String lightCodes;

    private Integer enabled;

    private String description;

    @TableField(value = "create_time")
    private LocalDateTime createTime;

    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}