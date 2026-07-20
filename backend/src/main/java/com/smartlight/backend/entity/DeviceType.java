package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备类型基础表
 */
@Data
@TableName("device_type")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型名称 */
    @TableField("type_name")
    private String typeName;

    /** 类型编码 */
    @TableField("type_code")
    private String typeCode;

    /** 额定功率(W) */
    @TableField("rated_power")
    private BigDecimal ratedPower;

    /** 描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
