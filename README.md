# 智慧路灯管理系统

基于 Spring Boot + Vue 3 的智慧路灯监控管理平台。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite + Element Plus + ECharts + Pinia |
| 后端 | Spring Boot 3.2 + MyBatis-Plus 3.5 |
| 数据库 | MySQL 8.0 |

## 快速启动

### 1. 数据库

MySQL 中执行 `backend/src/main/resources/sql/schema.sql`

### 2. 后端

```bash
cd backend
cp src/main/resources/application-example.properties src/main/resources/application.properties
# 编辑 application.properties，修改数据库密码
mvn spring-boot:run
```

启动于 `http://localhost:8080`

### 3. 前端

```bash
cd frontend
npm install
npm run dev
```

启动于 `http://localhost:5173`

### 4. 登录

| 用户 | 密码 | 角色 |
|------|------|------|
| admin | admin123 | 管理员 |
| operator | 123456 | 运维人员 |

## 功能模块

| 模块 | 说明 |
|------|------|
| 控制台 | 路灯状态统计、报警概览、图表展示 |
| 设备管理 | 路灯CRUD、分组统计、设备台账 |
| 实时监测 | 传感器数据查询、设备监控 |
| 照明控制 | 远程开关/调光、定时策略、阈值联动、操作日志 |
| 报警管理 | 报警列表、处理报警 |
| 碳减排分析 | 减排摘要、趋势图、路段对比 |
| AI 中心 | 预测调光、运维助手、知识库管理 |
| 系统管理 | 用户管理、系统配置、告警规则、操作审计 |

## 项目结构

```
├── backend/                  # Spring Boot 后端
│   └── src/main/java/com/smartlight/backend/
│       ├── controller/       # 12 个 Controller
│       ├── service/          # 业务层
│       ├── entity/           # 9 个实体
│       ├── mapper/           # MyBatis 映射
│       ├── dto/              # 数据传输对象
│       ├── config/           # CORS、分页等配置
│       └── common/           # 统一响应、异常处理
├── frontend/                 # Vue 3 前端
│   └── src/
│       ├── views/            # 18 个页面视图
│       ├── api/              # 11 个 API 封装模块
│       ├── router/           # 路由（含登录守卫、权限控制）
│       ├── store/            # Pinia 状态管理
│       └── utils/            # 工具函数
└── .gitignore
```