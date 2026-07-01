package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 报警信息实体类
 */
@Data
@TableName("alert")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联路灯ID */
    private Long lightId;

    /** 报警类型：1-过流，2-过压，3-欠压，4-过热，5-通讯故障，6-其他 */
    private Integer alertType;

    /** 报警级别：1-提示，2-一般，3-严重，4-紧急 */
    private Integer alertLevel;

    /** 报警内容 */
    private String message;

    /** 处理状态：0-未处理，1-已处理 */
    private Integer status;

    /** 处理人 */
    private String handler;

    /** 处理时间 */
    private LocalDateTime handleTime;

    /** 处理备注 */
    private String handleRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
