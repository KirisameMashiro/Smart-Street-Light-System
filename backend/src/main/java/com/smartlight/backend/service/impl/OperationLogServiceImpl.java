package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.OperationLog;
import com.smartlight.backend.mapper.OperationLogMapper;
import com.smartlight.backend.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public IPage<OperationLog> getPage(int pageNum, int pageSize, String operator, String type, String startTime, String endTime) {
        Page<OperationLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operator)) wrapper.eq(OperationLog::getOperator, operator);
        if (StringUtils.hasText(type)) wrapper.eq(OperationLog::getType, type);
        if (StringUtils.hasText(startTime)) wrapper.ge(OperationLog::getCreateTime, startTime);
        if (StringUtils.hasText(endTime)) wrapper.le(OperationLog::getCreateTime, endTime);
        wrapper.orderByDesc(OperationLog::getCreateTime);
        return this.page(page, wrapper);
    }
}
