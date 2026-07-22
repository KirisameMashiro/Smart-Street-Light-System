package com.smartlight.backend.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.Broadcast;
import com.smartlight.backend.entity.BroadcastStrategy;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.mapper.BroadcastMapper;
import com.smartlight.backend.mapper.BroadcastStrategyMapper;
import com.smartlight.backend.mapper.LightMapper;
import com.smartlight.backend.service.MqttPublishService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 广播定时调度器
 * <p>
 * 每 30 秒检查一次所有启用的广播策略，对于在当前时间段内、不启用人流量判断的策略，
 * 按照播放间隔向关联的路灯发送广播命令。
 * <p>
 * 启用人流量判断的策略由 {@link com.smartlight.backend.service.BroadcastTriggerService} 处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BroadcastTimeScheduler {

    private final BroadcastStrategyMapper strategyMapper;
    private final BroadcastMapper broadcastMapper;
    private final LightMapper lightMapper;
    private final MqttPublishService mqttPublishService;
    private final Optional<StringRedisTemplate> stringRedisTemplate;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    private static final String KEY_LAST_PLAY_PREFIX = "broadcast:last-play:";

    /**
     * 每 30 秒检查一次时间触发的广播策略
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void checkTimeBasedStrategies() {
        // 只查询不启用人流量条件的策略（纯时间段触发）
        LambdaQueryWrapper<BroadcastStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BroadcastStrategy::getEnabled, 1)
               .eq(BroadcastStrategy::getEnableFlow, false);
        List<BroadcastStrategy> strategies = strategyMapper.selectList(wrapper);

        if (strategies.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (BroadcastStrategy strategy : strategies) {
            try {
                processTimeStrategy(strategy, now);
            } catch (Exception e) {
                log.error("定时广播策略处理失败: strategyId={}, error={}",
                        strategy.getId(), e.getMessage(), e);
            }
        }
    }

    private void processTimeStrategy(BroadcastStrategy strategy, LocalDateTime now) {
        // 1. 检查时间范围
        if (!isInTimeRange(now, strategy)) return;

        // 2. 获取广播
        Broadcast broadcast = broadcastMapper.selectById(strategy.getBroadcastId());
        if (broadcast == null || broadcast.getEnabled() != 1) return;
        if (broadcast.getContent() == null || broadcast.getContent().isBlank()) return;

        // 3. 获取关联路灯
        List<Long> lightIds = broadcast.getLightIds();
        if (lightIds == null || lightIds.isEmpty()) return;

        // 4. 构建语音文件 URL
        String voiceFileUrl = null;
        if (broadcast.getVoiceFilePath() != null && !broadcast.getVoiceFilePath().isEmpty()) {
            voiceFileUrl = appBaseUrl + "/api/broadcast/broadcasts/"
                    + broadcast.getId() + "/voice-file";
        }

        // 5. 向每个带扬声器的路灯发送广播（受播放间隔限制）
        int sentCount = 0;
        int skipCount = 0;
        List<Light> lights = lightMapper.selectBatchIds(lightIds);
        for (Light light : lights) {
            if (light.getHasSpeaker() == null || !light.getHasSpeaker()) continue;
            if (light.getLightCode() == null || light.getLightCode().isEmpty()) continue;

            // 检查播放间隔
            if (!canPlayNow(strategy.getId(), light.getId(), strategy.getPlayInterval())) {
                skipCount++;
                continue;
            }

            try {
                mqttPublishService.publishBroadcastCommand(
                        light.getLightCode(),
                        broadcast.getContent(),
                        voiceFileUrl
                );
                recordPlayTime(strategy.getId(), light.getId(), strategy.getPlayInterval());
                sentCount++;
            } catch (Exception e) {
                log.error("发送广播命令失败: lightCode={}, error={}",
                        light.getLightCode(), e.getMessage());
            }
        }

        if (sentCount > 0 || skipCount > 0) {
            log.info("定时广播: strategy={}, 发送={}, 间隔跳过={}, 总计={}",
                    strategy.getName(), sentCount, skipCount, lights.size());
        }
    }

    private boolean isInTimeRange(LocalDateTime now, BroadcastStrategy strategy) {
        LocalTime currentTime = now.toLocalTime();
        LocalTime startTime = strategy.getStartTime();
        LocalTime endTime = strategy.getEndTime();

        if (startTime == null || endTime == null) return true;

        boolean timeMatch;
        if (startTime.isBefore(endTime)) {
            timeMatch = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        } else {
            timeMatch = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
        }
        if (!timeMatch) return false;

        // 检查重复规则
        String repeatType = strategy.getRepeatType();
        if (repeatType == null || "daily".equals(repeatType)) return true;

        DayOfWeek dow = now.getDayOfWeek();
        int dowValue = dow.getValue();

        if ("weekdays".equals(repeatType)) return dowValue >= 1 && dowValue <= 5;
        if ("weekend".equals(repeatType)) return dowValue == 6 || dowValue == 7;
        if ("custom".equals(repeatType)) {
            List<Integer> customDays = strategy.getCustomDays();
            return customDays != null && customDays.contains(dowValue);
        }
        return true;
    }

    private boolean canPlayNow(Long strategyId, Long lightId, Integer playIntervalMinutes) {
        if (stringRedisTemplate.isEmpty()) return true;

        // 播放间隔为 0 表示持续播放（无间隔限制），始终允许
        if (playIntervalMinutes == null || playIntervalMinutes <= 0) return true;

        String key = KEY_LAST_PLAY_PREFIX + strategyId + ":" + lightId;
        return !Boolean.TRUE.equals(stringRedisTemplate.get().hasKey(key));
    }

    private void recordPlayTime(Long strategyId, Long lightId, Integer playIntervalMinutes) {
        if (stringRedisTemplate.isEmpty()) return;

        String key = KEY_LAST_PLAY_PREFIX + strategyId + ":" + lightId;
        int interval = (playIntervalMinutes != null && playIntervalMinutes > 0)
                ? playIntervalMinutes : 1;

        stringRedisTemplate.get().opsForValue()
                .set(key, LocalDateTime.now().toString(), interval, TimeUnit.MINUTES);
    }
}
