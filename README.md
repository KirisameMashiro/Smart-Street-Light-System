# 智慧路灯管理系统

基于 Spring Boot 3 + Vue 3 + MQTT + Redis 的智慧路灯监控管理平台，支持远程开关/调光、定时策略、阈值联动、AI 预测调光、碳减排分析等功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus + ECharts + Pinia + Leaflet |
| 后端 | Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8.0 + Redis |
| 消息 | MQTT (Eclipse Paho) — 设备通信 |
| 缓存 | Redis — 传感器最新数据缓存、小时聚合写入、路灯列表缓存 |
| 模拟 | Python + Paho MQTT — 传感器数据模拟 |
| AI | DeepSeek API + 知识库 — 智能运维助手 |

## 项目结构

```
├── backend/                          # Spring Boot 后端
│   └── src/main/java/com/smartlight/backend/
│       ├── controller/               # REST 控制器
│       ├── service/                  # 业务逻辑层
│       ├── entity/                   # 数据实体
│       ├── mapper/                   # MyBatis 映射接口
│       ├── config/                   # MQTT、WebSocket、Redis、CORS 等配置
│       ├── scheduler/                # 定时任务（定时策略、碳减排计算）
│       └── common/                   # 统一响应、异常处理
├── frontend/                         # Vue 3 前端
│   └── src/
│       ├── views/                    # 页面视图
│       ├── api/                      # API 封装
│       ├── router/                   # 路由
│       ├── store/                    # 状态管理
│       └── utils/                    # 工具函数
├── backend-python/                   # 传感器数据模拟器
│   └── sensor_simulator.py           # 模拟路灯上报传感器数据
└── docs/                             # 文档
```

## 数据架构

```
传感器 MQTT 上报（每盏灯每 3~5 秒）
         │
         ▼
  SensorDataIngestService.ingest()
    ├── 从 Redis 读取上一周期功率（替代 SELECT MySQL）
    ├── 梯形积分法计算采样间隔耗电
    ├── 写入 Redis HSET → sensor:latest:{id}（最新数据缓存，300s TTL）
    ├── 写入 Redis HINCRBY → energy:today（今日累计耗电）
    ├── 累加至 CumulativeEnergyService（内存，毫秒级）
    └── 入队 SensorDataBatchService（内存缓冲区）
                 │ 每 5 秒 flush
                 ▼
  SensorDataBatchService.flushToDatabase()
    └── UPSERT → sensor_data_hourly（小时聚合表，每灯每小时 1 行）
         (不再写入原始 sensor_data 表，写入量降低 99.9%)

碳排放计算：
  CarbonService.computeDailyStats()
    └── 查 sensor_data_hourly（24 行/灯/天，替代全表扫描）

实时监控前端：
  RealtimeMonitor.vue
    ├── 路灯列表 → GET /api/lights（Redis 缓存 light:all）
    ├── 传感器数据 → GET /api/sensor-data/latest/all（Redis pipeline 批量读取）
    └── 今日耗电 → GET /api/sensor-data/today-energy（CumulativeEnergyService 内存）
```

## 快速部署

### 前置条件

- JDK 17+
- Node.js 18+
- MySQL 8.0
- Redis 6+（可选，不启动则自动降级为直接查 MySQL）
- MQTT Broker（可选，不启用则不下发命令到设备）

### 1. 数据库

```bash
# 创建数据库并导入表结构
mysql -u root -p < backend/src/main/resources/sql/schema.sql
```

### 2. 后端

```bash
cd backend

# 复制配置模板并修改数据库密码等参数
cp src/main/resources/application-example.properties \
   src/main/resources/application.properties

# 启动
mvn spring-boot:run
```

启动于 `http://localhost:8080`

### 3. Redis（推荐，大幅提升性能）

```bash
# Windows: 启动 Redis 服务（确保 redis-server 已安装）
redis-server

# 默认连接 localhost:6379，无密码
# 如 Redis 未启动，系统自动降级为直接读写 MySQL
```

### 4. 前端

```bash
cd frontend
npm install
npm run dev
```

启动于 `http://localhost:5173`，自动代理 API 到后端 8080 端口。

### 5. 传感器模拟器（可选）

```bash
cd backend-python

# 安装依赖
pip install paho-mqtt

# 复制配置文件
cp config.example.ini config.ini
# 编辑 config.ini，设置 MQTT Broker 地址和后端 API URL

# 启动模拟器
python sensor_simulator.py
```

模拟器会从后端 API 获取路灯列表，每 3-5 秒模拟上报一次传感器数据到 MQTT Broker，同时订阅控制命令主题来响应开关/调光操作。

### 6. 登录

| 用户 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 管理员 |
| operator | 123456 | 运维人员 |

## MQTT 主题约定

| 方向 | 主题 | 说明 |
|------|------|------|
| 设备 → 后端 | `smartlight/sensor/{lightId}` | 路灯上报光照、电压、电流等传感器数据 |
| 后端 → 设备 | `smartlight/control/{lightCode}` | 下发开关/调光控制命令 |

控制命令 JSON 格式：

```json
{"command":"switch",    "status":1,                "lightCode":"L001"}
{"command":"brightness","brightness":75,            "lightCode":"L001"}
{"command":"set",       "status":1,"brightness":80, "lightCode":"L001"}
```

## Redis 数据结构

| Redis Key | 类型 | 说明 | TTL |
|-----------|------|------|-----|
| `sensor:latest:{lightId}` | Hash | 每盏灯最新传感器数据 | 300s |
| `energy:today` | Hash | 今日累计耗电（Wh） | 持久化 |
| `light:all` | String(JSON) | 全量路灯列表缓存 | 300s |

## 数据库核心表

| 表名 | 说明 | 数据量 |
|------|------|--------|
| `light` | 路灯设备元数据 | 少量 |
| ~~`sensor_data`~~ | ~~原始传感器数据（不再写入）~~ | ~~旧数据保留，不增长~~ |
| `sensor_data_hourly` | 小时聚合数据（碳排放/趋势图用） | 每灯每天 24 行 |
| `carbon_stats` | 碳减排日统计数据 | 每路每天 1 行 |
| `alert` | 告警记录 | 按需增长 |
| `alert_rule` | 告警规则配置 | 少量 |
| `timed_strategy` | 定时策略 | 少量 |
| `threshold_control` | 阈值联动配置 | 少量 |

## 功能模块

| 模块 | 说明 |
|------|------|
| 控制台 | 路灯状态统计、报警概览、ECharts 图表 |
| 设备管理 | 路灯 CRUD、分组统计、设备台账、地图标注 |
| 实时监测 | 传感器数据实时曲线、设备监控（Redis 缓存加速） |
| 照明控制 | 远程开关/调光、定时策略、阈值联动、操作日志 |
| 报警管理 | 报警列表、处理报警、告警规则配置 |
| 碳减排分析 | 减排摘要、趋势图、路段对比（基于小时聚合表） |
| AI 中心 | 预测调光、DeepSeek 运维助手、知识库管理 |
| 系统管理 | 用户管理、系统配置、操作审计 |

## 配置说明

### 后端 `application.properties`

```properties
# 数据库
spring.datasource.url=jdbc:mysql://localhost:3306/smart_light
spring.datasource.username=root
spring.datasource.password=your_password

# Redis（可选，不启动则自动降级）
spring.data.redis.host=localhost
spring.data.redis.port=6379

# MQTT（可选，false 则仅更新数据库不下发命令）
mqtt.enabled=true
mqtt.broker-url=tcp://192.168.111.129:1883
```

### 模拟器 `config.ini`

```ini
[mqtt]
broker = 192.168.111.129
port = 1883

[backend]
api_url = http://localhost:8080
```

## 许可证

仅供学习参考。