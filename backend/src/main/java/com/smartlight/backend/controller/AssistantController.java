package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.service.AgentService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ai/assistant")
public class AssistantController {

    private final AgentService agentService;

    public AssistantController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * AI Agent 对话
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> data) {
        String message = (String) data.getOrDefault("message", "");
        if (message == null || message.isBlank()) {
            return Result.error("消息不能为空");
        }

        // 调用 AI Agent
        String reply = agentService.chat(message);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("timestamp", new Date().toString());
        return Result.success(result);
    }

    /**
     * 对话历史（暂无持久化，返回空列表）
     */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(@RequestParam(required = false) String sessionId) {
        return Result.success(Collections.emptyList());
    }
}
