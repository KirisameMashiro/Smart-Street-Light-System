package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai/assistant")
public class AssistantController {

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> data) {
        String message = (String) data.getOrDefault("message", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", "您好！我是智慧路灯AI运维助手。\n\n我可以帮您：\n1. 查询路灯设备状态\n2. 分析能耗数据\n3. 诊断设备故障\n\n请问有什么可以帮您的？");
        result.put("timestamp", new Date().toString());
        return Result.success(result);
    }

    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(@RequestParam(required = false) String sessionId) {
        return Result.success(Arrays.asList(
                new LinkedHashMap<String, Object>() {{ put("sessionId", "s1"); put("title", "能耗分析咨询"); put("lastTime", "2026-07-01 10:30"); }},
                new LinkedHashMap<String, Object>() {{ put("sessionId", "s2"); put("title", "设备故障排查"); put("lastTime", "2026-06-30 14:20"); }}
        ));
    }
}
