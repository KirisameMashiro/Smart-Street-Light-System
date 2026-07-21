package com.smartlight.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人流量数据上报 DTO（MQTT / HTTP 共用）
 */
@Data
public class PedestrianFlowIngestDTO {

    /** 关联路灯ID */
    private Long lightId;

    /** 人流量计数 */
    private Integer flowCount;

    /** 数据采集时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectTime;
}