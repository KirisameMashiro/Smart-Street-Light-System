package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.Alert;

import java.util.List;

public interface AlertService extends IService<Alert> {

    /**
     * 分页查询报警信息
     */
    IPage<Alert> getPage(int pageNum, int pageSize, Long lightId,
                         Integer alertType, Integer alertLevel, Integer status);

    /**
     * 处理报警
     */
    boolean handleAlert(Long id, String handler, String handleRemark);

    /**
     * 批量处理报警
     */
    int handleAlertBatch(List<Long> ids, String handler, String handleRemark);

    /**
     * 统计未处理的报警数量
     */
    long countUnhandled();
}