package com.smartlight.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气服务 - 基于 Open-Meteo 免费 API
 * 
 * 仅用于判断是否出现极端恶劣天气（雷暴、暴雪、浓雾等），
 * 普通阴天、小雨不触发白天补光。
 * 缓存：同一坐标 15 分钟内不重复请求。
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast"
            + "?latitude=%s&longitude=%s"
            + "&current=weather_code"
            + "&timezone=auto";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long CACHE_TTL_MS = 15 * 60 * 1000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CachedResult> cache = new ConcurrentHashMap<>();

    /**
     * 判断当前是否为极端恶劣天气（需要白天开灯的程度）
     */
    public boolean isExtremeWeather(double latitude, double longitude) {
        String key = round(latitude) + "," + round(longitude);
        CachedResult cached = cache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.ts < CACHE_TTL_MS) {
            return cached.extreme;
        }

        try {
            String url = String.format(OPEN_METEO_URL, latitude, longitude);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode current = objectMapper.readTree(resp.body()).get("current");
                if (current != null) {
                    int code = current.get("weather_code").asInt();
                    boolean extreme = isExtremeCode(code);
                    cache.put(key, new CachedResult(extreme));
                    return extreme;
                }
            }
        } catch (Exception e) {
            log.warn("天气API请求异常: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 仅以下极端天气码才视为需要白天补光：
     *   48   浓雾/淞
     *   65   大雨
     *   75   暴雪
     *   77   雪粒
     *   82   强阵雨
     *   95   雷暴（轻度）
     *   96   雷暴+冰雹（轻度）
     *   99   雷暴+冰雹（重度）
     */
    private static boolean isExtremeCode(int code) {
        return code == 48 || code == 65 || code == 75 || code == 77
                || code == 82 || code == 95 || code == 96 || code == 99;
    }

    private static String round(double v) {
        return String.valueOf(Math.round(v * 1000.0) / 1000.0);
    }

    private static class CachedResult {
        final boolean extreme;
        final long ts = System.currentTimeMillis();
        CachedResult(boolean extreme) { this.extreme = extreme; }
    }
}
