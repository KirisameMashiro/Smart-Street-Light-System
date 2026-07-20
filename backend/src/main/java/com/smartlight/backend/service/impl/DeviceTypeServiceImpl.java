package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.DeviceType;
import com.smartlight.backend.mapper.DeviceTypeMapper;
import com.smartlight.backend.service.DeviceTypeService;
import org.springframework.stereotype.Service;

@Service
public class DeviceTypeServiceImpl extends ServiceImpl<DeviceTypeMapper, DeviceType> implements DeviceTypeService {

    @Override
    public boolean save(DeviceType entity) {
        // 类型名称唯一性校验
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getTypeName, entity.getTypeName());
        if (entity.getId() != null) {
            wrapper.ne(DeviceType::getId, entity.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("设备类型名称已存在：" + entity.getTypeName());
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(DeviceType entity) {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceType::getTypeName, entity.getTypeName());
        if (entity.getId() != null) {
            wrapper.ne(DeviceType::getId, entity.getId());
        }
        if (this.count(wrapper) > 0) {
            throw new IllegalArgumentException("设备类型名称已存在：" + entity.getTypeName());
        }
        return super.updateById(entity);
    }
}
