package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.service.LightService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LightServiceImpl extends ServiceImpl<LightMapper, Light> implements LightService {

    @Override
    public IPage<Light> getPage(int pageNum, int pageSize, String keyword, Integer status, String district, String road, String deviceType) {
        Page<Light> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Light> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Light::getLightName, keyword)
                   .or()
                   .like(Light::getLightCode, keyword)
                   .or()
                   .like(Light::getLocation, keyword);
        }

        if (status != null) {
            wrapper.eq(Light::getStatus, status);
        }

        if (StringUtils.hasText(district)) {
            wrapper.eq(Light::getDistrict, district);
        }

        if (StringUtils.hasText(road)) {
            wrapper.eq(Light::getRoad, road);
        }

        if (StringUtils.hasText(deviceType)) {
            wrapper.eq(Light::getDeviceType, deviceType);
        }

        wrapper.orderByDesc(Light::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchSwitchStatus(List<Long> ids, Integer status) {
        List<Light> lights = this.listByIds(ids);
        for (Light light : lights) {
            light.setStatus(status);
            if (status == 0) {
                light.setBrightness(0);
            } else if (status == 1 && (light.getBrightness() == null || light.getBrightness() == 0)) {
                light.setBrightness(100);
            }
        }
        return this.updateBatchById(lights);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setBrightness(Long id, Integer brightness) {
        Light light = this.getById(id);
        if (light == null) {
            return false;
        }
        light.setBrightness(brightness);
        if (brightness > 0) {
            light.setStatus(1);
        } else {
            light.setStatus(0);
        }
        return this.updateById(light);
    }

    @Override
    public long countByStatus(Integer status) {
        LambdaQueryWrapper<Light> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Light::getStatus, status);
        return this.count(wrapper);
    }

    @Override
    public List<String> getDistricts() {
        return this.list()
                .stream()
                .map(Light::getDistrict)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getRoads() {
        return this.list()
                .stream()
                .map(Light::getRoad)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getDeviceTypes() {
        return this.list()
                .stream()
                .map(Light::getDeviceType)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}