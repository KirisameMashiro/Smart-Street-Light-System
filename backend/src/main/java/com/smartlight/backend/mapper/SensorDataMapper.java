package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.SensorData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SensorDataMapper extends BaseMapper<SensorData> {

    /**
     * 根据路灯ID查询最新一条传感器数据
     */
    @Select("SELECT * FROM sensor_data WHERE light_id = #{lightId} ORDER BY create_time DESC LIMIT 1")
    SensorData selectLatestByLightId(Long lightId);
}