package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName(value = "timed_strategy", autoResultMap = true)
public class TimedStrategy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String type;

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    @JsonProperty("weekdays")
    private List<Integer> weekdays;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer brightness;

    /**
     * 是否启用动态亮度调节（根据实时光照传感器数据动态计算亮度）
     * true: 策略仅控开关时机，亮度由光照+阈值分段动态决定
     * false: 使用策略指定的固定亮度
     */
    private Boolean useDynamicBrightness;

    @TableField(value = "region_groups")
    @JsonIgnore
    private String regionGroupsJson;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonProperty("groups")
    public List<RegionGroup> getGroups() {
        if (regionGroupsJson == null || regionGroupsJson.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(regionGroupsJson, new TypeReference<List<RegionGroup>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    @JsonProperty("groups")
    public void setGroups(List<RegionGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            this.regionGroupsJson = null;
        } else {
            try {
                this.regionGroupsJson = OBJECT_MAPPER.writeValueAsString(groups);
            } catch (Exception e) {
                this.regionGroupsJson = null;
            }
        }
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegionGroup {
        private String district;
        private List<String> roads;
    }
}