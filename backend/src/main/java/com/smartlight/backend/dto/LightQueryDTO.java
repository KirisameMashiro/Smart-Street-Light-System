package com.smartlight.backend.dto;

import lombok.Data;

/**
 * 路灯查询参数 DTO
 */
@Data
public class LightQueryDTO {
    private int pageNum = 1;
    private int pageSize = 10;
    private String keyword;
    private Integer status;
}
