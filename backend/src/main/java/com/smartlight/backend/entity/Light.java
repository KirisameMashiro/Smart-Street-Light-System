package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 路灯设备实体类
 */
@Data
@TableName("light")
public class Light {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 路灯编号 */
    private String lightCode;

    /** 路灯名称 */
    private String lightName;

    /** 安装位置 */
    private String location;

    /** 经度 */
    private Double longitude;

    /** 纬度 */
    private Double latitude;

    /** 路灯状态：0-关闭，1-开启，2-故障 */
    private Integer status;

    /** 亮度百分比 (0-100) */
    private Integer brightness;

    /** 是否手动控制：true-手动控制（定时策略不会关闭），false-自动控制 */
    private Boolean manualControl;

    /** 设备类型 */
    private String deviceType;

    /** 额定功率 (W) */
    private Double ratedPower;

    /** 备注 */
    private String remark;

    /** 行政区 */
    private String district;

    /** 路段 */
    private String road;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
