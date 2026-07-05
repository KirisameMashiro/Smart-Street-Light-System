package com.smartlight.backend.service;

import java.util.List;
import java.util.Map;

/**
 * 碳减排核算服务
 */
public interface CarbonService {

    /**
     * 获取碳减排汇总数据（总节电量、总减排量、节能率）
     */
    Map<String, Object> getSummary();

    /**
     * 获取月度/年度能耗趋势
     */
    List<Map<String, Object>> getTrend(String period);

    /**
     * 获取路段能耗对比
     */
    List<Map<String, Object>> getRoadCompare();

    /**
     * 获取能耗基准配置
     */
    List<Map<String, Object>> getBaseline();

    /**
     * 更新能耗基准配置
     */
    boolean updateBaseline(String key, String value);
}