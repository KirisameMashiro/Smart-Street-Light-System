package com.smartlight.backend.dto;

import lombok.Data;

/**
 * 传感器数据查询参数 DTO
 */
@Data
public class SensorDataQueryDTO {
    private int pageNum = 1;
    private int pageSize = 10;
    private Long lightId;
    private String startTime;
    private String endTime;
}
