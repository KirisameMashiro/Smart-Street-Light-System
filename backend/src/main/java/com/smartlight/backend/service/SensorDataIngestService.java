package com.smartlight.backend.service;

import com.smartlight.backend.dto.SensorDataDTO;
import com.smartlight.backend.entity.SensorData;

/**
 * 传感器数据入库服务（MQTT 和 HTTP 双通道共用）
 */
public interface SensorDataIngestService {

    /**
     * 接收并保存传感器数据
     * @param dto 传感器数据
     * @return 保存后的 SensorData 实体
     */
    SensorData ingest(SensorDataDTO dto);
}