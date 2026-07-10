package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.SensorData;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface SensorDataService extends IService<SensorData> {

    /**
     * 分页查询传感器数据（历史数据，直接查 MySQL）
     */
    IPage<SensorData> getPage(int pageNum, int pageSize, Long lightId,
                              LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取路灯的最新传感器数据
     * 优先从 Redis 缓存读取，Miss 时回退查询 MySQL
     */
    SensorData getLatestByLightId(Long lightId);

    /**
     * 获取指定时间范围内的平均数据
     */
    SensorData getAverageData(Long lightId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 批量获取所有路灯的最新传感器数据
     * 通过 Redis pipeline 一次获取全部，替代 N 次独立查询
     *
     * @return Map<lightId, SensorData> 所有路灯的最新传感器数据
     */
    Map<Long, SensorData> getAllLatest();
}