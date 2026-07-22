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

    /**
     * 关灯光照阈值（lux），仅动态亮度模式生效。
     * 实时光照值高于此阈值时，策略不会开灯（或关闭已开的灯）。
     * 默认 100，表示亮于 100 lux 时不需要开灯。
     */
    private Double lightOffThreshold;

    /**
     * 亮度分段配置 JSON。
     * 格式：[{"threshold":30,"brightness":100},{"threshold":60,"brightness":60},...]
     * 仅动态亮度模式生效，策略级独立配置，不再依赖全局阈值表。
     */
    @TableField(value = "brightness_segments")
    @JsonIgnore
    private String brightnessSegmentsJson;

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

        /**
         * 分组级关灯光照阈值（lux），覆盖策略级 lightOffThreshold。
         * 仅动态亮度模式生效，为 null 时继承策略级配置。
         */
        private Double lightOffThreshold;

        /**
         * 分组级亮度分段配置，覆盖策略级 brightnessSegments。
         * 仅动态亮度模式生效，为 null 或空时继承策略级配置。
         */
        private List<BrightnessSegment> brightnessSegments;
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrightnessSegment {
        private Double threshold;
        private Integer brightness;
    }

    /**
     * 获取亮度分段列表（从 JSON 反序列化），仅动态亮度模式使用
     */
    @JsonProperty("brightnessSegments")
    public List<BrightnessSegment> getBrightnessSegments() {
        if (brightnessSegmentsJson == null || brightnessSegmentsJson.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(brightnessSegmentsJson,
                    new TypeReference<List<BrightnessSegment>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 设置亮度分段列表（序列化为 JSON），仅动态亮度模式使用
     */
    @JsonProperty("brightnessSegments")
    public void setBrightnessSegments(List<BrightnessSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            this.brightnessSegmentsJson = null;
        } else {
            try {
                this.brightnessSegmentsJson = OBJECT_MAPPER.writeValueAsString(segments);
            } catch (Exception e) {
                this.brightnessSegmentsJson = null;
            }
        }
    }
}