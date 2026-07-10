package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.entity.SensorDataHourlyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SensorDataMapper extends BaseMapper<SensorData> {

    /** 根据路灯ID查询最新一条传感器数据 */
    @Select("SELECT * FROM sensor_data WHERE light_id = #{lightId} ORDER BY create_time DESC LIMIT 1")
    SensorData selectLatestByLightId(Long lightId);

    /** 按日期统计每个路灯的日耗电总量 (Wh → kWh)，全表扫描版（改用 hourly 版） */
    @Select("SELECT light_id AS lightId, DATE(collect_time) AS statDate, SUM(sampling_energy) / 1000 AS dailyEnergyKwh "
            + "FROM sensor_data WHERE sampling_energy IS NOT NULL AND sampling_energy > 0 "
            + "GROUP BY light_id, DATE(collect_time) ORDER BY statDate")
    List<Map<String, Object>> selectDailyEnergyPerLight();

    /** 获取有采样数据的所有不重复日期，全表扫描版（改用 hourly 版） */
    @Select("SELECT DISTINCT DATE(collect_time) AS statDate FROM sensor_data WHERE sampling_energy IS NOT NULL ORDER BY statDate")
    List<Map<String, Object>> selectDistinctDates();

    /** 获取各路段的路灯数量 */
    @Select("SELECT road, COUNT(*) AS lightCount FROM light WHERE road IS NOT NULL GROUP BY road")
    List<Map<String, Object>> selectLightCountByRoad();

    /** 获取传感器数据的时间跨度（天数） */
    @Select("SELECT DATEDIFF(MAX(collect_time), MIN(collect_time)) + 1 AS days FROM sensor_data")
    Map<String, Object> selectDataSpanDays();

    /** 查询今天所有路灯的累计耗电 (Wh) */
    @Select("SELECT light_id AS lightId, SUM(sampling_energy) AS totalEnergy "
            + "FROM sensor_data WHERE sampling_energy IS NOT NULL AND collect_time >= CURDATE() "
            + "GROUP BY light_id")
    List<Map<String, Object>> selectTodayEnergySum();

    // ==================== 批量写入（在 XML 中实现） ====================

    /** 批量插入传感器数据（无主键回填） */
    Long insertBatch(@Param("list") List<SensorData> list);

    /** 批量 Upsert 小时级聚合数据 */
    void upsertHourlyBatch(@Param("list") List<SensorDataHourlyVO> list);

    // ==================== 小时聚合查询（替代全表扫描） ====================

    /** 从小时聚合表查询每天每灯累计耗电（替代 selectDailyEnergyPerLight 全表扫描） */
    @Select("SELECT light_id AS lightId, DATE(hour_start) AS statDate, "
            + "SUM(total_energy) / 1000 AS dailyEnergyKwh "
            + "FROM sensor_data_hourly "
            + "WHERE hour_start >= #{start} AND hour_start < #{end} "
            + "GROUP BY light_id, DATE(hour_start) ORDER BY statDate")
    List<Map<String, Object>> selectDailyEnergyByHourly(@Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    /** 从小时聚合表获取所有不重复日期（替代从 sensor_data 查 DISTINCT） */
    @Select("SELECT DISTINCT DATE(hour_start) AS statDate FROM sensor_data_hourly ORDER BY statDate")
    List<Map<String, Object>> selectDistinctDatesFromHourly();

    // ==================== 数据保留策略（在 XML 中实现） ====================

    /** 删除早于指定时间的原始传感器数据 */
    int deleteByCollectTimeBefore(@Param("cutoff") LocalDateTime cutoff);

    /** 获取 sensor_data 表中最老的 collect_time 日期 */
    @Select("SELECT DATE(MIN(collect_time)) AS oldestDate FROM sensor_data")
    Map<String, Object> selectOldestDate();
}