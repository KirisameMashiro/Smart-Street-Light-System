package com.smartlight.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * TTS 试听请求 DTO
 */
@Data
public class TtsPreviewDTO {

    /** 要合成的文本 */
    private String text;

    /** 语音角色/名称 */
    private String voiceName;

    /** 语速 0.5~2.0 */
    private BigDecimal speed;

    /** 音量 0.0~1.0 */
    private BigDecimal volume;
}