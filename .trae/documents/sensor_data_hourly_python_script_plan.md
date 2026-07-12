# 计划：检查后端的 Python 脚本是否与历史光照趋势页面所需数据匹配

## 一、Summary（摘要）

用户要求检查项目中的 Python 脚本是否能生成“历史光照趋势”页面（`/monitor/illuminance-trend`）及其内部传感器数据表格所需要的数据。当前页面调用后端接口 `/api/sensor-data/page`，后端已改为从 `sensor_data_hourly` 表读取数据。现有的 Python 脚本 `backend-python/sensor_simulator.py` 仅通过 MQTT 发送原始传感器数据，不直接写库；小时级聚合由后端 Java 服务 `SensorDataBatchService` 完成。由于用户之前已清空 `sensor_data_hourly` 表，页面目前无数据。因此需要新建一个 Python 脚本，直接向 `sensor_data_hourly` 表写入模拟历史数据，以便页面能展示图表和表格，然后运行该脚本。

## 二、Current State Analysis（现状分析）

### 2.1 前端需求
- 页面路径：`/monitor/illuminance-trend`，组件文件 `frontend/src/views/monitor/IlluminanceTrend.vue`。
- 调用接口：`getSensorDataPage(params)` → `GET /api/sensor-data/page`。
- 期望返回字段：`lightId`、`collectTime`、`illuminance`、`power`、`voltage`、`current`、`temperature`、`humidity`、`samplingEnergy`。
- 时间参数格式：`YYYY-MM-DDTHH:mm:ss`（前端）→ 后端接收 `String` 类型。

### 2.2 后端现状
- 控制器：`backend/src/main/java/com/smartlight/backend/controller/SensorDataController.java`
  - `GET /api/sensor-data/page` 调用 `SensorDataService.getPage(...)`。
- DTO：`backend/src/main/java/com/smartlight/backend/dto/SensorDataQueryDTO.java`
  - `startTime`、`endTime` 已改为 `String`。
- ServiceImpl：`backend/src/main/java/com/smartlight/backend/service/impl/SensorDataServiceImpl.java`
  - `getPage()` 已改为调用 `this.getBaseMapper().selectFromHourlyPage(...)`，从 `sensor_data_hourly` 表查询。
- Mapper：`backend/src/main/java/com/smartlight/backend/mapper/SensorDataMapper.java`
  - 新增 `selectFromHourlyPage(Long lightId, String startTime, String endTime)`。
- 数据库表：`sensor_data_hourly` 字段包括 `light_id`、`hour_start`、`avg_illuminance`、`avg_power`、`avg_voltage`、`avg_current`、`avg_temperature`、`avg_humidity`、`total_energy`、`data_count`、`max_power`、`min_power`。

### 2.3 Python 脚本现状
- 目录：`backend-python/`
- 文件：
  - `sensor_simulator.py`：纯 MQTT 模式，向后端发布 MQTT 消息，后端消费后写入 Redis 并聚合到 `sensor_data_hourly`。
  - `config.example.ini`：配置示例，包含 mysql、mqtt、simulator、backend 四个段。
- 结论：现有脚本无法直接、批量地生成历史数据到 `sensor_data_hourly`，尤其是在表被清空后。需要新建脚本。

## 三、Proposed Changes（计划变更）

### 3.1 新建 Python 脚本：批量生成历史小时聚合数据

**目标文件**：`backend-python/generate_hourly_history.py`

**功能**：
1. 读取 `config.ini` 中的 MySQL 连接配置。
2. 连接 MySQL，查询 `light` 表获取所有路灯 ID。
3. 为每盏路灯生成过去 N 天（按用户选择：14 天）的小时级聚合数据。
4. 模拟字段：
   - `hour_start`：整点时间（分钟秒为 0）。
   - `avg_illuminance`：0~1800 lux，白天高、夜晚低。
   - `avg_power`：开灯时 50~150 W，关灯时 0.5~5 W。
   - `avg_voltage`：215~225 V。
   - `avg_current`：根据功率/电压估算。
   - `avg_temperature`：25~40 °C。
   - `avg_humidity`：50~80 %RH。
   - `total_energy`：根据功率估算该小时累计耗电（Wh）。
   - `data_count`：模拟采样次数，例如 60~120。
   - `max_power`、`min_power`：在 avg_power 基础上浮动。
5. 使用 `INSERT INTO sensor_data_hourly ... ON DUPLICATE KEY UPDATE` 批量写入，避免重复。
6. 支持参数：
   - `--days`：生成多少天的数据（默认 14，按用户选择）。
   - `--clear`：写入前是否清空表（按用户选择：清空）。
   - 也可直接执行无需参数。

**字段映射关系（脚本 → 数据库 → 前端）**：

| 脚本生成 | 数据库字段 | 前端使用 |
|----------|------------|----------|
| hour_start | hour_start | collectTime |
| avg_illuminance | avg_illuminance | illuminance |
| avg_power | avg_power | power |
| avg_voltage | avg_voltage | voltage |
| avg_current | avg_current | current |
| avg_temperature | avg_temperature | temperature |
| avg_humidity | avg_humidity | humidity |
| total_energy | total_energy | samplingEnergy |

### 3.2 运行脚本

1. 确保 `backend-python/config.ini` 存在（从 `config.example.ini` 复制并确认 MySQL 密码正确）。
2. 安装依赖：`pip install pymysql`（若未安装）。
3. 执行脚本（按用户选择：14天并清空）：
   ```bash
   cd backend-python
   python generate_hourly_history.py --days 14 --clear
   ```
4. 脚本执行后，刷新前端页面 `http://localhost:5173/monitor/illuminance-trend`，应能看到图表和表格数据。

### 3.3 验证方式

1. 数据库验证：
   ```sql
   SELECT COUNT(*) FROM sensor_data_hourly;
   SELECT * FROM sensor_data_hourly ORDER BY hour_start DESC LIMIT 10;
   ```
2. 前端验证：访问历史光照趋势页面，选择“最近24小时”或“最近7天”，图表和表格应加载数据，不再报 `Validation failed` 或“暂无数据”。
3. 后端日志验证：请求 `/api/sensor-data/page` 成功返回 `200`，无异常。

## 四、Assumptions & Decisions（假设与决策）

1. **使用 `sensor_data_hourly` 表**：用户已说明 `sensor_data` 表已弃用，数据均来自 `sensor_data_hourly`。脚本只写入该表。
2. **使用 `pymysql` 直连数据库**：MQTT 模拟器依赖后端实时消费和聚合，写入效率低且历史数据无法补全。新建脚本直连 MySQL 写入小时聚合数据最稳妥。
3. **时间范围**：默认生成最近 7 天数据，覆盖页面上“最近1小时/6小时/24小时/7天”的快捷筛选。
4. **不修改现有 `sensor_simulator.py`**：保持 MQTT 模拟器用于实时场景，新建独立脚本用于批量生成历史数据。
5. **不提交 `config.ini`**：该文件已在 `.gitignore` 中，避免泄露数据库密码。

## 五、Execution Steps（执行步骤，待用户确认后实施）

1. 读取 `config.example.ini` 确认配置项。
2. 创建 `backend-python/generate_hourly_history.py`。
3. 确认 `config.ini` 存在且 MySQL 配置正确。
4. 运行脚本生成数据。
5. 验证数据库表数据量。
6. 刷新前端页面验证图表和表格。
