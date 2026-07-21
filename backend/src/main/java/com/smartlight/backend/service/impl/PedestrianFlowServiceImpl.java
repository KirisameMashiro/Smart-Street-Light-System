package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.entity.PedestrianFlow;
import com.smartlight.backend.mapper.PedestrianFlowMapper;
import com.smartlight.backend.service.LightService;
import com.smartlight.backend.service.PedestrianFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PedestrianFlowServiceImpl extends ServiceImpl<PedestrianFlowMapper, PedestrianFlow> implements PedestrianFlowService {

    private final Optional<StringRedisTemplate> stringRedisTemplate;
    private final LightService lightService;

    private static final String KEY_FLOW_LATEST_PREFIX = "flow:latest:";

    @Override
    public IPage<PedestrianFlow> getPage(int pageNum, int pageSize, Long lightId,
                                         String startTime, String endTime) {
        List<Map<String, Object>> allRecords = baseMapper.selectFromHourlyPage(lightId, startTime, endTime);

        int total = allRecords.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        List<PedestrianFlow> pageRecords = allRecords.subList(start, end).stream()
                .map(this::mapRowToFlow)
                .collect(Collectors.toList());

        Page<PedestrianFlow> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(pageRecords);
        return page;
    }

    private PedestrianFlow mapRowToFlow(Map<String, Object> map) {
        PedestrianFlow flow = new PedestrianFlow();
        flow.setLightId(map.get("lightId") != null ? ((Number) map.get("lightId")).longValue() : null);
        flow.setCollectTime(map.get("collectTime") != null ? (LocalDateTime) map.get("collectTime") : null);
        flow.setFlowCount(map.get("flowCount") != null ? ((Number) map.get("flowCount")).intValue() : null);
        return flow;
    }

    @Override
    public PedestrianFlow getLatestByLightId(Long lightId) {
        if (lightId == null) return null;

        // ① 优先从 Redis 读取
        PedestrianFlow cached = getFromRedis(lightId);
        if (cached != null) return cached;

        // ② Redis Miss，回退到 MySQL
        LambdaQueryWrapper<PedestrianFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PedestrianFlow::getLightId, lightId)
               .orderByDesc(PedestrianFlow::getCollectTime)
               .last("LIMIT 1");
        return this.getOne(wrapper);
    }

    @Override
    public Map<Long, PedestrianFlow> getAllLatest() {
        Map<Long, PedestrianFlow> result = new HashMap<>();
        List<Light> allLights = lightService.getCachedList();
        if (allLights.isEmpty()) return result;

        if (stringRedisTemplate.isEmpty()) {
            fillMissingFromDb(allLights, result);
            return result;
        }

        List<String> keys = allLights.stream()
                .map(l -> KEY_FLOW_LATEST_PREFIX + l.getId())
                .collect(Collectors.toList());

        List<Object> redisResults = stringRedisTemplate.get().executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    for (String key : keys) {
                        byte[] rawKey = key.getBytes();
                        connection.hashCommands().hGetAll(rawKey);
                    }
                    return null;
                });

        for (int i = 0; i < allLights.size(); i++) {
            Long lightId = allLights.get(i).getId();
            Object raw = (i < redisResults.size()) ? redisResults.get(i) : null;
            if (raw instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> entryMap = (Map<Object, Object>) raw;
                if (!entryMap.isEmpty()) {
                    PedestrianFlow data = mapToFlow(lightId, entryMap);
                    if (data != null) result.put(lightId, data);
                }
            }
        }

        if (result.size() < allLights.size()) {
            fillMissingFromDb(allLights, result);
        }
        return result;
    }

    @Override
    public PedestrianFlow getAverageData(Long lightId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<PedestrianFlow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PedestrianFlow::getLightId, lightId)
               .ge(PedestrianFlow::getCollectTime, startTime)
               .le(PedestrianFlow::getCollectTime, endTime);
        List<PedestrianFlow> list = this.list(wrapper);
        if (list.isEmpty()) return null;

        PedestrianFlow avg = new PedestrianFlow();
        avg.setLightId(lightId);
        avg.setFlowCount((int) list.stream()
                .filter(d -> d.getFlowCount() != null)
                .mapToInt(PedestrianFlow::getFlowCount)
                .average().orElse(0));
        return avg;
    }

    // ==================== 内部辅助方法 ====================

    private PedestrianFlow getFromRedis(Long lightId) {
        if (stringRedisTemplate.isEmpty()) return null;
        String key = KEY_FLOW_LATEST_PREFIX + lightId;
        Map<Object, Object> entries = stringRedisTemplate.get().opsForHash().entries(key);
        if (entries.isEmpty()) return null;
        return mapToFlow(lightId, entries);
    }

    private PedestrianFlow mapToFlow(Long lightId, Map<Object, Object> map) {
        PedestrianFlow data = new PedestrianFlow();
        data.setLightId(lightId);
        data.setFlowCount(getInt(map, "flowCount"));
        data.setCollectTime(getLocalDateTime(map, "collectTime"));
        return data;
    }

    private Integer getInt(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private LocalDateTime getLocalDateTime(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null || val.toString().isEmpty()) return null;
        try { return LocalDateTime.parse(val.toString()); }
        catch (Exception e) { return null; }
    }

    private void fillMissingFromDb(List<Light> allLights, Map<Long, PedestrianFlow> result) {
        for (Light light : allLights) {
            if (result.containsKey(light.getId())) continue;
            try {
                LambdaQueryWrapper<PedestrianFlow> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(PedestrianFlow::getLightId, light.getId())
                       .orderByDesc(PedestrianFlow::getCollectTime)
                       .last("LIMIT 1");
                PedestrianFlow dbData = this.getOne(wrapper);
                if (dbData != null) result.put(light.getId(), dbData);
            } catch (Exception e) {
                log.warn("补全人流量数据失败: lightId={}", light.getId(), e);
            }
        }
    }
}