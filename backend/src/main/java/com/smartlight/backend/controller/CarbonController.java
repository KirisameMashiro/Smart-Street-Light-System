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
     * 能耗趋势（日度/月度/年度）
     * @param type 时间粒度：daily-日度 / monthly-月度 / yearly-年度
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrend(@RequestParam(defaultValue = "monthly") String type) {
        return Result.success(carbonService.getTrend(type));
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
    public Result<Map<String, Object>> getBaseline() {
        return Result.success(carbonService.getBaseline());
    }

    /**
     * 更新能耗基准配置
     */
    @PutMapping("/baseline")
    public Result<Boolean> updateBaseline(@RequestBody Map<String, Object> data) {
        if (data != null) {
            Object basePower = data.get("basePower");
            Object dailyHours = data.get("dailyHours");
            Object emissionFactor = data.get("emissionFactor");
            if (basePower != null) {
                carbonService.updateBaseline("energy_baseline_power", String.valueOf(basePower));
            }
            if (dailyHours != null) {
                carbonService.updateBaseline("energy_baseline_hours", String.valueOf(dailyHours));
            }
            if (emissionFactor != null) {
                carbonService.updateBaseline("co2_factor", String.valueOf(emissionFactor));
            }
        }
        return Result.success(true);
    }

    /**
     * 手动触发碳减排日统计计算
     * @param date 统计日期（yyyy-MM-dd），为空则计算昨天
     * @return 写入的记录数
     */
    @PostMapping("/compute")
    public Result<Integer> computeDailyStats(@RequestParam(required = false) String date) {
        int count = carbonService.computeDailyStats(date);
        String msg = date != null ? date : "昨天";
        return Result.success(msg + " 碳减排统计完成，共写入 " + count + " 条记录", count);
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
        report.append(String.format("总节电量: %.1f kWh\n", summary.get("savedEnergy")));
        report.append(String.format("总减排量: %.1f kg CO₂\n", summary.get("reducedCo2")));
        report.append(String.format("节能率: %.1f%%\n\n", summary.get("energySavingRate")));

        report.append("月度趋势:\n");
        for (Map<String, Object> t : trend) {
            report.append(String.format("  %s: 节电 %.1f kWh, 减排 %.1f kg\n",
                    t.get("month"), t.get("savedEnergy"), t.get("reducedCo2")));
        }

        report.append("\n路段对比:\n");
        for (Map<String, Object> r : roadCompare) {
            report.append(String.format("  %s: 节电 %.1f kWh\n",
                    r.get("road"), r.get("savedEnergy")));
        }

        return Result.success(report.toString());
    }
}