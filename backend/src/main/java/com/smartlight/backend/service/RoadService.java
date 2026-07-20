package com.smartlight.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.Road;

import java.util.List;

public interface RoadService extends IService<Road> {

    /**
     * 根据行政区ID查询路段列表（包含行政区名称）
     */
    List<Road> listByDistrictId(Long districtId);
}
