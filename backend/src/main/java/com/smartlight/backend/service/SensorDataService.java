package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.SensorData;

import java.time.LocalDateTime;
import java.util.List;

public interface SensorDataService extends IService<SensorData> {

    /**
     * 分页查询传感器数据
     */
    IPage<SensorData> getPage(int pageNum, int pageSize, Long lightId,
                              LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取路灯的最新传感器数据
     */
    SensorData getLatestByLightId(Long lightId);

    /**
     * 获取指定时间范围内的平均数据
     */
    SensorData getAverageData(Long lightId, LocalDateTime startTime, LocalDateTime endTime);
}