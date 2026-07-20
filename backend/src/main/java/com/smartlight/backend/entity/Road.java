package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 路段基础表
 */
@Data
@TableName("road")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Road {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 路段名称 */
    @TableField("road_name")
    private String roadName;

    /** 所属行政区ID */
    @TableField("district_id")
    private Long districtId;

    /** 排序号 */
    @TableField("sort_order")
    private Integer sortOrder;

    /** 描述 */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 关联行政区名称（非数据库字段） */
    @TableField(exist = false)
    private String districtName;
}
