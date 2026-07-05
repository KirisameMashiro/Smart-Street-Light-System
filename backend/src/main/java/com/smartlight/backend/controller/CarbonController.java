package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.service.CarbonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 碳减排核算与可视化 API
 */
@RestController
@RequestMapping("/api/carbon")
@RequiredArgsConstructor
public class CarbonController {

    private final CarbonService carbonService;

    /**
     * 核心指标：总节电量、总减排量、节能率
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> getSummary() {
        return Result.success(carbonService.getSummary());
    }

    /**
     * 月度/年度能耗趋势
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrend(@RequestParam(defaultValue = "monthly") String period) {
        return Result.success(carbonService.getTrend(period));
    }

    /**
     * 路段能耗对比
     */
    @GetMapping("/road-compare")
    public Result<List<Map<String, Object>>> getRoadCompare() {
        return Result.success(carbonService.getRoadCompare());
    }

    /**
     * 获取能耗基准配置
     */
    @GetMapping("/baseline")
    public Result<List<Map<String, Object>>> getBaseline() {
        return Result.success(carbonService.getBaseline());
    }

    /**
     * 更新能耗基准配置
     */
    @PutMapping("/baseline")
    public Result<Boolean> updateBaseline(@RequestBody List<Map<String, String>> configs) {
        if (configs != null) {
            for (Map<String, String> config : configs) {
                String key = config.get("configKey");
                String value = config.get("configValue");
                if (key != null && value != null) {
                    carbonService.updateBaseline(key, value);
                }
            }
        }
        return Result.success(true);
    }

    /**
     * Excel 报表导出（预留）
     */
    @GetMapping("/export")
    public Result<String> exportReport(@RequestParam(required = false) String period) {
        Map<String, Object> summary = carbonService.getSummary();
        List<Map<String, Object>> trend = carbonService.getTrend("monthly");
        List<Map<String, Object>> roadCompare = carbonService.getRoadCompare();

        // 构建纯文本报表摘要
        StringBuilder report = new StringBuilder();
        report.append("=== 碳减排分析报表 ===\n\n");
        report.append(String.format("总节电量: %.1f kWh\n", summary.get("totalSavedPower")));
        report.append(String.format("总减排量: %.1f kg CO₂\n", summary.get("totalReducedCO2")));
        report.append(String.format("节能率: %.1f%%\n\n", summary.get("energySavingRate")));

        report.append("月度趋势:\n");
        for (Map<String, Object> t : trend) {
            report.append(String.format("  %s: 节电 %.1f kWh, 减排 %.1f kg\n",
                    t.get("month"), t.get("savedEnergy"), t.get("reducedCO2")));
        }

        report.append("\n路段对比:\n");
        for (Map<String, Object> r : roadCompare) {
            report.append(String.format("  %s: 改造前 %.1f kWh → 改造后 %.1f kWh\n",
                    r.get("road"), r.get("before"), r.get("after")));
        }

        return Result.success(report.toString());
    }
}