package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音设置实体
 * 对应 voice_setting 表
 */
@Data
@TableName("voice_setting")
public class VoiceSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 语音角色/名称 */
    @TableField("voice_name")
    private String voiceName;

    /** 语速 0.5~2.0 */
    private java.math.BigDecimal speed;

    /** 音量 0.0~1.0 */
    private java.math.BigDecimal volume;

    /** 是否启用: 0-禁用, 1-启用 */
    private Integer enabled;

    /** 描述 */
    private String description;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}