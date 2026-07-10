# MQTT 传感器数据上报操作说明

## 概述

MQTTX（Windows）通过 EMQX Broker（Ubuntu VM）向 Spring Boot 后端发布传感器数据，后端自动入库并触发告警检测。

---

## Step 1: MQTTX 连接配置

| 参数 | 值                     |
|------|-----------------------|
| Broker 地址 | `tcp://your-broker-url:1883` |
| 端口 | `1883`                |
| Client ID | 自动生成（或自定义）            |

---

## Step 2: Topic 格式

```
smartlight/sensor/{lightId}
```

- `smartlight/` — 固定前缀（`application.properties` 中 `mqtt.topic-prefix`）
- `sensor/` — 传感器数据类型
- `{lightId}` — 路灯 ID，必须是数据库中已有的 ID（见 `light` 表）

示例：
| 路灯 ID | Topic |
|---------|-------|
| 1 | `smartlight/sensor/1` |
| 5 | `smartlight/sensor/5` |
| 21 | `smartlight/sensor/21` |

---

## Step 3: JSON 消息体格式

```json
{
  "lightId": 1,
  "illuminance": 12000,
  "power": 150.00,
  "voltage": 220.5,
  "current": 0.680,
  "temperature": 32.5,
  "humidity": 65.0,
  "collectTime": "2026-07-04 14:30:00"
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `lightId` | 数字 | **是** | 路灯 ID，对应数据库中存在的路灯 |
| `illuminance` | 数字 | 否 | 光照强度（lux） |
| `power` | 数字 | 否 | 当前功率（W） |
| `voltage` | 数字 | 否 | 电压（V） |
| `current` | 数字 | 否 | 电流（A） |
| `temperature` | 数字 | 否 | 温度（°C） |
| `humidity` | 数字 | 否 | 湿度（%RH） |
| `collectTime` | 字符串 | 否 | 采集时间，格式 `yyyy-MM-dd HH:mm:ss`，不填则用当前时间 |

---

## Step 4: 测试场景

### 场景 1：正常数据（不触发告警）

```json
{"lightId":1,"voltage":220,"current":0.68,"temperature":30,"power":150}
```

结果：数据入库，不生成告警。

### 场景 2：电压偏低（触发 light_fault 紧急告警）

```json
{"lightId":1,"voltage":20,"current":0}
```

匹配规则 `电压<50V` → 灯故障 → 告警级别 **4（紧急）**。

### 场景 3：温度过高（触发 env_warning 一般告警）

```json
{"lightId":1,"temperature":40}
```

匹配规则 `温度>37°C` → 高温预警 → 告警级别 **2（一般）**。

### 场景 4：功率异常（触发 power_overload 一般告警）

```json
{"lightId":1,"power":200}
```

匹配规则 `功率>额定值*1.2` → 假设额定功率 150W → 200 > 180 → 告警级别 **2（一般）**。

### 场景 5：光照偏低（触发 sensor_abnormal 严重告警，仅在白天）

```json
{"lightId":1,"illuminance":500,"collectTime":"2026-07-04 12:00:00"}
```

匹配规则 `照度<10000lux(白天)` → 当前时间 12:00（白天） → 告警级别 **3（严重）**。

---

## Step 5: 查看结果

### 后端日志

启动后端后，日志会依次出现：

```
MQTT 收到消息: topic=smartlight/sensor/1, payload={...}
传感器数据入库成功: lightId=1
告警已生成: id=8, lightId=1, type=5, level=4, message=...
WebSocket 告警推送完成: alertId=8, 已发送 1/1 个客户端
```

### 前端页面

打开浏览器 → `http://localhost:5173` → 告警管理页面，新告警会实时自动出现（无需手动刷新）。

---

## 异常处理

| 问题 | 结果 | 日志级别 |
|------|------|---------|
| JSON 格式错误 | 消息丢弃，继续接收后续消息 | ERROR |
| 缺少 `lightId` | 消息丢弃 | WARN |
| `lightId` 不存在 | 数据入库，跳过告警检测 | WARN |
| 非 JSON 内容 | 消息丢弃 | ERROR |
| EMQX Broker 断连 | 后端自动重连 | WARN |

---

## 前提条件

1. Ubuntu VM 中 EMQX 正在运行：`systemctl status emqx`
2. `application.properties` 中已启用 MQTT：
   ```properties
   mqtt.enabled=true
   mqtt.broker-url=tcp://your-broker-url:1883
   ```
3. Windows 与 Ubuntu VM 网络互通（NAT 模式下需保持网络连通）
4. 后端已启动（`BackendApplication`）
5. 数据库中已有对应的路灯记录（`light` 表）和告警规则（`alert_rule` 表）