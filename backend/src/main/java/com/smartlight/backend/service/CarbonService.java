package com.smartlight.backend.service;

import java.util.List;
import java.util.Map;

/**
 * 碳减排核算服务
 * 数据来源于 carbon_stats 预统计表
 */
public interface CarbonService {

    /**
     * 获取碳减排汇总数据（总节电量、总减排量、节能率）
     * @param type month-月度每日, year-年度每月
     * @param period 月份(2026-07)或年份(2026)
     */
    Map<String, Object> getSummary(String type, String period);

    /**
     * 获取能耗趋势
     * @param type month-月度每日, year-年度每月
     * @param period 月份(2026-07)或年份(2026)
     */
    List<Map<String, Object>> getTrend(String type, String period);

    /**
     * 获取路段能耗对比
     * @param type month-月度每日, year-年度每月
     * @param period 月份(2026-07)或年份(2026)
     */
    List<Map<String, Object>> getRoadCompare(String type, String period);

    /**
     * 获取能耗基准配置（前端需要的扁平对象：{basePower, dailyHours, emissionFactor}）
     */
    Map<String, Object> getBaseline();

    /**
     * 更新能耗基准配置
     */
    boolean updateBaseline(String key, String value);

    /**
     * 计算并写入指定日期的碳减排统计数据
     * @param date 统计日期，null 则计算所有有数据的天
     * @return 写入的记录数
     */
    int computeDailyStats(String date);
}