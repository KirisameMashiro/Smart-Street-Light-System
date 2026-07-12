package com.smartlight.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("threshold_control")
public class ThresholdControl {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Boolean enabled;

    private Double lightOnThreshold;

    private Double lightOffThreshold;

    private Integer lowBrightness;

    private Integer midBrightness;

    private Integer highBrightness;

    private Integer detectionPeriod;

    @TableField(exist = false)
    private List<SegmentConfig> segments;

    @TableField("segments")
    private String segmentsJson;

    @Data
    public static class SegmentConfig {
        private Double threshold;
        private Integer brightness;
    }

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public List<SegmentConfig> getSegments() {
        if (segments == null && segmentsJson != null) {
            try {
                segments = new ObjectMapper().readValue(segmentsJson, new TypeReference<List<SegmentConfig>>() {});
            } catch (IOException e) {
                segments = new ArrayList<>();
            }
        }
        return segments;
    }

    public void setSegments(List<SegmentConfig> segments) {
        this.segments = segments;
        if (segments != null) {
            try {
                this.segmentsJson = new ObjectMapper().writeValueAsString(segments);
            } catch (IOException e) {
                this.segmentsJson = "[]";
            }
        } else {
            this.segmentsJson = null;
        }
    }
}