package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.AlertHandleDTO;
import com.smartlight.backend.dto.AlertQueryDTO;
import com.smartlight.backend.entity.Alert;
import com.smartlight.backend.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报警信息管理 API
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertService alertService;

    /**
     * 分页查询报警列表
     */
    @GetMapping("/page")
    public Result<IPage<Alert>> getPage(AlertQueryDTO queryDTO) {
        IPage<Alert> page = alertService.getPage(
                queryDTO.getPageNum(),
                queryDTO.getPageSize(),
                queryDTO.getLightId(),
                queryDTO.getAlertType(),
                queryDTO.getAlertLevel(),
                queryDTO.getStatus()
        );
        return Result.success(page);
    }

    /**
     * 获取报警详情
     */
    @GetMapping("/{id}")
    public Result<Alert> getById(@PathVariable Long id) {
        Alert alert = alertService.getById(id);
        if (alert == null) {
            return Result.notFound("报警信息不存在");
        }
        return Result.success(alert);
    }

    /**
     * 处理报警
     */
    @PutMapping("/{id}/handle")
    public Result<Boolean> handle(@PathVariable Long id, @RequestBody AlertHandleDTO handleDTO) {
        return Result.success(alertService.handleAlert(id, handleDTO.getHandler(), handleDTO.getHandleRemark()));
    }

    /**
     * 批量处理报警
     */
    @PutMapping("/handle-batch")
    public Result<Integer> handleBatch(@RequestBody AlertHandleDTO handleDTO) {
        return Result.success(alertService.handleAlertBatch(
                handleDTO.getIds(),
                handleDTO.getHandler(),
                handleDTO.getHandleRemark()
        ));
    }

    /**
     * 获取未处理的报警数量
     */
    @GetMapping("/unhandled-count")
    public Result<Long> getUnhandledCount() {
        return Result.success(alertService.countUnhandled());
    }
}
