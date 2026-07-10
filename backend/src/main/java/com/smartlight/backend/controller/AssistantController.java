package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.service.AgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/ai/assistant")
public class AssistantController {

    private final AgentService agentService;

    public AssistantController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * AI Agent 对话（非流式）
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> data) {
        String message = (String) data.getOrDefault("message", "");
        if (message == null || message.isBlank()) {
            return Result.error("消息不能为空");
        }

        String sessionId = (String) data.getOrDefault("sessionId", UUID.randomUUID().toString());
        String reply = agentService.chat(sessionId, message);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("reply", reply);
        result.put("timestamp", new Date().toString());
        return Result.success(result);
    }

    /**
     * AI Agent 对话（SSE 流式）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> data) {
        String message = (String) data.getOrDefault("message", "");
        if (message == null || message.isBlank()) {
            SseEmitter emitter = new SseEmitter(5000L);
            try {
                emitter.send(SseEmitter.event().name("error").data("消息不能为空"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        String sessionId = (String) data.getOrDefault("sessionId", UUID.randomUUID().toString());
        SseEmitter emitter = new SseEmitter(120000L); // 2分钟超时

        agentService.chatStream(sessionId, message, chunk -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("chunk")
                        .data(chunk));
            } catch (IOException e) {
                // 客户端断开连接
            }
        });

        // 工具调用完成后发送结果
        try {
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(Map.of("sessionId", sessionId)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
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
