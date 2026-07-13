package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Alert;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.mapper.AlertMapper;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.service.AlertService;
import com.smartlight.backend.service.MqttPublishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {

    private final LightMapper lightMapper;

    @Autowired
    @Lazy
    private MqttPublishService mqttPublishService;

    public AlertServiceImpl(LightMapper lightMapper) {
        this.lightMapper = lightMapper;
    }

    @Override
    public IPage<Alert> getPage(int pageNum, int pageSize, Long lightId,
                                Integer alertType, Integer alertLevel, Integer status) {
        Page<Alert> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();

        if (lightId != null) {
            wrapper.eq(Alert::getLightId, lightId);
        }
        if (alertType != null) {
            wrapper.eq(Alert::getAlertType, alertType);
        }
        if (alertLevel != null) {
            wrapper.eq(Alert::getAlertLevel, alertLevel);
        }
        if (status != null) {
            wrapper.eq(Alert::getStatus, status);
        }

        wrapper.orderByDesc(Alert::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleAlert(Long id, String handler, String handleRemark) {
        Alert alert = this.getById(id);
        if (alert == null || alert.getStatus() == 1) {
            return false;
        }
        alert.setStatus(1);
        alert.setHandler(handler);
        alert.setHandleRemark(handleRemark);
        alert.setHandleTime(LocalDateTime.now());
        this.updateById(alert);

        // 通讯故障(5)告警修复后，将路灯从故障(2)恢复为在线(1)并发送 MQTT 命令
        if (Integer.valueOf(5).equals(alert.getAlertType()) && alert.getLightId() != null) {
            Light light = lightMapper.selectById(alert.getLightId());
            if (light != null && Integer.valueOf(2).equals(light.getStatus())) {
                light.setStatus(1);
                // 不设亮度，由阈值联动后续决定
                lightMapper.updateById(light);
                mqttPublishService.publishCombinedControl(light.getLightCode(), 1, light.getBrightness());
            }
        }

        return true;
    }

    @Override
    public long countUnhandled() {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getStatus, 0);
        return this.count(wrapper);
    }
}