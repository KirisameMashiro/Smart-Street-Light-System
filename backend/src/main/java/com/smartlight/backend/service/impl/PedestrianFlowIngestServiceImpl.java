package com.smartlight.backend.service.impl;

import com.smartlight.backend.dto.PedestrianFlowIngestDTO;
import com.smartlight.backend.entity.PedestrianFlow;
import com.smartlight.backend.service.PedestrianFlowIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 人流量数据入库服务实现（仅 Redis）
 * <p>
 * 1. 写入 Redis 原始数据队列 flow:raw（List），供小时聚合消费
 * 2. 更新 Redis 缓存 flow:latest:{lightId}（Hash）
 * <p>
 * 不再写入 MySQL pedestrian_flow 表，降低数据库压力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PedestrianFlowIngestServiceImpl implements PedestrianFlowIngestService {

    private final Optional<StringRedisTemplate> stringRedisTemplate;

    /** Redis Key 前缀：每盏路灯最新人流量 */
    private static final String KEY_FLOW_LATEST_PREFIX = "flow:latest:";
    /** Redis Key：人流量原始数据队列 */
    private static final String KEY_FLOW_RAW_QUEUE = "flow:raw";
    /** 人流量缓存 TTL（秒） */
    private static final long FLOW_CACHE_TTL_SECONDS = 300;

    @Override
    public PedestrianFlow ingest(PedestrianFlowIngestDTO dto) {
        if (dto.getLightId() == null) {
            log.warn("人流量数据缺少 lightId，丢弃");
            return null;
        }

        // 1. 构建实体
        PedestrianFlow entity = new PedestrianFlow();
        entity.setLightId(dto.getLightId());
        entity.setFlowCount(dto.getFlowCount() != null ? dto.getFlowCount() : 0);
        entity.setCollectTime(dto.getCollectTime() != null ? dto.getCollectTime() : LocalDateTime.now());

        // 2. 写入 Redis 原始数据队列（供小时聚合消费）
        pushToRawQueue(entity);

        // 3. 人流量原始数据已推入 Redis 队列，由 BatchService 定时消费聚合

        // 4. 更新 Redis 缓存（最新人流量）
        saveToRedis(entity);

        return entity;
    }

    /**
     * 将原始人流量数据推入 Redis List 队列
     * 格式："lightId,flowCount,collectTime"
     */
    private void pushToRawQueue(PedestrianFlow entity) {
        if (stringRedisTemplate.isEmpty()) return;
        String value = entity.getLightId() + ","
                + entity.getFlowCount() + ","
                + (entity.getCollectTime() != null ? entity.getCollectTime().toString() : "");
        stringRedisTemplate.get().opsForList().leftPush(KEY_FLOW_RAW_QUEUE, value);
        log.debug("人流量原始数据已推入队列: value={}", value);
    }

    private void saveToRedis(PedestrianFlow entity) {
        if (stringRedisTemplate.isEmpty()) return;
        String key = KEY_FLOW_LATEST_PREFIX + entity.getLightId();

        stringRedisTemplate.get().opsForHash().putAll(key, Map.of(
                "flowCount", String.valueOf(entity.getFlowCount()),
                "collectTime", entity.getCollectTime() != null ? entity.getCollectTime().toString() : ""
        ));

        stringRedisTemplate.get().expire(key, FLOW_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("人流量缓存已更新 Redis: key={}, flowCount={}", key, entity.getFlowCount());
    }
}