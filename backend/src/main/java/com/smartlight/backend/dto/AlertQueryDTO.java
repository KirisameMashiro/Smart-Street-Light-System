package com.smartlight.backend.dto;

import lombok.Data;

/**
 * 报警查询参数 DTO
 */
@Data
public class AlertQueryDTO {
    private int pageNum = 1;
    private int pageSize = 10;
    private Long lightId;
    private Integer alertType;
    private Integer alertLevel;
    private Integer status;
}
