package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.District;
import com.smartlight.backend.mapper.DistrictMapper;
import com.smartlight.backend.service.DistrictService;
import org.springframework.stereotype.Service;

@Service
public class DistrictServiceImpl extends ServiceImpl<DistrictMapper, District> implements DistrictService {

    @Override
    public boolean save(District entity) {
        // 名称唯一性校验
        LambdaQueryWrapper<District> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(District::getDistrictName, entity.getDistrictName());
        if (entity.getId() != null) {
            wrapper.ne(District::getId, entity.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("行政区名称已存在：" + entity.getDistrictName());
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(District entity) {
        LambdaQueryWrapper<District> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(District::getDistrictName, entity.getDistrictName());
        if (entity.getId() != null) {
            wrapper.ne(District::getId, entity.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("行政区名称已存在：" + entity.getDistrictName());
        }
        return super.updateById(entity);
    }
}
