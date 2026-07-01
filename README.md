# 智慧路灯管理系统 🌟

Smart Street Light Management System — 基于 Spring Boot 3 + MyBatis-Plus 的后端管理系统。

## 📋 项目简介

智慧路灯管理系统是一个用于城市路灯设备集中监控和管理的后端服务平台。系统提供路灯设备的远程控制、传感器数据采集与监控、设备故障报警、用户权限管理等核心功能。

## 🏗️ 技术栈

| 技术 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.0 |
| MyBatis-Plus | 3.5.5 |
| MySQL | 8.0+ |
| Maven | 3.8+ |
| Lombok | 1.18.30 |

## 📁 项目结构

```
backend/
├── src/main/java/com/smartlight/backend/
│   ├── common/               # 通用工具类
│   │   ├── Result.java           # 统一API响应封装
│   │   └── GlobalExceptionHandler.java  # 全局异常处理器
│   ├── config/               # 配置类
│   │   ├── MyBatisPlusConfig.java  # MyBatis-Plus 配置（分页、自动填充）
│   │   └── WebMvcConfig.java      # Web MVC 配置（CORS跨域）
│   ├── entity/               # 数据实体
│   │   ├── Light.java            # 路灯设备实体
│   │   ├── SensorData.java       # 传感器数据实体
│   │   ├── Alert.java            # 报警信息实体
│   │   └── User.java             # 系统用户实体
│   ├── mapper/               # MyBatis-Plus Mapper 接口
│   ├── service/              # 业务层接口
│   │   └── impl/                 # 业务层实现
│   ├── controller/           # API 控制器
│   │   ├── LightController.java      # 路灯管理
│   │   ├── SensorDataController.java # 传感器数据
│   │   ├── AlertController.java      # 报警管理
│   │   └── UserController.java       # 用户管理
│   └── dto/                  # 数据传输对象
├── src/main/resources/
│   ├── application-example.properties  # 配置模板
│   ├── mapper/               # MyBatis XML 映射文件
│   └── sql/schema.sql        # 数据库建表脚本
└── pom.xml
```

## 🚀 快速开始

### 前置条件

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

### 1. 克隆项目

```bash
git clone https://github.com/KirisameMashiro/Smart-Street-Light-System.git
cd Smart-Street-Light-System
```

### 2. 创建数据库

先确保 MySQL 服务运行中，然后执行初始化脚本：

```bash
# 登录 MySQL
mysql -u root -p

# 在 MySQL 中执行
source backend/src/main/resources/sql/schema.sql
```

或者直接用 MySQL 命令行：

```bash
mysql -u root -p < backend/src/main/resources/sql/schema.sql
```

### 3. 配置数据库连接

复制配置模板并修改：

```bash
cp backend/src/main/resources/application-example.properties backend/src/main/resources/application.properties
```

编辑 `application.properties`，修改数据库密码：

```properties
spring.datasource.password=你的数据库密码
```

### 4. 启动应用

```bash
cd backend
mvn spring-boot:run
```

应用默认启动在 **http://localhost:8090**

## 📡 API 接口

### 路灯管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/lights` | 获取所有路灯 |
| GET | `/api/lights/page` | 分页查询路灯 |
| GET | `/api/lights/{id}` | 获取路灯详情 |
| POST | `/api/lights` | 新增路灯 |
| PUT | `/api/lights` | 更新路灯 |
| DELETE | `/api/lights/{id}` | 删除路灯 |
| POST | `/api/lights/batch-switch` | 批量开关灯 |
| PUT | `/api/lights/{id}/brightness` | 设置亮度 |
| GET | `/api/lights/stats` | 路灯状态统计 |

### 传感器数据

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sensor-data/page` | 分页查询传感器数据 |
| GET | `/api/sensor-data/latest/{lightId}` | 获取最新传感器数据 |
| GET | `/api/sensor-data/average/{lightId}` | 获取平均传感器数据 |
| POST | `/api/sensor-data` | 新增传感器数据 |

### 报警管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alerts/page` | 分页查询报警 |
| GET | `/api/alerts/{id}` | 获取报警详情 |
| PUT | `/api/alerts/{id}/handle` | 处理报警 |
| GET | `/api/alerts/unhandled-count` | 获取未处理报警数 |

### 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/users/login` | 用户登录 |
| GET | `/api/users` | 获取用户列表 |
| POST | `/api/users` | 新增用户 |
| PUT | `/api/users` | 更新用户 |
| DELETE | `/api/users/{id}` | 删除用户 |

### 默认账户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 系统管理员 |
| operator | 123456 | 运维人员 |

## 🗄️ 数据库表结构

- **user** — 系统用户表
- **light** — 路灯设备表
- **sensor_data** — 传感器数据表
- **alert** — 报警信息表

详细建表语句见 `backend/src/main/resources/sql/schema.sql`

## 📞 联系

项目维护者：KirisameMashiro
