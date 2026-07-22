package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.entity.Broadcast;
import com.smartlight.backend.entity.BroadcastStrategy;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.mapper.BroadcastMapper;
import com.smartlight.backend.mapper.BroadcastStrategyMapper;
import com.smartlight.backend.mapper.LightMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 广播人流触发服务
 * <p>
 * 在人流量数据到达后，检查是否有启用的广播策略满足触发条件。
 * 如果满足，通过 MQTT 向关联路灯发送广播播放命令。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastTriggerService {

    private final BroadcastStrategyMapper strategyMapper;
    private final BroadcastMapper broadcastMapper;
    private final LightMapper lightMapper;
    private final Optional<StringRedisTemplate> stringRedisTemplate;

    @Autowired
    @Lazy
    private MqttPublishService mqttPublishService;

    @Value("${mqtt.enabled:false}")
    private boolean mqttEnabled;

    /** 后端服务的基础 URL，设备通过此地址下载语音文件 */
    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    /** Redis Key 前缀：上次播放时间 */
    private static final String KEY_LAST_PLAY_PREFIX = "broadcast:last-play:";

    /**
     * 当人流量数据到达时调用此方法，检查并触发广播
     *
     * @param lightId   人流量数据关联的路灯 ID
     * @param flowCount 当前人流量计数
     */
    public void checkAndTrigger(Long lightId, Integer flowCount) {
        if (lightId == null || flowCount == null) return;

        // 1. 找到所有启用的、启用人流量条件的广播策略
        LambdaQueryWrapper<BroadcastStrategy> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BroadcastStrategy::getEnabled, 1)
               .eq(BroadcastStrategy::getEnableFlow, true);
        List<BroadcastStrategy> strategies = strategyMapper.selectList(wrapper);

        if (strategies.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();

        for (BroadcastStrategy strategy : strategies) {
            try {
                processStrategy(strategy, lightId, flowCount, now);
            } catch (Exception e) {
                log.error("处理广播策略失败: strategyId={}, lightId={}, error={}",
                        strategy.getId(), lightId, e.getMessage(), e);
            }
        }
    }

    /**
     * 处理单个策略：检查各项条件，满足则触发
     */
    private void processStrategy(BroadcastStrategy strategy, Long lightId,
                                  Integer flowCount, LocalDateTime now) {
        // 2. 检查时间调度
        if (!isInTimeRange(now, strategy)) {
            return;
        }

        // 3. 获取广播及其关联的路灯
        Broadcast broadcast = broadcastMapper.selectById(strategy.getBroadcastId());
        if (broadcast == null || broadcast.getEnabled() != 1) {
            return;
        }

        // 4. 检查该路灯是否属于此广播的关联路灯
        List<Long> lightIds = broadcast.getLightIds();
        if (lightIds == null || !lightIds.contains(lightId)) {
            return;
        }

        // 5. 检查人流量条件
        if (!isFlowConditionMet(strategy, flowCount)) {
            return;
        }

        // 6. 检查播放间隔
        if (!canPlayNow(strategy.getId(), lightId)) {
            return;
        }

        // 7. 获取路灯信息
        Light light = lightMapper.selectById(lightId);
        if (light == null || light.getLightCode() == null) {
            log.warn("路灯不存在或无编号: lightId={}", lightId);
            return;
        }

        // 8. 必须有扬声器
        if (light.getHasSpeaker() == null || !light.getHasSpeaker()) {
            return;
        }

        // 9. 构建语音文件 URL
        String voiceFileUrl = null;
        if (broadcast.getVoiceFilePath() != null && !broadcast.getVoiceFilePath().isEmpty()) {
            voiceFileUrl = appBaseUrl + "/api/broadcast/broadcasts/"
                    + broadcast.getId() + "/voice-file";
        }

        // 10. 通过 MQTT 发送广播命令
        log.info("触发人流广播: strategy={}, broadcast={}, lightId={}, lightCode={}, flowCount={}, threshold={}",
                strategy.getName(), broadcast.getTitle(), lightId, light.getLightCode(),
                flowCount, strategy.getFlowThreshold());

        mqttPublishService.publishBroadcastCommand(
                light.getLightCode(),
                broadcast.getContent(),
                voiceFileUrl
        );

        // 11. 记录上次播放时间
        recordPlayTime(strategy.getId(), lightId, strategy.getPlayInterval());
    }

    /**
     * 检查当前时间是否在策略的调度时间范围内
     */
    private boolean isInTimeRange(LocalDateTime now, BroadcastStrategy strategy) {
        LocalTime currentTime = now.toLocalTime();
        LocalTime startTime = strategy.getStartTime();
        LocalTime endTime = strategy.getEndTime();

        if (startTime == null || endTime == null) {
            return true; // 无时间限制
        }

        // 检查时间是否在区间内
        boolean timeMatch;
        if (startTime.isBefore(endTime)) {
            timeMatch = !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime);
        } else {
            // 跨天区间（如 22:00 - 06:00）
            timeMatch = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
        }
        if (!timeMatch) return false;

        // 检查重复类型
        String repeatType = strategy.getRepeatType();
        if (repeatType == null || "daily".equals(repeatType)) {
            return true;
        }

        DayOfWeek dow = now.getDayOfWeek();
        int dowValue = dow.getValue(); // 1=Monday, 7=Sunday

        if ("weekdays".equals(repeatType)) {
            return dowValue >= 1 && dowValue <= 5;
        } else if ("weekend".equals(repeatType)) {
            return dowValue == 6 || dowValue == 7;
        } else if ("custom".equals(repeatType)) {
            List<Integer> customDays = strategy.getCustomDays();
            return customDays != null && customDays.contains(dowValue);
        }

        return true;
    }

    /**
     * 检查人流量条件是否满足
     */
    private boolean isFlowConditionMet(BroadcastStrategy strategy, Integer flowCount) {
        String condition = strategy.getFlowCondition();
        Integer threshold = strategy.getFlowThreshold();

        if (condition == null || threshold == null) return true;

        if ("gt".equals(condition)) {
            return flowCount > threshold;
        } else if ("lt".equals(condition)) {
            return flowCount < threshold;
        }
        return true;
    }

    /**
     * 检查是否可以播放（播放间隔控制）
     */
    private boolean canPlayNow(Long strategyId, Long lightId) {
        if (stringRedisTemplate.isEmpty()) return true;

        Integer playInterval = null; // Will be fetched per-strategy below
        // We already have the strategy - let's just check Redis directly

        String key = KEY_LAST_PLAY_PREFIX + strategyId + ":" + lightId;
        String lastPlay = stringRedisTemplate.get().opsForValue().get(key);

        if (lastPlay != null) {
            log.debug("播放间隔未到: strategyId={}, lightId={}, lastPlay={}",
                    strategyId, lightId, lastPlay);
            return false;
        }
        return true;
    }

    /**
     * 记录播放时间
     */
    private void recordPlayTime(Long strategyId, Long lightId, Integer playIntervalMinutes) {
        if (stringRedisTemplate.isEmpty()) return;

        String key = KEY_LAST_PLAY_PREFIX + strategyId + ":" + lightId;
        String value = LocalDateTime.now().toString();

        // 默认最小间隔 1 分钟
        int interval = (playIntervalMinutes != null && playIntervalMinutes > 0)
                ? playIntervalMinutes : 1;

        stringRedisTemplate.get().opsForValue().set(key, value, interval, TimeUnit.MINUTES);
        log.debug("记录播放时间: key={}, ttl={}min", key, interval);
    }
}
