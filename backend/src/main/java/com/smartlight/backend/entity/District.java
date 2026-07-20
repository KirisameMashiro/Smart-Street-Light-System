package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 行政区基础表
 */
@Data
@TableName("district")
@JsonIgnoreProperties(ignoreUnknown = true)
public class District {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 行政区名称 */
    @TableField("district_name")
    private String districtName;

    /** 行政区编码 */
    @TableField("district_code")
    private String districtCode;

    /** 排序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
