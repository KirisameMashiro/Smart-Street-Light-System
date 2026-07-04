package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.Light;

import java.util.List;

public interface LightService extends IService<Light> {

    /**
     * 分页查询路灯列表
     */
    IPage<Light> getPage(int pageNum, int pageSize, String keyword, Integer status, String district, String road, String deviceType);

    /**
     * 批量开关灯
     */
    boolean batchSwitchStatus(List<Long> ids, Integer status);

    /**
     * 设置路灯亮度
     */
    boolean setBrightness(Long id, Integer brightness);

    /**
     * 根据状态统计数量
     */
    long countByStatus(Integer status);

    /**
     * 获取所有行政区列表
     */
    List<String> getDistricts();

    /**
     * 获取所有路段列表
     */
    List<String> getRoads();

    /**
     * 获取所有设备类型列表
     */
    List<String> getDeviceTypes();
}