package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai/predict")
public class PredictController {

    @GetMapping("/dimming")
    public Result<List<Map<String, Object>>> getDimmingPrediction() {
        return Result.success(Arrays.asList(
                new LinkedHashMap<String, Object>() {{
                    put("lightId", 1); put("suggestedBrightness", 65); put("confidence", 0.92);
                }},
                new LinkedHashMap<String, Object>() {{
                    put("lightId", 2); put("suggestedBrightness", 70); put("confidence", 0.88);
                }},
                new LinkedHashMap<String, Object>() {{
                    put("lightId", 3); put("suggestedBrightness", 25); put("confidence", 0.95);
                }}
        ));
    }

    @GetMapping("/energy")
    public Result<List<Map<String, Object>>> getEnergyPrediction() {
        return Result.success(Arrays.asList(
                new LinkedHashMap<String, Object>() {{ put("month", "2026-07"); put("predicted", 1100); put("actual", null); }},
                new LinkedHashMap<String, Object>() {{ put("month", "2026-08"); put("predicted", 1250); put("actual", null); }},
                new LinkedHashMap<String, Object>() {{ put("month", "2026-09"); put("predicted", 980); put("actual", null); }}
        ));
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getPredictionHistory() {
        return Result.success(Arrays.asList(
                new LinkedHashMap<String, Object>() {{ put("date", "2026-06-01"); put("predicted", 1050); put("actual", 1020); put("accuracy", 0.97); }},
                new LinkedHashMap<String, Object>() {{ put("date", "2026-06-15"); put("predicted", 1180); put("actual", 1150); put("accuracy", 0.97); }},
                new LinkedHashMap<String, Object>() {{ put("date", "2026-07-01"); put("predicted", 1100); put("actual", 1080); put("accuracy", 0.98); }}
        ));
    }
}
