package com.smartlight.backend.dto;

import lombok.Data;

import java.util.List;

/**
 * 报警处理 DTO
 */
@Data
public class AlertHandleDTO {
    private List<Long> ids;
    private String handler;
    private String handleRemark;
}
