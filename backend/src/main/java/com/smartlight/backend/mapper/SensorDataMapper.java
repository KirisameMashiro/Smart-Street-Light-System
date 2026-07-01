package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.SensorData;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensorDataMapper extends BaseMapper<SensorData> {
}