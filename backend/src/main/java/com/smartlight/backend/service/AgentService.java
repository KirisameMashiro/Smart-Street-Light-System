package com.smartlight.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartlight.backend.config.AIConfig;
import com.smartlight.backend.entity.Knowledge;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Agent 核心服务
 * 通过 OpenAI 兼容协议调用大模型，支持 Function Calling 操控系统
 */
@Slf4j
@Service
public class AgentService {

    private final AIConfig aiConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final LightService lightService;
    private final SensorDataService sensorDataService;
    private final AlertService alertService;
    private final KnowledgeService knowledgeService;
    private final List<Map<String, Object>> tools;

    public AgentService(AIConfig aiConfig, RestTemplate restTemplate, ObjectMapper objectMapper,
                        LightService lightService, SensorDataService sensorDataService,
                        AlertService alertService, KnowledgeService knowledgeService) {
        this.aiConfig = aiConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.lightService = lightService;
        this.sensorDataService = sensorDataService;
        this.alertService = alertService;
        this.knowledgeService = knowledgeService;
        this.tools = buildToolDefinitions();
    }

    // ==================== 工具定义 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> list = new ArrayList<>();

        // === 1. 查询路灯状态 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "query_lights",
                        "description", "查询路灯设备状态。可按行政区、路段、状态筛选。不传参数返回所有路灯。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "district", Map.of("type", "string", "description", "行政区名称，如：黄浦区、浦东新区"),
                                        "road", Map.of("type", "string", "description", "路段名称，如：人民路、南京路"),
                                        "status", Map.of("type", "integer", "description", "状态: 0=关闭, 1=开启, 2=故障")
                                ),
                                "required", Collections.emptyList()
                        )
                )
        ));

        // === 2. 开关灯 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "control_light",
                        "description", "开关单个路灯。需要路灯ID和操作类型。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "lightId", Map.of("type", "integer", "description", "路灯ID"),
                                        "action", Map.of("type", "string", "description", "操作: on=开启, off=关闭")
                                ),
                                "required", List.of("lightId", "action")
                        )
                )
        ));

        // === 3. 批量开关灯 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "batch_control_lights",
                        "description", "按行政区或路段批量开关路灯。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "district", Map.of("type", "string", "description", "行政区名称"),
                                        "road", Map.of("type", "string", "description", "路段名称"),
                                        "action", Map.of("type", "string", "description", "操作: on=开启, off=关闭")
                                ),
                                "required", List.of("action")
                        )
                )
        ));

        // === 4. 调节亮度 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "set_brightness",
                        "description", "设置路灯亮度百分比(0-100)。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "lightId", Map.of("type", "integer", "description", "路灯ID"),
                                        "brightness", Map.of("type", "integer", "description", "亮度百分比，0-100")
                                ),
                                "required", List.of("lightId", "brightness")
                        )
                )
        ));

        // === 5. 查询传感器数据 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "query_sensor_data",
                        "description", "查询路灯最新传感器数据：照度、功率、电压、电流、温度、湿度。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "lightId", Map.of("type", "integer", "description", "路灯ID，不传则查询所有")
                                ),
                                "required", Collections.emptyList()
                        )
                )
        ));

        // === 6. 查询报警 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "query_alerts",
                        "description", "查询未处理的报警信息。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "level", Map.of("type", "integer", "description", "报警级别: 1=提示, 2=一般, 3=严重, 4=紧急")
                                ),
                                "required", Collections.emptyList()
                        )
                )
        ));

        // === 7. 查询知识库 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "search_knowledge",
                        "description", "搜索系统知识库，获取运维手册、故障排查指南、设备参数、操作指引等。支持按关键词搜索和按分类筛选。分类可选值：故障排查、维护规范、设备参数、操作指引。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "keyword", Map.of("type", "string", "description", "搜索关键词，如：故障排查、LED、维护、设备参数"),
                                        "category", Map.of("type", "string", "description", "知识分类筛选，可选值：故障排查、维护规范、设备参数、操作指引")
                                ),
                                "required", List.of("keyword")
                        )
                )
        ));

        // === 8. 获取系统概览 ===
        list.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_system_overview",
                        "description", "获取系统整体状态概览：在线/离线/故障路灯数、未处理报警数。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Collections.emptyMap()
                        )
                )
        ));

        return list;
    }

    // ==================== 工具实现 ====================

    private String executeTool(String toolName, Map<String, Object> args) {
        try {
            return switch (toolName) {
                case "query_lights" -> queryLights(args);
                case "control_light" -> controlLight(args);
                case "batch_control_lights" -> batchControlLights(args);
                case "set_brightness" -> setBrightness(args);
                case "query_sensor_data" -> querySensorData(args);
                case "query_alerts" -> queryAlerts(args);
                case "search_knowledge" -> searchKnowledge(args);
                case "get_system_overview" -> getSystemOverview();
                default -> "未知工具: " + toolName;
            };
        } catch (Exception e) {
            log.error("工具执行失败: {} - {}", toolName, e.getMessage());
            return "执行失败: " + e.getMessage();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> args, String key) {
        return (Map<String, Object>) args.get(key);
    }

    private Object getArg(Map<String, Object> args, String key) {
        return args.get(key);
    }

    private String queryLights(Map<String, Object> args) throws JsonProcessingException {
        String district = args.get("district") != null ? args.get("district").toString() : null;
        String road = args.get("road") != null ? args.get("road").toString() : null;
        Object statusObj = args.get("status");
        Integer status = null;
        if (statusObj instanceof Number n) status = n.intValue();

        var page = lightService.getPage(1, 50, null, status, district, road, null);
        List<Map<String, Object>> list = page.getRecords().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("name", l.getLightName());
            m.put("code", l.getLightCode());
            m.put("district", l.getDistrict());
            m.put("road", l.getRoad());
            m.put("status", l.getStatus() == 1 ? "开启" : (l.getStatus() == 2 ? "故障" : "关闭"));
            m.put("brightness", l.getBrightness());
            m.put("type", l.getDeviceType());
            m.put("ratedPower", l.getRatedPower());
            return m;
        }).collect(Collectors.toList());

        return objectMapper.writeValueAsString(Map.of(
                "total", list.size(),
                "lights", list
        ));
    }

    private String controlLight(Map<String, Object> args) throws JsonProcessingException {
        Long lightId = ((Number) args.get("lightId")).longValue();
        String action = args.get("action").toString();
        Integer status = "on".equalsIgnoreCase(action) ? 1 : 0;

        Light light = lightService.getById(lightId);
        if (light == null) {
            return objectMapper.writeValueAsString(Map.of("success", false, "message", "路灯不存在: " + lightId));
        }

        lightService.batchSwitchStatus(List.of(lightId), status);
        String msg = "on".equalsIgnoreCase(action) ? "已开启" : "已关闭";
        return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "message", light.getLightName() + "(" + light.getDistrict() + light.getRoad() + ") " + msg
        ));
    }

    private String batchControlLights(Map<String, Object> args) throws JsonProcessingException {
        String district = args.get("district") != null ? args.get("district").toString() : null;
        String road = args.get("road") != null ? args.get("road").toString() : null;
        String action = args.get("action").toString();
        Integer status = "on".equalsIgnoreCase(action) ? 1 : 0;

        var page = lightService.getPage(1, 200, null, null, district, road, null);
        List<Long> ids = page.getRecords().stream().map(Light::getId).collect(Collectors.toList());

        if (ids.isEmpty()) {
            String location = (district != null ? district : "") + (road != null ? road : "");
            return objectMapper.writeValueAsString(Map.of("success", false, "message", "未找到匹配的路灯: " + location));
        }

        lightService.batchSwitchStatus(ids, status);
        String msg = "on".equalsIgnoreCase(action) ? "已开启" : "已关闭";
        return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "message", "共" + ids.size() + "盏路灯" + msg
        ));
    }

    private String setBrightness(Map<String, Object> args) throws JsonProcessingException {
        Long lightId = ((Number) args.get("lightId")).longValue();
        Integer brightness = ((Number) args.get("brightness")).intValue();

        Light light = lightService.getById(lightId);
        if (light == null) {
            return objectMapper.writeValueAsString(Map.of("success", false, "message", "路灯不存在: " + lightId));
        }

        brightness = Math.max(0, Math.min(100, brightness));
        lightService.setBrightness(lightId, brightness);
        return objectMapper.writeValueAsString(Map.of(
                "success", true,
                "message", light.getLightName() + " 亮度已设置为 " + brightness + "%"
        ));
    }

    private String querySensorData(Map<String, Object> args) throws JsonProcessingException {
        List<Map<String, Object>> list = new ArrayList<>();
        if (args.get("lightId") != null) {
            Long lightId = ((Number) args.get("lightId")).longValue();
            SensorData data = sensorDataService.getLatestByLightId(lightId);
            if (data != null) {
                list.add(sensorDataToMap(data));
            }
        } else {
            for (long i = 1; i <= 30; i++) {
                SensorData data = sensorDataService.getLatestByLightId(i);
                if (data != null) list.add(sensorDataToMap(data));
            }
        }
        return objectMapper.writeValueAsString(Map.of("total", list.size(), "data", list));
    }

    private Map<String, Object> sensorDataToMap(SensorData d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lightId", d.getLightId());
        m.put("illuminance", d.getIlluminance());
        m.put("power", d.getPower());
        m.put("voltage", d.getVoltage());
        m.put("current", d.getCurrent());
        m.put("temperature", d.getTemperature());
        m.put("humidity", d.getHumidity());
        m.put("time", d.getCollectTime() != null ? d.getCollectTime().toString() : null);
        return m;
    }

    private String queryAlerts(Map<String, Object> args) throws JsonProcessingException {
        Integer level = null;
        if (args.get("level") instanceof Number n) level = n.intValue();
        var page = alertService.getPage(1, 20, null, null, level, 0);
        List<Map<String, Object>> list = page.getRecords().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("lightId", a.getLightId());
            m.put("type", a.getAlertType());
            m.put("level", a.getAlertLevel());
            m.put("message", a.getMessage());
            m.put("time", a.getCreateTime() != null ? a.getCreateTime().toString() : null);
            return m;
        }).collect(Collectors.toList());
        return objectMapper.writeValueAsString(Map.of("total", list.size(), "alerts", list));
    }

    private String searchKnowledge(Map<String, Object> args) throws JsonProcessingException {
        String keyword = args.get("keyword").toString();
        String category = args.get("category") != null ? args.get("category").toString() : null;

        var page = knowledgeService.getPage(1, 10, category, keyword);
        List<Map<String, Object>> list = page.getRecords().stream()
                .map(k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", k.getId());
                    m.put("title", k.getTitle());
                    m.put("category", k.getCategory());
                    m.put("keywords", k.getKeywords());
                    String content = k.getContent();
                    m.put("content", content != null && content.length() > 500
                            ? content.substring(0, 500) + "..." : content);
                    return m;
                }).collect(Collectors.toList());
        return objectMapper.writeValueAsString(Map.of("total", list.size(), "results", list));
    }

    private String getSystemOverview() throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "onlineCount", lightService.countByStatus(1),
                "offlineCount", lightService.countByStatus(0),
                "faultCount", lightService.countByStatus(2),
                "unhandledAlertCount", alertService.countUnhandled()
        ));
    }

    // ==================== 会话管理 ====================

    /** 会话存储：sessionId -> 消息历史 */
    private final Map<String, LinkedList<Map<String, Object>>> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    /** 每会话最多保留消息条数（不含 system prompt） */
    private static final int MAX_HISTORY_SIZE = 20;
    /** 会话过期时间（毫秒），30分钟无活动自动清理 */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000;

    /** 获取或创建会话消息列表 */
    private LinkedList<Map<String, Object>> getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new LinkedList<>());
    }

    /** 获取会话历史（供 Controller 查询） */
    public List<Map<String, Object>> getHistory(String sessionId) {
        LinkedList<Map<String, Object>> session = sessions.get(sessionId);
        if (session == null) return Collections.emptyList();
        return new ArrayList<>(session);
    }

    /** 清除指定会话 */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    // ==================== 核心对话流程 ====================

    /**
     * 发送消息给 AI Agent，支持 Function Calling 和会话上下文
     */
    public String chat(String sessionId, String userMessage) {
        // 获取或创建会话历史
        LinkedList<Map<String, Object>> history = getOrCreateSession(sessionId);

        // 构建完整消息列表：system + 历史 + 当前用户消息
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", aiConfig.getSystemPrompt()));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        // 用户消息加入历史
        Map<String, Object> userMsg = Map.of("role", "user", "content", userMessage);
        history.add(userMsg);
        trimHistory(history);

        // 最多允许 5 轮工具调用
        for (int round = 0; round < 5; round++) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", aiConfig.getModel());
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("tools", tools);
            body.put("tool_choice", "auto");

            Map<String, Object> response;
            try {
                String url = aiConfig.getBaseUrl() + "/chat/completions";
                var headers = new org.springframework.http.HttpHeaders();
                headers.set("Authorization", "Bearer " + aiConfig.getApiKey());
                headers.set("Content-Type", "application/json");
                var entity = new org.springframework.http.HttpEntity<>(body, headers);
                response = restTemplate.postForObject(url, entity, Map.class);
            } catch (Exception e) {
                log.error("AI API 调用失败", e);
                return "抱歉，AI 服务暂时不可用，请稍后重试。错误: " + e.getMessage();
            }

            if (response == null) {
                return "抱歉，未收到 AI 响应。";
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return "抱歉，AI 返回为空。";
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");

            // 检查是否有工具调用
            Object toolCallsObj = message.get("tool_calls");
            if (toolCallsObj == null) {
                // 纯文本回复
                Object content = message.get("content");
                String reply = content != null ? content.toString() : "AI 未返回有效内容。";
                // 回复加入历史
                history.add(Map.of("role", "assistant", "content", reply));
                trimHistory(history);
                return reply;
            }

            // 有工具调用需要执行
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) toolCallsObj;
            if (toolCalls.isEmpty()) {
                Object content = message.get("content");
                return content != null ? content.toString() : "";
            }

            // 将 assistant 消息加入对话
            messages.add(message);

            // 执行每个工具调用
            for (Map<String, Object> tc : toolCalls) {
                String callId = tc.get("id").toString();
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                String toolName = func.get("name").toString();
                String argsStr = func.get("arguments").toString();

                Map<String, Object> toolArgs;
                try {
                    toolArgs = objectMapper.readValue(argsStr, Map.class);
                } catch (Exception e) {
                    toolArgs = Collections.emptyMap();
                }

                log.info("Agent 调用工具: {} 参数: {}", toolName, toolArgs);
                String toolResult = executeTool(toolName, toolArgs);
                log.info("工具 {} 返回: {}", toolName, toolResult);

                // 将工具结果加入对话
                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", callId,
                        "content", toolResult
                ));
            }
        }

        return "操作已执行，但响应超时。";
    }

    /** 裁剪历史消息，保留最近 N 条，防止 Token 溢出 */
    private void trimHistory(LinkedList<Map<String, Object>> history) {
        while (history.size() > MAX_HISTORY_SIZE) {
            history.pollFirst();
        }
    }
}
