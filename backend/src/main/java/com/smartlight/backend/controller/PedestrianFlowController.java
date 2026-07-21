package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.PedestrianFlowQueryDTO;
import com.smartlight.backend.entity.PedestrianFlow;
import com.smartlight.backend.service.PedestrianFlowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 人流量监测 API
 * <p>
 * - 历史数据 /page 从小时聚合表查询<br>
 * - 最新数据 /latest/{lightId}、/latest/all 优先走 Redis 缓存
 */
@RestController
@RequestMapping("/api/pedestrian-flow")
public class PedestrianFlowController {

    @Autowired
    private PedestrianFlowService pedestrianFlowService;

    /**
     * 分页查询人流量历史数据
     */
    @GetMapping("/page")
    public Result<IPage<PedestrianFlow>> getPage(PedestrianFlowQueryDTO queryDTO) {
        IPage<PedestrianFlow> page = pedestrianFlowService.getPage(
                queryDTO.getPageNum(),
                queryDTO.getPageSize(),
                queryDTO.getLightId(),
                queryDTO.getStartTime(),
                queryDTO.getEndTime()
        );
        return Result.success(page);
    }

    /**
     * 获取路灯最新人流量数据
     */
    @GetMapping("/latest/{lightId}")
    public Result<PedestrianFlow> getLatest(@PathVariable Long lightId) {
        return Result.success(pedestrianFlowService.getLatestByLightId(lightId));
    }

    /**
     * 批量获取所有路灯最新人流量数据
     */
    @GetMapping("/latest/all")
    public Result<Map<Long, PedestrianFlow>> getAllLatest() {
        return Result.success(pedestrianFlowService.getAllLatest());
    }

    /**
     * 获取平均人流量数据
     */
    @GetMapping("/average/{lightId}")
    public Result<PedestrianFlow> getAverage(
            @PathVariable Long lightId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        if (startTime == null) startTime = LocalDateTime.now().minusHours(1);
        if (endTime == null) endTime = LocalDateTime.now();
        return Result.success(pedestrianFlowService.getAverageData(lightId, startTime, endTime));
    }

    /**
     * 新增人流量数据
     */
    @PostMapping
    public Result<Boolean> add(@RequestBody PedestrianFlow pedestrianFlow) {
        if (pedestrianFlow.getCollectTime() == null) {
            pedestrianFlow.setCollectTime(LocalDateTime.now());
        }
        return Result.success(pedestrianFlowService.save(pedestrianFlow));
    }
}