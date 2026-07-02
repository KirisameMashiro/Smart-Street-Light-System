package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {
    IPage<OperationLog> getPage(int pageNum, int pageSize, String operator, String type, String startTime, String endTime);
}
