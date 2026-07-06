package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.CarbonStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface CarbonStatsMapper extends BaseMapper<CarbonStats> {

    /**
     * 获取总节电、总减排、平均节能率（汇总所有路段）
     */
    @Select("SELECT COALESCE(SUM(saved_energy), 0) AS totalSaved, " +
            "COALESCE(SUM(co2_reduction), 0) AS totalCO2, " +
            "COALESCE(AVG(saving_rate), 0) AS avgRate " +
            "FROM carbon_stats WHERE road IS NULL")
    Map<String, Object> selectSummary();

    /**
     * 获取指定日期范围内每日的统计数据（用于月度每日趋势）
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    @Select("SELECT stat_date AS statDate, " +
            "SUM(saved_energy) AS savedEnergy, " +
            "SUM(co2_reduction) AS co2Reduction, " +
            "SUM(actual_energy) AS actualEnergy, " +
            "AVG(saving_rate) AS savingRate " +
            "FROM carbon_stats WHERE road IS NULL " +
            "AND stat_date BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY stat_date ORDER BY stat_date")
    List<Map<String, Object>> selectDailyStats(@Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * 获取指定年份每月汇总数据（用于年度每月趋势）
     */
    @Select("SELECT DATE_FORMAT(stat_date, '%Y-%m') AS month, " +
            "SUM(saved_energy) AS savedEnergy, " +
            "SUM(co2_reduction) AS co2Reduction, " +
            "SUM(actual_energy) AS actualEnergy, " +
            "AVG(saving_rate) AS savingRate " +
            "FROM carbon_stats WHERE road IS NULL " +
            "AND YEAR(stat_date) = #{year} " +
            "GROUP BY DATE_FORMAT(stat_date, '%Y-%m') ORDER BY month")
    List<Map<String, Object>> selectYearlyMonthlyStats(@Param("year") int year);

    /**
     * 按月统计全部汇总数据（用于兼容旧参数）
     */
    @Select("SELECT DATE_FORMAT(stat_date, '%Y-%m') AS month, " +
            "SUM(saved_energy) AS savedEnergy, " +
            "SUM(co2_reduction) AS co2Reduction, " +
            "SUM(actual_energy) AS actualEnergy, " +
            "AVG(saving_rate) AS savingRate " +
            "FROM carbon_stats WHERE road IS NULL " +
            "GROUP BY DATE_FORMAT(stat_date, '%Y-%m') ORDER BY month")
    List<Map<String, Object>> selectMonthlyStats();

    /**
     * 按路段汇总
     */
    @Select("SELECT road, " +
            "SUM(saved_energy) AS savedEnergy, " +
            "SUM(co2_reduction) AS co2Reduction, " +
            "SUM(actual_energy) AS actualEnergy, " +
            "AVG(saving_rate) AS savingRate, " +
            "MAX(light_count) AS lightCount, " +
            "SUM(baseline_energy) AS baselineEnergy " +
            "FROM carbon_stats WHERE road IS NOT NULL " +
            "GROUP BY road")
    List<Map<String, Object>> selectStatsByRoad();

    /**
     * 检查某天某路段是否已有统计数据
     */
    @Select("SELECT COUNT(*) FROM carbon_stats WHERE stat_date = #{date} AND ((road IS NULL AND #{road} IS NULL) OR road = #{road})")
    int countByDateAndRoad(LocalDate date, String road);
}