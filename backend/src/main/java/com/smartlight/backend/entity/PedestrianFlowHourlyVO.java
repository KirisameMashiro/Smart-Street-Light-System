package com.smartlight.backend.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 人流量小时聚合数据 VO
 * 对应 pedestrian_flow_hourly 表
 */
@Data
@NoArgsConstructor
public class PedestrianFlowHourlyVO {

    private Long lightId;
    private LocalDateTime hourStart;
    private Double avgFlow;
    private Integer maxFlow;
    private Integer minFlow;
    private Integer totalFlow;
    private Integer dataCount;

    public PedestrianFlowHourlyVO(Long lightId, LocalDateTime hourStart) {
        this.lightId = lightId;
        this.hourStart = hourStart;
        this.dataCount = 0;
        this.totalFlow = 0;
        this.avgFlow = 0.0;
    }

    public void accumulate(PedestrianFlow data) {
        if (data == null || data.getFlowCount() == null) return;
        int n = this.dataCount;
        double currentAvg = (this.avgFlow != null) ? this.avgFlow : 0.0;
        this.avgFlow = (currentAvg * n + data.getFlowCount()) / (n + 1);
        this.totalFlow = (this.totalFlow == null ? 0 : this.totalFlow) + data.getFlowCount();
        if (this.maxFlow == null || data.getFlowCount() > this.maxFlow) this.maxFlow = data.getFlowCount();
        if (this.minFlow == null || (data.getFlowCount() > 0 && data.getFlowCount() < this.minFlow)) this.minFlow = data.getFlowCount();
        this.dataCount = n + 1;
    }
}