# Redis 缓存 + 批量写入优化方案

## Context

当前系统在大量路灯场景下存在两个严重性能瓶颈：

**写入瓶颈**：传感器模拟器每 3~5 秒为每盏灯发一条 MQTT 消息，后端 `SensorDataIngestServiceImpl.ingest()` 对每条消息都单独 INSERT 到 MySQL，且在写入前还要 SELECT 一次最新数据（用于梯形积分计算耗电）。1000 盏灯场景下，MySQL 写入 QPS 高达 600~1000，且每条写入都是独立事务。

**读取瓶颈**：前端实时监控页面 `RealtimeMonitor.vue` 每次轮询时：
1. 调用 `GET /api/lights` 全表扫描 MySQL
2. 对每盏灯挨个调用 `GET /api/sensor-data/latest/{id}` — N+1 问题

## 设计方案

### 整体架构

```
[传感器 MQTT 上报]
       ↓
  MqttConfig.handleMessage()
       ↓
  SensorDataIngestServiceImpl.ingest()
       ├── ① 写 Redis（sensor:latest:{id} HSET）      ← 新
       ├── ② 读 Redis 获取上次功率（替代 SELECT MySQL）  ← 新
       ├── ③ 累加 CumulativeEnergyService（内存，不变）
       ├── ④ 放入 BlockingQueue 缓冲区                  ← 新
       ├── ⑤ 异步触发告警检测（executor.submit）         ← 改
       └── ⑥ 立即返回（毫秒级）
                              ↓
                    SensorDataBatchService（新）
                    @Scheduled(fixedDelay=5000)
                    drainTo() → batch INSERT → MySQL
                              ↓
                    CarbonService（每天凌晨 2:30 从 MySQL SUM 查询，不受影响）
                    CumulativeEnergyService 每日重置（零点清内存，不受影响）

[前端轮询]
       ↓
  RealtimeMonitor.vue 刷新
       ├── GET /api/lights              → 从 Redis 读（加缓存注解）
       ├── GET /api/sensor-data/latest/all  ← 新增批量接口，从 Redis pipeline 读取
       └── GET /api/sensor-data/today-energy → CumulativeEnergyService 内存（不变）
```

---

## 改动清单

### 后端（9 个文件）

#### 1. `backend/pom.xml` — 新增依赖
- 添加 `spring-boot-starter-data-redis`
- 添加 `commons-pool2`（连接池）

#### 2. `backend/src/main/resources/application.properties` — 新增配置
```properties
# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.connect-timeout=2000
```

#### 3. **新文件** `RedisConfig.java` — Redis 配置类
- 配置 `RedisTemplate<String, Object>`，使用 Jackson2JsonRedisSerializer
- 配置 `StringRedisTemplate`
- 设置 key 和 value 的序列化

#### 4. **新文件** `SensorDataBatchService.java` — 批量写入服务
```java
@Service
@Slf4j
@RequiredArgsConstructor
public class SensorDataBatchService {
    
    private final SensorDataMapper sensorDataMapper;
    private final AlertCheckService alertCheckService;
    
    // 线程安全缓冲区
    private final BlockingQueue<SensorData> buffer = new LinkedBlockingQueue<>();
    
    public void enqueue(SensorData data) {
        buffer.offer(data);
    }
    
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void flushToDatabase() {
        List<SensorData> batch = new ArrayList<>();
        buffer.drainTo(batch, 500);
        if (batch.isEmpty()) return;
        
        // 批量 INSERT
        sensorDataMapper.insertBatch(batch);  // 需要新增 Mapper 方法
        log.info("批量写入传感器数据: {} 条", batch.size());
        
        // 批量异步触发告警检测
        for (SensorData data : batch) {
            alertCheckService.checkAndGenerateAlert(data);
        }
    }
}
```

#### 5. `SensorDataMapper.java` — 新增批量 INSERT 方法
```java
// 新增 XML 或注解批量插入
void insertBatch(@Param("list") List<SensorData> list);
```

对应 `SensorDataMapper.xml` 新增：
```xml
<insert id="insertBatch" useGeneratedKeys="false">
    INSERT INTO sensor_data (light_id, illuminance, power, voltage, current, 
                             temperature, humidity, sampling_energy, collect_time, create_time)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.lightId}, #{item.illuminance}, #{item.power}, #{item.voltage},
         #{item.current}, #{item.temperature}, #{item.humidity}, 
         #{item.samplingEnergy}, #{item.collectTime}, NOW())
    </foreach>
</insert>
```

#### 6. `SensorDataIngestServiceImpl.java` — 核心改造：写入 Redis + 批量缓冲区
**改动内容：**
- 注入 `StringRedisTemplate`、`SensorDataBatchService`
- `ingest()` 中，计算耗电所需的上次功率改为从 Redis 读
- 写入 Redis `sensor:latest:{lightId}`（Hash）
- 写入 Redis `energy:today`（Hash，递增累计耗电）
- 放入 `SensorDataBatchService` 的缓冲区
- 异步触发告警检测（不再同步）
- 移除 `@Transactional`（不再需要）

#### 7. `SensorDataServiceImpl.java` — 读取改为优先查 Redis
**改动内容：**
- `getLatestByLightId()` → 先查 Redis `sensor:latest:{id}`，Miss 再查 MySQL
- 新增 `getAllLatest()` → 从 Redis 一次性读取所有路灯最新数据（pipeline）

#### 8. `SensorDataController.java` — 新增批量查询接口
```java
@GetMapping("/latest/all")
public Result<Map<Long, SensorDataVO>> getAllLatest() {
    return Result.success(sensorDataService.getAllLatest());
}
```

#### 9. `LightServiceImpl.java` — 路灯元数据增加 Redis 缓存
- 查询列表时加 `@Cacheable` 或将全量数据缓存到 Redis
- 增删改时清除缓存

---

### 前端（3 个文件）

#### 1. `frontend/src/api/sensor.js` — 新增批量查询 API
```javascript
// 获取所有路灯最新传感器数据（一次性批量查询）
export function getAllLatestSensorData() {
  return request.get('/sensor-data/latest/all')
}
```

#### 2. `frontend/src/views/monitor/RealtimeMonitor.vue` — 核心改造
**改动内容：**
- `loadSensorData()` 由 N 次 `getLatestSensorData(light.id)` 改为 1 次 `getAllLatestSensorData()`
- 移除 `runConcurrent` 并发控制逻辑（不再需要挨个请求）
- 保持 `CumulativeEnergyService` 调用不变（仍是 `/today-energy`）

#### 3. `frontend/src/api/light.js` — 使用 Redis 读取（前端无需改动）

---

## 不受影响的服务（验证确认）

| Service | 说明 |
|---------|------|
| **CumulativeEnergyService** | 纯内存 ConcurrentHashMap，依赖 `ingest()` 同步调用 `accumulate()`，维持不变 |
| **CarbonService** | 凌晨 2:30 计算昨日数据，batch writer 5 秒窗口不影响 |
| **MqttPublishService** | 只处理控制命令下行，与传感器写入路径无依赖 |
| **AlertCheckService** | 核心逻辑不变，触发方式从同步改为 `executor.submit()`，逻辑完全一致 |

## 实施步骤及任务列表

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `pom.xml` | 编辑 | 添加 redis + pool2 依赖 |
| 2 | `application.properties` | 编辑 | 添加 Redis 配置 |
| 3 | `RedisConfig.java` | **新建** | RedisTemplate 配置类 |
| 4 | `SensorDataMapper.java` + XML | 编辑 | 新增 `insertBatch` 方法 |
| 5 | `SensorDataBatchService.java` | **新建** | 批量写入 + 攒批调度 |
| 6 | `SensorDataIngestServiceImpl.java` | **编辑** | 核心改造：写 Redis + 入队列 + 异步告警 |
| 7 | `SensorDataServiceImpl.java` | **编辑** | 读取改造：优先 Redis，新增 getAllLatest |
| 8 | `SensorDataController.java` | **编辑** | 新增 `/latest/all` 批量接口 |
| 9 | `LightServiceImpl.java` | **编辑** | 路灯元数据 Redis 缓存 |
| 10 | `frontend/src/api/sensor.js` | 编辑 | 新增 `getAllLatestSensorData()` |
| 11 | `frontend/src/views/monitor/RealtimeMonitor.vue` | **编辑** | N 次 → 1 次批量请求 |

## 验证方案

1. **后端编译**：`mvn clean compile` 确认无编译错误
2. **Redis 连通性**：启动 Redis，确认 `RedisTemplate` 正确连接
3. **写入验证**：
   - 启动模拟器发送 MQTT 消息
   - 确认 Redis `sensor:latest:{id}` 中有数据
   - 确认 batch writer 每 5 秒将数据写入 MySQL
4. **读取验证**：
   - 在前端打开实时监控页面
   - 确认列表和地图正常显示数据
   - 确认 `/api/sensor-data/latest/all` 一次返回全部数据
5. **碳减排验证**：
   - 确认第二天的碳减排计算正常
6. **Git 提交前**：所有推送到 GitHub 的操作会先征得同意