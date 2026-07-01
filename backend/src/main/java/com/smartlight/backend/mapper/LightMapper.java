package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.Light;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LightMapper extends BaseMapper<Light> {
}