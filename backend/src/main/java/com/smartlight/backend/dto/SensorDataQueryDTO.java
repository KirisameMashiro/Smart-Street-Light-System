package com.smartlight.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 传感器数据查询参数 DTO
 */
@Data
public class SensorDataQueryDTO {
    private int pageNum = 1;
    private int pageSize = 10;
    private Long lightId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
