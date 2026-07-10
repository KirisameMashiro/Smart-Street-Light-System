package com.smartlight.backend.brightness;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 天气数据获取 — 支持和风天气 API 或降级为模拟数据
 * <p>
 * 和风天气免费 API: https://dev.qweather.com/
 * 配置 weather.api.key 即可启用真实数据
 */
@Slf4j
@Component
public class WeatherApiClient {

    @Value("${weather.api.enabled:false}")
    private boolean apiEnabled;

    @Value("${weather.api.key:}")
    private String apiKey;

    /** 和风天气城市 API */
    private static final String QWEATHER_CITY_URL =
            "https://geoapi.qweather.com/v2/city/lookup?location=%s,%s&key=%s";

    /** 和风天气逐时预报 API */
    private static final String QWEATHER_HOURLY_URL =
            "https://devapi.qweather.com/v7/weather/24h?location=%s&key=%s";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build();

    /** 模拟天气数据缓存，避免频繁请求 */
    private CachedWeather cachedWeather;
    private long lastFetchTime = 0;
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30分钟

    /**
     * 获取当前天气数据
     * 优先调用 API，失败或未配置 API key 则返回模拟数据
     */
    public WeatherData fetchWeather(double lat, double lon) {
        if (apiEnabled && apiKey != null && !apiKey.isBlank()) {
            try {
                return fetchFromApi(lat, lon);
            } catch (Exception e) {
                log.warn("天气API调用失败，降级为模拟数据: {}", e.getMessage());
            }
        }
        return fetchMock(lat, lon);
    }

    /**
     * 获取缓存的天气数据（可能不是最新的，但避免频繁请求）
     */
    public WeatherData fetchWeatherCached(double lat, double lon) {
        long now = System.currentTimeMillis();
        if (cachedWeather != null && (now - lastFetchTime) < CACHE_TTL_MS) {
            return cachedWeather.data;
        }
        WeatherData data = fetchWeather(lat, lon);
        cachedWeather = new CachedWeather(data);
        lastFetchTime = now;
        return data;
    }

    /**
     * 发送 GET 请求并自动处理 gzip 解压
     */
    private String getDecompressed(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET().build();
        HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

        String encoding = resp.headers().firstValue("Content-Encoding").orElse("");
        InputStream in = resp.body();

        if ("gzip".equalsIgnoreCase(encoding)) {
            in = new GZIPInputStream(in);
        }

        byte[] bytes = in.readAllBytes();
        in.close();
        String body = new String(bytes, StandardCharsets.UTF_8);
        log.debug("API response [{}]: {}", url.substring(0, Math.min(url.length(), 80)), body.substring(0, Math.min(body.length(), 200)));
        return body;
    }

    private WeatherData fetchFromApi(double lat, double lon) throws Exception {
        // 先查城市 ID
        String cityUrl = String.format(QWEATHER_CITY_URL, lon, lat, apiKey);
        Map<String, Object> cityMap = objectMapper.readValue(getDecompressed(cityUrl),
                new TypeReference<Map<String, Object>>() {});
        String cityId = null;
        if ("200".equals(String.valueOf(cityMap.get("code")))) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> location = (List<Map<String, String>>) cityMap.get("location");
            if (location != null && !location.isEmpty()) {
                cityId = location.get(0).get("id");
            }
        }
        if (cityId == null) {
            throw new RuntimeException("无法获取城市ID, code=" + cityMap.get("code"));
        }

        // 逐时天气
        String hourlyUrl = String.format(QWEATHER_HOURLY_URL, cityId, apiKey);
        Map<String, Object> hourlyMap = objectMapper.readValue(getDecompressed(hourlyUrl),
                new TypeReference<Map<String, Object>>() {});
        if (!"200".equals(String.valueOf(hourlyMap.get("code")))) {
            throw new RuntimeException("天气API返回异常, code=" + hourlyMap.get("code"));
        }

        // 取当前小时的数据
        @SuppressWarnings("unchecked")
        List<Map<String, String>> hourlyList = (List<Map<String, String>>) hourlyMap.get("hourly");
        if (hourlyList == null || hourlyList.isEmpty()) {
            throw new RuntimeException("天气API无逐时数据");
        }
        Map<String, String> currentWeather = hourlyList.get(0);

        return new WeatherData(
                Integer.parseInt(currentWeather.getOrDefault("cloud", "50")),
                parseRainLevel(currentWeather.getOrDefault("text", "")),
                "none",
                Integer.parseInt(currentWeather.getOrDefault("vis", "10000")),
                currentWeather.getOrDefault("text", "晴")
        );
    }

    /**
     * 模拟天气数据 — 用于无 API key 时的降级
     */
    private WeatherData fetchMock(double lat, double lon) {
        java.time.LocalTime now = java.time.LocalTime.now();
        int hour = now.getHour();

        // 模拟不同时段的典型天气
        if (hour >= 6 && hour < 8) {
            return new WeatherData(40, "none", "none", 8000, "晴间多云（模拟）");
        } else if (hour >= 8 && hour < 17) {
            return new WeatherData(60, "none", "none", 10000, "多云（模拟）");
        } else if (hour >= 17 && hour < 19) {
            return new WeatherData(30, "none", "none", 9000, "晴（模拟）");
        } else {
            return new WeatherData(20, "none", "none", 10000, "晴朗（模拟）");
        }
    }

    private String parseRainLevel(String weatherText) {
        if (weatherText == null) return "none";
        String t = weatherText.toLowerCase();
        if (t.contains("暴雨") || t.contains("大暴雨")) return "storm";
        if (t.contains("大雨")) return "heavy";
        if (t.contains("中雨")) return "moderate";
        if (t.contains("雨") || t.contains("小雨") || t.contains("阵雨")) return "light";
        return "none";
    }

    /** 天气数据 */
    public record WeatherData(int cloudCover, String rainLevel, String snowLevel,
                              int visibility, String description) {}

    private record CachedWeather(WeatherData data) {}
}
