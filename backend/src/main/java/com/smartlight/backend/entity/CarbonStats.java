package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 碳减排日统计数据实体
 */
@Data
@TableName("carbon_stats")
public class CarbonStats {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** 路段（null=全路段汇总） */
    private String road;

    /** 路灯数量 */
    private Integer lightCount;

    /** 基准能耗(kWh) */
    private Double baselineEnergy;

    /** 实际能耗(kWh) */
    private Double actualEnergy;

    /** 节电量(kWh) */
    private Double savedEnergy;

    /** CO₂减排量(kg) */
    private Double co2Reduction;

    /** 节能率(%) */
    private Double savingRate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}