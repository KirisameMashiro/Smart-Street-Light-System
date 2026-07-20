package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Road;
import com.smartlight.backend.mapper.RoadMapper;
import com.smartlight.backend.service.RoadService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoadServiceImpl extends ServiceImpl<RoadMapper, Road> implements RoadService {

    @Override
    public List<Road> listByDistrictId(Long districtId) {
        return baseMapper.selectByDistrictId(districtId);
    }

    @Override
    public boolean save(Road entity) {
        // 同行政区内路段名称唯一性校验
        LambdaQueryWrapper<Road> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Road::getRoadName, entity.getRoadName())
               .eq(Road::getDistrictId, entity.getDistrictId());
        if (entity.getId() != null) {
            wrapper.ne(Road::getId, entity.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("该行政区下已存在路段：" + entity.getRoadName());
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(Road entity) {
        LambdaQueryWrapper<Road> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Road::getRoadName, entity.getRoadName())
               .eq(Road::getDistrictId, entity.getDistrictId());
        if (entity.getId() != null) {
            wrapper.ne(Road::getId, entity.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("该行政区下已存在路段：" + entity.getRoadName());
        }
        return super.updateById(entity);
    }
}
