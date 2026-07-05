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
     * 获取每个路灯的最新总能耗
     */
    @Select("SELECT light_id AS lightId, MAX(total_energy) AS totalEnergy FROM sensor_data WHERE total_energy IS NOT NULL GROUP BY light_id")
    List<Map<String, Object>> selectLatestEnergyPerLight();

    /**
     * 按路灯分组取第一条和最后一条 totalEnergy 计算每月净耗电
     */
    @Select("SELECT t.month, SUM(t.energyDelta) AS totalEnergy FROM (" +
            "SELECT light_id, DATE_FORMAT(collect_time, '%Y-%m') AS month, " +
            "(MAX(total_energy) - MIN(total_energy)) AS energyDelta " +
            "FROM sensor_data WHERE total_energy IS NOT NULL " +
            "GROUP BY light_id, DATE_FORMAT(collect_time, '%Y-%m')" +
            ") t GROUP BY t.month ORDER BY t.month")
    List<Map<String, Object>> selectMonthlyEnergy();

    /**
     * 按路段统计净耗电（每路灯最新totalEnergy - 最初totalEnergy）
     */
    @Select("SELECT l.road, SUM(e.energyDelta) AS totalEnergy FROM (" +
            "SELECT light_id, MAX(total_energy) - MIN(total_energy) AS energyDelta " +
            "FROM sensor_data WHERE total_energy IS NOT NULL GROUP BY light_id" +
            ") e JOIN light l ON e.light_id = l.id " +
            "WHERE l.road IS NOT NULL GROUP BY l.road")
    List<Map<String, Object>> selectEnergyByRoad();

    /**
     * 获取各路段的路灯数量
     */
    @Select("SELECT road, COUNT(*) AS lightCount FROM light WHERE road IS NOT NULL GROUP BY road")
    List<Map<String, Object>> selectLightCountByRoad();

    /**
     * 计算总耗电量（每个路灯 max - min 之差的和 = 实际消耗量）
     */
    @Select("SELECT COALESCE(SUM(energyDelta), 0) AS totalEnergy FROM (" +
            "SELECT MAX(total_energy) - MIN(total_energy) AS energyDelta " +
            "FROM sensor_data WHERE total_energy IS NOT NULL GROUP BY light_id" +
            ") t")
    Map<String, Object> selectTotalEnergy();

    /**
     * 获取传感器数据的时间跨度（天数）
     */
    @Select("SELECT DATEDIFF(MAX(collect_time), MIN(collect_time)) + 1 AS days FROM sensor_data")
    Map<String, Object> selectDataSpanDays();
}