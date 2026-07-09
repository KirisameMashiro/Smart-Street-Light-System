package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.entity.ThresholdControl;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.mapper.ThresholdControlMapper;
import com.smartlight.backend.service.MqttPublishService;
import com.smartlight.backend.service.ThresholdControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThresholdControlServiceImpl implements ThresholdControlService {

    private final ThresholdControlMapper thresholdControlMapper;
    private final SensorDataMapper sensorDataMapper;
    private final LightMapper lightMapper;
    private final MqttPublishService mqttPublishService;

    @Override
    public ThresholdControl getConfig() {
        List<ThresholdControl> list = thresholdControlMapper.selectList(null);
        if (list == null || list.isEmpty()) {
            ThresholdControl defaults = new ThresholdControl();
            defaults.setEnabled(false);
            defaults.setLightOnThreshold(30.0);
            defaults.setLightOffThreshold(100.0);
            defaults.setLowBrightness(100);
            defaults.setMidBrightness(60);
            defaults.setHighBrightness(30);
            defaults.setDetectionPeriod(60);
            return defaults;
        }
        return list.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveConfig(ThresholdControl config) {
        List<ThresholdControl> list = thresholdControlMapper.selectList(null);
        if (list == null || list.isEmpty()) {
            config.setId(null);
            return thresholdControlMapper.insert(config) > 0;
        } else {
            ThresholdControl existing = list.get(0);
            config.setId(existing.getId());
            return thresholdControlMapper.updateById(config) > 0;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleEnabled(boolean enabled) {
        ThresholdControl config = getConfig();
        config.setEnabled(enabled);
        if (config.getId() == null) {
            return thresholdControlMapper.insert(config) > 0;
        } else {
            return thresholdControlMapper.updateById(config) > 0;
        }
    }

    /**
     * 定时执行光照联动控制（每 60 秒扫描一次）
     * 读取光照传感器数据，根据阈值自动调节路灯亮度
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void autoLinkageControl() {
        ThresholdControl config = getConfig();
        if (!config.getEnabled()) {
            return; // 总开关关闭，不执行联动
        }

        Double lightOn = config.getLightOnThreshold();
        Double lightOff = config.getLightOffThreshold();
        Integer lowB = config.getLowBrightness();
        Integer midB = config.getMidBrightness();
        Integer highB = config.getHighBrightness();

        if (lightOn == null || lightOff == null) return;
        if (lowB == null) lowB = 100;
        if (midB == null) midB = 60;
        if (highB == null) highB = 30;

        // 查询所有在线的路灯
        List<Light> lights = lightMapper.selectList(
                new LambdaQueryWrapper<Light>().eq(Light::getStatus, 1));

        int adjustedCount = 0;
        for (Light light : lights) {
            // 获取该路灯最新传感器数据
            SensorData latest = sensorDataMapper.selectLatestByLightId(light.getId());
            if (latest == null || latest.getIlluminance() == null) {
                continue;
            }

            double illuminance = latest.getIlluminance();
            Integer targetBrightness;
            Integer targetStatus;

            if (illuminance < lightOn) {
                // 环境光照低于开灯阈值 → 开灯，低光照档（最亮）
                targetStatus = 1;
                targetBrightness = lowB;
            } else if (illuminance > lightOff) {
                // 环境光照高于关灯阈值 → 关灯
                targetStatus = 0;
                targetBrightness = 0;
            } else {
                // 环境光照在开灯和关灯之间 → 按比例分档
                targetStatus = 1;
                double ratio = (illuminance - lightOn) / (lightOff - lightOn);
                if (ratio < 0.33) {
                    targetBrightness = lowB;       // 偏暗 → 低光照档（高亮度）
                } else if (ratio < 0.66) {
                    targetBrightness = midB;       // 中等 → 中光照档
                } else {
                    targetBrightness = highB;       // 偏亮 → 高光照档（低亮度）
                }
            }

            // 如果状态或亮度有变化才执行
            boolean changed = false;
            if (!Integer.valueOf(targetStatus).equals(light.getStatus())) {
                if (targetStatus == 0 && Boolean.TRUE.equals(light.getManualControl())) {
                    continue;
                }
                light.setStatus(targetStatus);
                changed = true;
            }
            if (!targetBrightness.equals(light.getBrightness())) {
                if (targetBrightness == 0 && Boolean.TRUE.equals(light.getManualControl())) {
                    continue;
                }
                light.setBrightness(targetBrightness);
                changed = true;
            }

            if (changed) {
                lightMapper.updateById(light);
                // MQTT发布组合命令
                mqttPublishService.publishCombinedControl(light.getLightCode(), targetStatus, targetBrightness);
                adjustedCount++;
                log.debug("阈值联动: lightId={}, 光照={}lux, 状态={}, 亮度={}%",
                        light.getId(), illuminance, targetStatus, targetBrightness);
            }
        }

        if (adjustedCount > 0) {
            log.info("阈值联动: 已调节 {} 盏路灯", adjustedCount);
        }
    }

    /**
     * 供 @Scheduled 动态获取检测周期
     */
    public long getDetectionPeriod() {
        ThresholdControl config = getConfig();
        Integer period = config.getDetectionPeriod();
        return period != null && period > 0 ? period : 60;
    }
}