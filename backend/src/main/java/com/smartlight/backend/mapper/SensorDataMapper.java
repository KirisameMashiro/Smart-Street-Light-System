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

/**
 * 传感器数据 Mapper
 *
 * 基础 CRUD 由 MyBatis-Plus BaseMapper 自动提供，无需在 XML 中配置。
 * 此处只定义 BaseMapper 不支持的专用查询和写入方法。
 */
@Mapper
public interface SensorDataMapper extends BaseMapper<SensorData> {

    /** 按路灯ID查最新一条传感器数据（Redis 降级用） */
    @Select("SELECT * FROM sensor_data WHERE light_id = #{lightId} ORDER BY create_time DESC LIMIT 1")
    SensorData selectLatestByLightId(Long lightId);

    /** 批量 UPSERT 小时聚合数据（XML 实现 ON DUPLICATE KEY UPDATE） */
    void upsertHourlyBatch(@Param("list") List<SensorDataHourlyVO> list);

    /** 从小时聚合表查询每日每灯耗电（碳排放计算用） */
    @Select("SELECT light_id AS lightId, DATE(hour_start) AS statDate, "
            + "SUM(total_energy) / 1000 AS dailyEnergyKwh "
            + "FROM sensor_data_hourly "
            + "WHERE hour_start >= #{start} AND hour_start < #{end} "
            + "GROUP BY light_id, DATE(hour_start) ORDER BY statDate")
    List<Map<String, Object>> selectDailyEnergyByHourly(@Param("start") LocalDateTime start,
                                                        @Param("end") LocalDateTime end);

    /** 从小时聚合表获取所有不重复日期 */
    @Select("SELECT DISTINCT DATE(hour_start) AS statDate FROM sensor_data_hourly ORDER BY statDate")
    List<Map<String, Object>> selectDistinctDatesFromHourly();

    // ==================== 以下为降级/兼容用，不走小时聚合表 ====================

    /** 全表扫描查每日耗电（小时聚合表为空时降级用） */
    @Select("SELECT light_id AS lightId, DATE(collect_time) AS statDate, "
            + "SUM(sampling_energy) / 1000 AS dailyEnergyKwh "
            + "FROM sensor_data WHERE sampling_energy IS NOT NULL AND sampling_energy > 0 "
            + "GROUP BY light_id, DATE(collect_time) ORDER BY statDate")
    List<Map<String, Object>> selectDailyEnergyPerLight();

    /** 全表扫描查所有不重复日期（降级用） */
    @Select("SELECT DISTINCT DATE(collect_time) AS statDate "
            + "FROM sensor_data WHERE sampling_energy IS NOT NULL ORDER BY statDate")
    List<Map<String, Object>> selectDistinctDates();

    /** 查询今日各灯累计耗电（CumulativeEnergyService 启动恢复用） */
    @Select("SELECT light_id AS lightId, SUM(sampling_energy) AS totalEnergy "
            + "FROM sensor_data WHERE sampling_energy IS NOT NULL AND collect_time >= CURDATE() "
            + "GROUP BY light_id")
    List<Map<String, Object>> selectTodayEnergySum();

    /** 从小时聚合表分页查询传感器数据 */
    @Select("<script>"
            + "SELECT light_id AS lightId, hour_start AS collectTime, "
            + "avg_illuminance AS illuminance, avg_power AS power, "
            + "avg_voltage AS voltage, avg_current AS current, "
            + "avg_temperature AS temperature, avg_humidity AS humidity, "
            + "total_energy / 1000 AS samplingEnergy "
            + "FROM sensor_data_hourly "
            + "<where>"
            + "<if test=\"lightId != null\"> AND light_id = #{lightId} </if>"
            + "<if test=\"startTime != null\"> AND hour_start >= #{startTime} </if>"
            + "<if test=\"endTime != null\"> AND hour_start &lt;= #{endTime} </if>"
            + "</where>"
            + "ORDER BY hour_start DESC"
            + "</script>")
    List<Map<String, Object>> selectFromHourlyPage(@Param("lightId") Long lightId,
                                                    @Param("startTime") String startTime,
                                                    @Param("endTime") String endTime);
}
