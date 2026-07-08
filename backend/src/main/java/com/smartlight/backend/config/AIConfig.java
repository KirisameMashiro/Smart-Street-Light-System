package com.smartlight.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI Agent 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.agent")
public class AIConfig {

    /** API Key */
    private String apiKey = "sk-your-api-key";

    /** API 地址（OpenAI 兼容协议） */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 系统提示词 */
    private String systemPrompt = "你是智慧路灯管理系统的AI运维助手。你可以查询路灯状态、控制路灯开关、调节亮度、查看传感器数据、查询报警信息。" +
            "系统内置知识库，包含四类运维知识：故障排查（LED灯不亮、电源故障等）、维护规范（巡检周期、保养步骤）、设备参数（额定功率、防护等级）、操作指引（开关灯、调光操作步骤）。" +
            "当用户询问故障、维护、参数、操作相关问题时，务必先调用search_knowledge工具检索知识库。" +
            "回复时请使用Markdown格式组织内容，可以用表格、标题、列表等让排版清晰，但不要使用emoji表情符号。";
}
