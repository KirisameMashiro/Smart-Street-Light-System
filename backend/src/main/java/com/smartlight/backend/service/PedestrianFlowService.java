package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.PedestrianFlow;

import java.time.LocalDateTime;
import java.util.Map;

public interface PedestrianFlowService extends IService<PedestrianFlow> {

    /**
     * 分页查询人流量历史数据（从小时聚合表）
     */
    IPage<PedestrianFlow> getPage(int pageNum, int pageSize, Long lightId,
                                  String startTime, String endTime);

    /**
     * 获取路灯最新人流量数据（Redis优先）
     */
    PedestrianFlow getLatestByLightId(Long lightId);

    /**
     * 批量获取所有路灯最新人流量
     */
    Map<Long, PedestrianFlow> getAllLatest();

    /**
     * 获取指定时间范围内的平均人流量
     */
    PedestrianFlow getAverageData(Long lightId, LocalDateTime startTime, LocalDateTime endTime);
}