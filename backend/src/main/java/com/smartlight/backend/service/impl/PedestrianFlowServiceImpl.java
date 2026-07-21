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
        // 小时聚合扩展字段
        flow.setMaxFlow(map.get("maxFlow") != null ? ((Number) map.get("maxFlow")).intValue() : null);
        flow.setMinFlow(map.get("minFlow") != null ? ((Number) map.get("minFlow")).intValue() : null);
        flow.setTotalFlow(map.get("totalFlow") != null ? ((Number) map.get("totalFlow")).intValue() : null);
        flow.setDataCount(map.get("dataCount") != null ? ((Number) map.get("dataCount")).intValue() : null);
        return flow;
    }

    @Override
    public PedestrianFlow getLatestByLightId(Long lightId) {
        if (lightId == null) return null;

        // ① 优先从 Redis 读取（最新人流量缓存）
        PedestrianFlow cached = getFromRedis(lightId);
        if (cached != null) return cached;

        // ② Redis Miss，回退到小时聚合表的最新记录
        // （原始数据不再写入 pedestrian_flow 表）
        Map<String, Object> hourlyRow = baseMapper.selectLatestFromHourly(lightId);
        if (hourlyRow != null && !hourlyRow.isEmpty()) {
            return mapRowToFlow(hourlyRow);
        }
        return null;
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
        // 从小时聚合表查询（原始数据仅存 Redis，不再入 MySQL）
        String start = startTime != null ? startTime.toString() : null;
        String end = endTime != null ? endTime.toString() : null;
        List<Map<String, Object>> hourlyRecords = baseMapper.selectFromHourlyPage(lightId, start, end);
        if (hourlyRecords.isEmpty()) return null;

        PedestrianFlow avg = new PedestrianFlow();
        avg.setLightId(lightId);
        double avgVal = hourlyRecords.stream()
                .map(m -> m.get("flowCount"))
                .filter(Objects::nonNull)
                .mapToDouble(v -> ((Number) v).doubleValue())
                .average().orElse(0);
        avg.setFlowCount((int) Math.ceil(avgVal));
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

    /**
     * Redis Miss 时的回退策略：查询小时聚合表的最新记录
     * （原始数据不再写入 pedestrian_flow 表，所以不从原始表回退）
     */
    private void fillMissingFromDb(List<Light> allLights, Map<Long, PedestrianFlow> result) {
        for (Light light : allLights) {
            if (result.containsKey(light.getId())) continue;
            try {
                Map<String, Object> hourlyRow = baseMapper.selectLatestFromHourly(light.getId());
                if (hourlyRow != null && !hourlyRow.isEmpty()) {
                    result.put(light.getId(), mapRowToFlow(hourlyRow));
                }
            } catch (Exception e) {
                log.warn("从小时聚合表补全人流量数据失败: lightId={}", light.getId(), e);
            }
        }
    }
}