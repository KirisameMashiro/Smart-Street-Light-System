package com.smartlight.backend.service;

import com.smartlight.backend.dto.PedestrianFlowIngestDTO;
import com.smartlight.backend.entity.PedestrianFlow;

/**
 * 人流量数据入库服务（MQTT 通道）
 */
public interface PedestrianFlowIngestService {

    /**
     * 接收并保存人流量数据
     * @param dto 人流量数据
     * @return 保存后的 PedestrianFlow 实体
     */
    PedestrianFlow ingest(PedestrianFlowIngestDTO dto);
}