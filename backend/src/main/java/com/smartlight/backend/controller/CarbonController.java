package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/carbon")
public class CarbonController {

    @GetMapping("/summary")
    public Result<Map<String, Object>> getSummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalSavedPower", 12580.5);
        map.put("totalReducedCO2", 12542.3);
        map.put("energySavingRate", 32.5);
        return Result.success(map);
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrend(@RequestParam(required = false) String period) {
        List<Map<String, Object>> list = new ArrayList<>();
        String[] months = {"2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06"};
        double[] values = {850, 920, 1050, 1180, 1250, 1320};
        for (int i = 0; i < months.length; i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", months[i]);
            m.put("value", values[i]);
            list.add(m);
        }
        return Result.success(list);
    }

    @GetMapping("/road-compare")
    public Result<List<Map<String, Object>>> getRoadCompare() {
        return Result.success(Arrays.asList(
                new LinkedHashMap<String, Object>() {{ put("road", "人民路"); put("before", 1200); put("after", 820); }},
                new LinkedHashMap<String, Object>() {{ put("road", "建设路"); put("before", 980); put("after", 650); }},
                new LinkedHashMap<String, Object>() {{ put("road", "解放路"); put("before", 750); put("after", 510); }}
        ));
    }

    @GetMapping("/baseline")
    public Result<List<Map<String, Object>>> getBaseline() {
        return Result.success(Arrays.asList(
                new LinkedHashMap<String, Object>() {{
                    put("configKey", "energy_baseline_power");
                    put("configValue", "250");
                    put("description", "传统钠灯基准功率(W)");
                }},
                new LinkedHashMap<String, Object>() {{
                    put("configKey", "energy_baseline_hours");
                    put("configValue", "12");
                    put("description", "日均照明时长(h)");
                }},
                new LinkedHashMap<String, Object>() {{
                    put("configKey", "co2_factor");
                    put("configValue", "0.997");
                    put("description", "碳排放因子(kg CO₂/kWh)");
                }}
        ));
    }

    @PutMapping("/baseline")
    public Result<Boolean> updateBaseline(@RequestBody List<Map<String, String>> configs) {
        return Result.success(true);
    }

    @GetMapping("/export")
    public Result<String> exportReport(@RequestParam(required = false) String period) {
        return Result.success("导出功能开发中");
    }
}
