package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.SensorData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SensorDataMapper extends BaseMapper<SensorData> {

    /**
     * 根据路灯ID查询最新一条传感器数据
     */
    @Select("SELECT * FROM sensor_data WHERE light_id = #{lightId} ORDER BY create_time DESC LIMIT 1")
    SensorData selectLatestByLightId(Long lightId);

    /**
     * 按日期统计每个路灯的日耗电总量 (Wh → kWh)
     * 用于 carbon_stats 表的数据聚合
     */
    @Select("SELECT light_id AS lightId, DATE(collect_time) AS statDate, SUM(sampling_energy) / 1000 AS dailyEnergyKwh " +
            "FROM sensor_data WHERE sampling_energy IS NOT NULL AND sampling_energy > 0 " +
            "GROUP BY light_id, DATE(collect_time) ORDER BY statDate")
    List<Map<String, Object>> selectDailyEnergyPerLight();

    /**
     * 获取有采样数据的所有不重复日期
     */
    @Select("SELECT DISTINCT DATE(collect_time) AS statDate FROM sensor_data WHERE sampling_energy IS NOT NULL ORDER BY statDate")
    List<Map<String, Object>> selectDistinctDates();

    /**
     * 获取各路段的路灯数量
     */
    @Select("SELECT road, COUNT(*) AS lightCount FROM light WHERE road IS NOT NULL GROUP BY road")
    List<Map<String, Object>> selectLightCountByRoad();

    /**
     * 获取传感器数据的时间跨度（天数）
     */
    @Select("SELECT DATEDIFF(MAX(collect_time), MIN(collect_time)) + 1 AS days FROM sensor_data")
    Map<String, Object> selectDataSpanDays();

    /**
     * 查询今天所有路灯的累计耗电 (Wh)
     */
    @Select("SELECT light_id AS lightId, SUM(sampling_energy) AS totalEnergy " +
            "FROM sensor_data WHERE sampling_energy IS NOT NULL AND collect_time >= CURDATE() " +
            "GROUP BY light_id")
    List<Map<String, Object>> selectTodayEnergySum();
}