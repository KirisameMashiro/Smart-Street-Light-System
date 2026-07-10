# 计划: 照明控制MQTT发布能力补全

## 背景

当前系统的照明控制流程存在严重缺失：**所有控制操作（开关、调光、定时策略、阈值联动）只更新了数据库，从未向MQTT Broker发送控制指令到实际的路灯设备。**

- 前端 RemoteControl.vue 通过 HTTP API 调用后端开关/调光
- 后端 LightServiceImpl/TimedStrategyScheduler/ThresholdControlServiceImpl 只修改数据库记录
- MqttConfig 已订阅 `smartlight/sensor/+` 接收传感器数据，但 **没有任何发布能力**
- MqttClient 是 `private` 字段，其他类无法使用

## 目标

在所有控制逻辑中增加MQTT发布环节，使控制命令能够下发到实际路灯设备。

## 实现方案

### 1. MQTT主题约定

| 方向 | 主题格式 | 说明 |
|------|----------|------|
| 接收(已有) | `smartlight/sensor/{lightCode}` | 传感器数据上报 |
| **发送(新增)** | `smartlight/control/{lightCode}` | 控制命令下发 |

### 2. 消息体格式 (JSON)

```json
{
  "command": "switch",
  "status": 1,
  "lightCode": "L001"
}
```
或
```json
{
  "command": "set",
  "status": 1,
  "brightness": 75,
  "lightCode": "L001"
}
```

### 3. 设计原则

- **优雅降级**: MQTT不可用时只记日志，数据库操作不受影响
- **发布在事务外**: MQTT发布放在`@Transactional`之后，避免MQTT超时回滚DB操作
- **空安全**: lightCode为null时跳过发布，记录警告

## 操作步骤

### 步骤1: 暴露MqttClient —— MqttConfig.java

在MqttConfig中添加getter方法，让其他Service能访问MqttClient实例和topic配置：

```java
public MqttClient getMqttClient() { return mqttClient; }
public boolean isMqttConnected() { return mqttClient != null && mqttClient.isConnected(); }
public String getTopicPrefix() { return topicPrefix; }
```

### 步骤2: 创建 MqttPublishService.java（新文件）

`backend/.../service/MqttPublishService.java`

封装所有MQTT发布逻辑，提供三个方法：
- `publishSwitchControl(lightCode, status)` — 发布开关命令
- `publishBrightnessControl(lightCode, brightness)` — 发布调光命令
- `publishCombinedControl(lightCode, status, brightness)` — 发布组合命令

全部包在try-catch中，失败只记日志不抛异常。

### 步骤3: 修改 LightServiceImpl.java

注入`MqttPublishService`：
- `batchSwitchStatus()`中，批量更新DB后，遍历每个灯调用 `publishSwitchControl(lightCode, status)`
- `setBrightness()`中，更新DB后，调用 `publishCombinedControl(lightCode, status, brightness)`

注意：发布操作在 `@Transactional` 事务提交 **之后** 执行（通过拆分方法或事务同步）。

### 步骤4: 修改 TimedStrategyServiceImpl.java

注入`MqttPublishService`：
- `applyStrategyImmediately()`中，每盏灯DB更新后调用 `publishCombinedControl(lightCode, 1, strategy.getBrightness())`
- `turnOffLightsForStrategy()`中，每盏灯DB更新后调用 `publishCombinedControl(lightCode, 0, 0)`

### 步骤5: 修改 TimedStrategyScheduler.java

注入`MqttPublishService`：
- `applyStrategyOn()`中，每盏灯DB更新后调用 `publishCombinedControl(lightCode, 1, strategy.getBrightness())`
- `applyStrategyOff()`中，每盏灯DB更新后调用 `publishCombinedControl(lightCode, 0, 0)`

### 步骤6: 修改 ThresholdControlServiceImpl.java

注入`MqttPublishService`：
- `autoLinkageControl()`中，每盏灯DB更新后调用 `publishCombinedControl(lightCode, targetStatus, targetBrightness)`

## 验证方法

1. **编译**: `cd backend && mvn compile` 确认无编译错误
2. **启动应用**: 运行 BackendApplication，观察日志中 MQTT 连接信息
3. **功能验证**:
   - 在前端远程控制页面点击开灯/关灯/调光，查看后端日志是否输出 `MQTT发布开关命令: topic=smartlight/control/XXX, status=1`
   - MQTT Broker 侧（若有 EMQX/Mosquitto 控制台）可看到 `smartlight/control/+` 主题的消息
4. **降级验证**: 关闭 MQTT Broker 后执行控制操作，确认数据库更新正常，日志输出 `MQTT未连接，跳过发布`

## 受影响的文件

| 文件 | 操作 |
|------|------|
| `backend/.../config/MqttConfig.java` | 修改(加getter) |
| `backend/.../service/MqttPublishService.java` | **新建** |
| `backend/.../service/impl/LightServiceImpl.java` | 修改(注入+发布) |
| `backend/.../service/impl/TimedStrategyServiceImpl.java` | 修改(注入+发布) |
| `backend/.../scheduler/TimedStrategyScheduler.java` | 修改(注入+发布) |
| `backend/.../service/impl/ThresholdControlServiceImpl.java` | 修改(注入+发布) |

> 注意：所有文件路径中的 `...` 代表 `src/main/java/com/smartlight/backend`