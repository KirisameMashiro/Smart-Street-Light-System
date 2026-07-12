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
            defaults.setSegments(createSegments(30.0, 100, 60, 30));
            return defaults;
        }
        return list.get(0);
    }

    private List<ThresholdControl.SegmentConfig> createSegments(double baseThreshold, int lowB, int midB, int highB) {
        ThresholdControl.SegmentConfig s1 = new ThresholdControl.SegmentConfig();
        s1.setThreshold(baseThreshold);
        s1.setBrightness(lowB);
        ThresholdControl.SegmentConfig s2 = new ThresholdControl.SegmentConfig();
        s2.setThreshold(baseThreshold + 20);
        s2.setBrightness(midB);
        ThresholdControl.SegmentConfig s3 = new ThresholdControl.SegmentConfig();
        s3.setThreshold(baseThreshold + 40);
        s3.setBrightness(highB);
        return List.of(s1, s2, s3);
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
            return;
        }

        Double lightOff = config.getLightOffThreshold();
        List<ThresholdControl.SegmentConfig> segments = config.getSegments();

        if (lightOff == null) return;
        if (segments == null || segments.isEmpty()) {
            return;
        }

        List<Light> lights = lightMapper.selectList(
                new LambdaQueryWrapper<Light>().eq(Light::getStatus, 1));

        int adjustedCount = 0;
        for (Light light : lights) {
            SensorData latest = sensorDataMapper.selectLatestByLightId(light.getId());
            if (latest == null || latest.getIlluminance() == null) {
                continue;
            }

            double illuminance = latest.getIlluminance();
            Integer targetBrightness;
            Integer targetStatus;

            if (illuminance > lightOff) {
                targetStatus = 0;
                targetBrightness = 0;
            } else {
                targetStatus = 1;
                targetBrightness = findMatchingBrightness(illuminance, segments);
            }

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

    private Integer findMatchingBrightness(double illuminance, List<ThresholdControl.SegmentConfig> segments) {
        ThresholdControl.SegmentConfig matched = null;
        for (ThresholdControl.SegmentConfig seg : segments) {
            if (illuminance <= seg.getThreshold()) {
                if (matched == null || seg.getThreshold() > matched.getThreshold()) {
                    matched = seg;
                }
            }
        }
        if (matched == null && !segments.isEmpty()) {
            matched = segments.stream()
                    .min((a, b) -> Double.compare(a.getThreshold(), b.getThreshold()))
                    .orElse(null);
        }
        return matched != null && matched.getBrightness() != null ? matched.getBrightness() : 100;
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