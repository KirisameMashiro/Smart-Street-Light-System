package com.smartlight.backend.dto;

import lombok.Data;

/**
 * 人流量查询 DTO
 */
@Data
public class PedestrianFlowQueryDTO {
    private int pageNum = 1;
    private int pageSize = 10;
    private Long lightId;
    private String startTime;
    private String endTime;
}