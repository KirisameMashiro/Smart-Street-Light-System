package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.Broadcast;
import com.smartlight.backend.entity.BroadcastStrategy;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.service.BroadcastService;
import com.smartlight.backend.service.BroadcastStrategyService;
import com.smartlight.backend.service.MqttPublishService;
import com.smartlight.backend.service.TtsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/broadcast")
public class BroadcastController {

    @Autowired
    private BroadcastService broadcastService;

    @Autowired
    private BroadcastStrategyService broadcastStrategyService;

    @Autowired
    private TtsService ttsService;

    @Autowired
    private LightMapper lightMapper;

    @Autowired
    private MqttPublishService mqttPublishService;

    /** 服务器对外可访问的地址，用于生成语音文件下载 URL 下发给路灯设备 */
    @org.springframework.beans.factory.annotation.Value("${broadcast.server-url:http://localhost:8080}")
    private String serverUrl;

    // ========== 广播设计管理 ==========

    @GetMapping("/broadcasts")
    public Result<List<Broadcast>> listBroadcasts() {
        LambdaQueryWrapper<Broadcast> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Broadcast::getCreateTime);
        return Result.success(broadcastService.list(wrapper));
    }

    @GetMapping("/broadcasts/{id}")
    public Result<Broadcast> getBroadcast(@PathVariable Long id) {
        return Result.success(broadcastService.getById(id));
    }

    @PostMapping("/broadcasts")
    public Result<Broadcast> addBroadcast(@RequestBody Broadcast broadcast) {
        broadcastService.save(broadcast);
        return Result.success(broadcast);
    }

    @PutMapping("/broadcasts")
    public Result<Boolean> updateBroadcast(@RequestBody Broadcast broadcast) {
        return Result.success(broadcastService.updateById(broadcast));
    }

    @DeleteMapping("/broadcasts/{id}")
    public Result<Boolean> deleteBroadcast(@PathVariable Long id) {
        return Result.success(broadcastService.removeById(id));
    }

    // ========== 语音生成与播放 ==========

    /**
     * 为指定广播生成语音文件
     */
    @PostMapping("/broadcasts/{id}/generate-voice")
    public Result<String> generateVoice(@PathVariable Long id) {
        Broadcast broadcast = broadcastService.getById(id);
        if (broadcast == null) {
            return Result.error("广播不存在");
        }
        if (broadcast.getContent() == null || broadcast.getContent().isBlank()) {
            return Result.error("广播内容为空，无法生成语音");
        }

        try {
            String filePath = ttsService.generateVoiceFile(broadcast);
            // 更新数据库中的语音文件路径
            broadcast.setVoiceFilePath(filePath);
            broadcastService.updateById(broadcast);
            return Result.success(filePath);
        } catch (IOException e) {
            return Result.error("语音文件保存失败: " + e.getMessage());
        } catch (Exception e) {
            return Result.error("语音生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定广播的语音文件（用于播放）
     */
    @GetMapping("/broadcasts/{id}/voice-file")
    public ResponseEntity<byte[]> getVoiceFile(@PathVariable Long id) {
        Broadcast broadcast = broadcastService.getById(id);
        if (broadcast == null || broadcast.getVoiceFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Path filePath = Paths.get(broadcast.getVoiceFilePath());
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] audioBytes = Files.readAllBytes(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"voice_" + id + ".wav\"")
                    .body(audioBytes);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 检查广播是否关联了带监控的路灯（用于前端动态显示人流条件字段）
     */
    @GetMapping("/broadcasts/{id}/has-monitoring")
    public Result<Map<String, Object>> hasMonitoring(@PathVariable Long id) {
        Broadcast broadcast = broadcastService.getById(id);
        if (broadcast == null) {
            return Result.error("广播不存在");
        }

        List<Long> lightIds = broadcast.getLightIds();
        boolean hasMonitoring = false;
        int monitorCount = 0;

        if (lightIds != null && !lightIds.isEmpty()) {
            List<Light> lights = lightMapper.selectBatchIds(lightIds);
            for (Light light : lights) {
                if (light.getHasCamera() != null && light.getHasCamera()) {
                    hasMonitoring = true;
                    monitorCount++;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hasMonitoring", hasMonitoring);
        result.put("monitorCount", monitorCount);
        return Result.success(result);
    }

    // ========== 广播策略管理 ==========

    @GetMapping("/strategies")
    public Result<List<BroadcastStrategy>> listStrategies() {
        return Result.success(broadcastStrategyService.listWithBroadcastTitle());
    }

    @GetMapping("/strategies/{id}")
    public Result<BroadcastStrategy> getStrategy(@PathVariable Long id) {
        BroadcastStrategy strategy = broadcastStrategyService.getById(id);
        if (strategy != null) {
            Broadcast broadcast = broadcastService.getById(strategy.getBroadcastId());
            if (broadcast != null) {
                strategy.setBroadcastTitle(broadcast.getTitle());
            }
        }
        return Result.success(strategy);
    }

    @PostMapping("/strategies")
    public Result<Boolean> addStrategy(@RequestBody BroadcastStrategy strategy) {
        LambdaQueryWrapper<BroadcastStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BroadcastStrategy::getName, strategy.getName());
        if (strategy.getId() != null) {
            wrapper.ne(BroadcastStrategy::getId, strategy.getId());
        }
        if (broadcastStrategyService.count(wrapper) > 0) {
            throw new IllegalArgumentException("策略名称已存在：" + strategy.getName());
        }
        return Result.success(broadcastStrategyService.save(strategy));
    }

    @PutMapping("/strategies")
    public Result<Boolean> updateStrategy(@RequestBody BroadcastStrategy strategy) {
        LambdaQueryWrapper<BroadcastStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BroadcastStrategy::getName, strategy.getName());
        if (strategy.getId() != null) {
            wrapper.ne(BroadcastStrategy::getId, strategy.getId());
        }
        if (broadcastStrategyService.count(wrapper) > 0) {
            throw new IllegalArgumentException("策略名称已存在：" + strategy.getName());
        }
        return Result.success(broadcastStrategyService.updateById(strategy));
    }

    @DeleteMapping("/strategies/{id}")
    public Result<Boolean> deleteStrategy(@PathVariable Long id) {
        return Result.success(broadcastStrategyService.removeById(id));
    }

    /**
     * 测试播放：通过 MQTT 向策略关联广播的所有路灯发送测试广播命令
     */
    @PostMapping("/strategies/{id}/test-play")
    public Result<String> testPlay(@PathVariable Long id) {
        BroadcastStrategy strategy = broadcastStrategyService.getById(id);
        if (strategy == null) {
            return Result.error("策略不存在");
        }

        Broadcast broadcast = broadcastService.getById(strategy.getBroadcastId());
        if (broadcast == null) {
            return Result.error("关联广播不存在");
        }

        List<Long> lightIds = broadcast.getLightIds();
        if (lightIds == null || lightIds.isEmpty()) {
            return Result.error("该广播未关联任何路灯");
        }

        // 构建语音文件 URL
        String voiceFileUrl = null;
        if (broadcast.getVoiceFilePath() != null && !broadcast.getVoiceFilePath().isEmpty()) {
            voiceFileUrl = serverUrl + "/api/broadcast/broadcasts/"
                    + broadcast.getId() + "/voice-file";
        }

        int sentCount = 0;
        List<Light> lights = lightMapper.selectBatchIds(lightIds);
        for (Light light : lights) {
            if (light.getHasSpeaker() != null && light.getHasSpeaker()
                    && light.getLightCode() != null && !light.getLightCode().isEmpty()) {
                mqttPublishService.publishBroadcastCommand(
                        light.getLightCode(),
                        broadcast.getContent(),
                        voiceFileUrl
                );
                sentCount++;
            }
        }

        if (sentCount == 0) {
            return Result.error("未找到带扬声器的关联路灯，无法发送测试广播");
        }

        return Result.success("已向 " + sentCount + " 个路灯发送测试广播命令");
    }
}
