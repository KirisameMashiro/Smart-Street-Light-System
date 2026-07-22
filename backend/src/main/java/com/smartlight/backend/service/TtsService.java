package com.smartlight.backend.service;

import com.smartlight.backend.dto.TtsPreviewDTO;
import com.smartlight.backend.entity.Broadcast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Value("${tts.output-dir:./voice-files}")
    private String ttsOutputDir;

    /**
     * TTS 试听：合成文本并返回 WAV 音频字节
     */
    public byte[] preview(TtsPreviewDTO dto) {
        String text = dto.getText();
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("合成文本不能为空");
        }

        String voiceName = dto.getVoiceName() != null ? dto.getVoiceName() : "serena";
        double speed = dto.getSpeed() != null ? dto.getSpeed().doubleValue() : 1.0;

        return callTtsApi(text, voiceName, speed);
    }

    /**
     * 为指定广播生成语音文件并保存到本地
     *
     * @param broadcast 广播实体（含语音设置和内容）
     * @return 保存后的文件路径
     */
    public String generateVoiceFile(Broadcast broadcast) throws IOException {
        String content = broadcast.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("广播内容不能为空");
        }

        String voiceName = broadcast.getVoiceName() != null ? broadcast.getVoiceName() : "serena";
        double speed = broadcast.getVoiceSpeed() != null ? broadcast.getVoiceSpeed().doubleValue() : 1.0;

        log.info("Generating voice file for broadcast id={}, voice={}, speed={}",
                broadcast.getId(), voiceName, speed);

        byte[] audioBytes = callTtsApi(content, voiceName, speed);

        // 确保输出目录存在
        Path outputDir = Paths.get(ttsOutputDir);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // 生成唯一文件件名：voice_{broadcastId}_{timestamp}.wav
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("voice_%d_%s.wav", broadcast.getId(), timestamp);
        Path filePath = outputDir.resolve(fileName);

        Files.write(filePath, audioBytes);

        String savedPath = filePath.toAbsolutePath().toString();
        log.info("Voice file saved: broadcast={}, path={}, size={} bytes",
                broadcast.getId(), savedPath, audioBytes.length);

        // 删除该广播的旧语音文件（可选，避免堆积）
        if (broadcast.getVoiceFilePath() != null) {
            try {
                Path oldPath = Paths.get(broadcast.getVoiceFilePath());
                if (Files.exists(oldPath)) {
                    Files.deleteIfExists(oldPath);
                    log.info("Deleted old voice file: {}", oldPath);
                }
            } catch (IOException e) {
                log.warn("Failed to delete old voice file: {}", e.getMessage());
            }
        }

        return savedPath;
    }

    /**
     * 调用 TTS API，返回 WAV 音频字节
     */
    private byte[] callTtsApi(String text, String voiceName, double speed) {
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

        log.info("TTS request: text_len={}, voice={}, speed={}, url={}",
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

            log.info("TTS success: audio_size={} bytes", audioBytes.length);
            return audioBytes;

        } catch (Exception e) {
            log.error("TTS failed: {}", e.getMessage(), e);
            throw new RuntimeException("语音合成失败: " + e.getMessage(), e);
        }
    }
}
