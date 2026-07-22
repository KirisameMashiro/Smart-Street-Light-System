package com.smartlight.backend.controller;

import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.TtsPreviewDTO;
import com.smartlight.backend.entity.VoiceSetting;
import com.smartlight.backend.mapper.VoiceSettingMapper;
import com.smartlight.backend.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 语音设置 + TTS 试听 API
 */
@Slf4j
@RestController
@RequestMapping("/api/broadcast/voice")
@RequiredArgsConstructor
public class VoiceSettingController {

    private final VoiceSettingMapper voiceSettingMapper;
    private final TtsService ttsService;

    /**
     * 获取当前语音设置（列表形式，通常只有一条记录）
     */
    @GetMapping
    public Result<List<VoiceSetting>> getSettings() {
        List<VoiceSetting> list = voiceSettingMapper.selectList(null);
        return Result.success(list);
    }

    /**
     * 保存语音设置（新增或更新）
     */
    @PutMapping
    public Result<Boolean> saveSetting(@RequestBody VoiceSetting setting) {
        if (setting.getId() != null) {
            return Result.success(voiceSettingMapper.updateById(setting) > 0);
        } else {
            return Result.success(voiceSettingMapper.insert(setting) > 0);
        }
    }

    /**
     * TTS 试听：合成文本并返回 WAV 音频
     */
    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> preview(@RequestBody TtsPreviewDTO dto) {
        byte[] audio = ttsService.preview(dto);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .body(audio);
    }
}