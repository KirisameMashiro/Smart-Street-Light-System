package com.smartlight.backend.service;

import com.smartlight.backend.dto.TtsPreviewDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

/**
 * TTS 语音合成服务
 * <p>
 * 调用外部 Qwen3-TTS API 进行文本转语音。
 * API 格式兼容 OpenAI Audio API：POST /v1/audio/speech
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final RestTemplate restTemplate;

    @Value("${tts.api-url:http://localhost:8001}")
    private String ttsApiUrl;

    /**
     * TTS 试听：合成文本并返回 WAV 音频字节
     */
    public byte[] preview(TtsPreviewDTO dto) {
        String text = dto.getText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("合成文本不能为空");
        }

        String voiceName = dto.getVoiceName() != null ? dto.getVoiceName() : "default";
        double speed = dto.getSpeed() != null ? dto.getSpeed().doubleValue() : 1.0;
        double volume = dto.getVolume() != null ? dto.getVolume().doubleValue() : 1.0;

        // 构建 OpenAI 兼容请求体
        Map<String, Object> requestBody = Map.of(
                "model", "Qwen3-TTS-12Hz-0.6B-CustomVoice",
                "input", text,
                "voice", voiceName,
                "response_format", "wav",
                "speed", speed
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        String url = ttsApiUrl + "/v1/audio/speech";

        log.info("TTS preview request: text_len={}, voice={}, speed={}, url={}",
                text.length(), voiceName, speed, url);

        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            byte[] audioBytes = response.getBody();
            if (audioBytes == null || audioBytes.length == 0) {
                throw new RuntimeException("TTS 服务返回空音频");
            }

            log.info("TTS preview success: audio_size={} bytes", audioBytes.length);
            return audioBytes;

        } catch (Exception e) {
            log.error("TTS preview failed: {}", e.getMessage(), e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        }
    }
}