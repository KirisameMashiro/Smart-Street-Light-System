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

        // sessionId：前端传入则复用，不传则自动生成
        String sessionId = (String) data.getOrDefault("sessionId", UUID.randomUUID().toString());

        // 调用 AI Agent
        String reply = agentService.chat(sessionId, message);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("reply", reply);
        result.put("timestamp", new Date().toString());
        return Result.success(result);
    }

    /**
     * 对话历史
     */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> getHistory(@RequestParam String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Result.error("sessionId 不能为空");
        }
        return Result.success(agentService.getHistory(sessionId));
    }

    /**
     * 清除会话
     */
    @PostMapping("/clear")
    public Result<String> clear(@RequestBody Map<String, Object> data) {
        String sessionId = (String) data.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return Result.error("sessionId 不能为空");
        }
        agentService.clearSession(sessionId);
        return Result.success("会话已清除");
    }
}
