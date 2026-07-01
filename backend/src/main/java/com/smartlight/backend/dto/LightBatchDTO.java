package com.smartlight.backend.dto;

import lombok.Data;
import java.util.List;

/**
 * 路灯批量操作 DTO
 */
@Data
public class LightBatchDTO {
    private List<Long> ids;
    private Integer status;
}
