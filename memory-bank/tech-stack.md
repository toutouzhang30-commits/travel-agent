# 旅游 AI 助手 MVP 技术栈（Java 版）

## 1. 选型目标
根据当前 PRD 和实施计划，这个项目的核心是 **Web 聊天式旅游规划**，重点能力包括：
- 聊天式需求收集
- AI 动态追问与行程生成
- 多轮修改行程
- 展示来源与不确定性提示
- SSE 流式展示 Agent 可见执行过程
- 后续接入 RAG 与 Tool Calling

因此技术栈需要优先满足以下目标：
1. **快速完成 MVP**，避免过度设计
2. **适合 Java 主栈开发**，降低实现和维护成本
3. **支持流式聊天体验**，让用户在网页端看到逐步执行过程
4. **方便后续扩展 RAG、工具调用、会话持久化和来源展示**
5. **保证结果可追溯**，方便展示来源和不确定性说明

---

## 2. 推荐技术栈总览

| 层级 | 推荐技术 | 作用 | 选择原因 |
| --- | --- | --- | --- |
| 开发语言 | Java 21 | 后端主语言 | 现代 Java 版本，语法和性能都适合新项目 |
| 后端框架 | Spring Boot 4 | 应用启动、接口开发、配置管理 | Java 生态成熟，适合快速搭建业务系统 |
| Web 层 | Spring MVC | 提供页面和 API | 对当前 Web 聊天室 + SSE 场景足够，复杂度低于全响应式方案 |
| 页面模板 | Thymeleaf | 聊天页 HTML 渲染 | 适合 Java 单体 MVP，和 Spring MVC 配合简单直接 |
| 前端交互 | 原生 JavaScript | 发送消息、消费 SSE、更新 DOM | 当前页面交互简单，先不引入额外前端框架 |
| AI 集成 | Spring AI | 统一接入大模型、Prompt、结构化输出、后续 Tool Calling | 与 Spring 体系兼容，便于后续扩展 |
| 模型接入 | DeepSeek OpenAI 兼容 API | 真实模型调用 | 当前仓库已验证，先收敛到单一 provider 降低复杂度 |
| 向量检索 | Spring AI PgVector | RAG 检索 | 直接使用可持久化向量库，支撑跨重启保存与后续检索扩展 |
| 网页采集 | Spring `RestClient` + Jsoup | 受控抓取公开网页、解析 HTML、抽取正文 | 满足 Phase 3B 的白名单采集与内容清洗，复杂度低于直接引入浏览器自动化 |
| 高级 RAG | 多路召回 + 重排 + 上下文压缩 | 提升检索质量 | 避免只靠单路向量召回，支撑旅游专有名词、区域、主题和来源可信度排序 |
| 短期记忆 | 内存态 WorkingMemory / SessionState 扩展 | 路由、检索、工具调用和 Reflection 的上下文 | 必须早于正式持久化进入主链路 |
| Reflection | 轻量校验器 + LLM 结构化校验 | 检查结果是否满足约束并触发局部修正 | 避免行程生成后缺少纠错闭环 |
| 数据库 | MySQL 8 | 后续存储会话、行程版本、来源快照等业务数据 | 结构化业务数据与向量检索分层管理，避免职责混淆 |
| ORM | Spring Data JPA | 数据访问层 | 对 Java 项目开发效率高，适合后续持久化阶段 |
| 会话缓存 | 内存态 SessionStateStore | Phase 1/2 会话状态 | 当前先满足网页聊天室闭环，后续再持久化 |
| 流式输出 | SSE | 向网页端推送状态、回答、itinerary 等事件 | 比 WebSocket 更轻量，符合当前单向流式回复场景 |
| 可观测性 | Spring Boot Actuator + Micrometer | 健康检查、接口监控、工具/检索链路监控 | 便于后续上线准备 |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc | 单元测试、接口测试 | 与 Spring Boot 主栈天然配合 |
| 构建部署 | Maven + Docker + Nginx | 构建、打包、部署、反向代理 | Java Web 项目的标准化部署方式 |

---

## 3. 为什么这套技术栈适合当前 MVP

### 3.1 适合 Phase 2 的最小可演示网页聊天室
当前最优先目标不是做复杂前端工程，而是先完成：
- 可打开的聊天页面
- 输入消息后可调用后端流式接口
- 页面能逐步显示状态、回答和 itinerary

因此页面层优先采用：
- **Spring Boot + Thymeleaf**
- **原生 JavaScript**
- **SSE 流式输出**

这样可以最快做出：
- 聊天输入框
- 消息列表
- 状态区
- itinerary 展示区
- 来源区占位

比起一开始就引入 React、Vue、HTMX 或更复杂的样式体系，这条路线更贴合当前 Phase 2 的目标。

### 3.2 适合“行程生成 + 多轮修改”
这个项目不是一次性问答，而是：
- 用户输入初始需求
- AI 动态追问补齐信息
- 生成按天行程
- 用户继续修改
- AI 在已有结果基础上调整

所以后端核心不是简单 Controller，而是要有一层 **Agent 编排服务层**。Spring AI 与当前已有的编排组件适合承担这层职责，用来：
- 理解用户意图
- 组织会话上下文
- 输出结构化 itinerary
- 后续接入检索和工具结果

### 3.3 适合“来源展示”和“不确定性提示”
当前路线明确要求：
- 要显示来源
- 数据不可靠时要明确提示
- 不能假装准确

所以系统需要保留：
- 来源名称
- 来源链接
- 校验时间 / 更新时间
- 可信度标记

这类结构化信息适合在后续阶段放入 **MySQL** 管理，而不是完全依赖模型临时生成文本。

### 3.4 适合后续演进，不提前引入超前复杂度
当前项目会逐步演进到：
- Web 聊天室
- RAG 检索
- Tool Calling
- 会话持久化
- 来源与不确定性闭环

因此技术栈需要“**现在够简单，后续能扩**”。

选择 Spring MVC + Thymeleaf + 原生 JS + SSE，有几个直接好处：
1. 当前 Phase 2 实现成本低
2. 不妨碍后续继续保留 API 层
3. 页面和后端都在同一项目里，调试路径短
4. 后续如果页面复杂度变高，再拆前后端也还来得及

---

## 4. 推荐系统架构

推荐采用 **单体应用优先** 的方式：

```text
用户浏览器
   ↓
Spring Boot Web 应用
   ├─ 页面入口 Controller
   ├─ 聊天 API / SSE API
   ├─ Agent 编排服务
   ├─ 需求抽取 / 完整度判断
   ├─ itinerary 生成 / 修改
   ├─ 会话状态管理
   ├─ 后续接入 Retrieval / Tools / Source
   └─ 数据访问层（后续）
           ↓
         MySQL
           ↓
     DeepSeek OpenAI 兼容 API
```

这种结构的优点是：
- 上手快
- Java 项目结构统一
- 部署简单
- 页面和 API 可以清晰分层
- 后续还能逐步接入 RAG、Tools 和持久化

---

## 5. 核心模块建议

### 5.1 页面层
负责：
- 渲染聊天页面
- 展示消息列表
- 展示状态区
- 展示 itinerary 卡片
- 展示来源区占位

**推荐技术：** Thymeleaf + 原生 JavaScript

### 5.2 API 层
负责：
- 接收网页端消息
- 提供同步接口和流式接口
- 返回结构化 SSE 事件
- 统一处理参数错误和流式异常

**推荐技术：** Spring MVC Controller + SseEmitter

### 5.3 AI 编排层
负责：
- 识别用户意图
- 判断信息是否完整
- 动态追问
- 生成结构化行程
- 根据用户反馈修改行程
- 后续决定是否需要检索或调用工具

**推荐技术：** Spring AI + AgentOrchestratorService

### 5.4 状态管理层
负责：
- 按 sessionId 保存当前需求状态
- 保存当前有效 itinerary
- 为多轮聊天提供上下文

**当前推荐实现：** 内存态 SessionStateStore + WorkingMemoryService

短期工作记忆需要提前承担：
- 当前需求画像
- 当前 itinerary 摘要
- 最近用户修改意图
- 最近 RAG 检索摘要和来源
- 最近工具调用摘要和失败状态
- Reflection 发现的问题和修正结果

### 5.5 数据层
后续存储内容建议包括：
- ChatSession
- ConversationTurn
- TripRequirementProfile
- ItineraryVersion
- SourceReference

**推荐技术：** MySQL + JPA

### 5.6 高级 RAG 层
负责：
- 受控网页采集
- HTML 清洗与正文抽取
- 文档去重与内容 hash
- chunk 策略
- 向量召回
- 关键词召回
- 元数据过滤
- 会话上下文召回
- 重排
- 上下文压缩

**推荐技术：** Spring `RestClient` + Jsoup + Spring AI PgVector + 轻量重排服务

说明：
- 静态网页优先用 `RestClient` + Jsoup
- JavaScript 强依赖页面暂不作为 Phase 3B 默认范围
- 不把运行时临时爬网页作为问答主路径
- 采集数据必须先入库、治理、索引，再进入 RAG 检索

### 5.7 Reflection 层
负责：
- 校验 itinerary 是否满足用户约束
- 校验来源与结论是否匹配
- 校验工具失败是否被明确提示
- 校验修改请求是否真正落实
- 对明显问题触发局部修正

**推荐技术：** Java 规则校验 + Spring AI 结构化输出校验

---

## 6. MVP 阶段的技术取舍建议

### 建议先做
1. **单体架构**，不要一开始拆微服务
2. **Spring MVC**，不要同时维护 MVC 和 WebFlux 两套模式
3. **SSE**，不要先上 WebSocket
4. **Thymeleaf + 原生 JavaScript**，不要先上复杂前端框架
5. **内存态 session**，不要在 Phase 2 就先做正式持久化
6. **结构化事件流**，不要只返回大段自然语言
7. **保留 API 层**，不要把业务逻辑放进页面脚本里
8. **短期工作记忆**，不要等 Phase 7 数据库持久化才开始管理上下文
9. **结构化路由输出**，不要让模型输出自然语言后再做字符串解析
10. **高级 RAG 基础设施**，不要长期停留在单路向量召回

### 暂时不建议优先做
- React / Vue 等复杂前端工程化
- 会话列表和账户体系
- 微服务拆分
- Redis 集群
- 重量级工作流引擎
- PgVector 级别的向量数据库（已转为当前确定路线，不再列为暂缓项）
- PDF 导出

原因很简单：当前目标是先验证“**网页聊天室 + 流式 Agent 过程展示**”是否能顺畅跑通，而不是先做平台化能力。

---

## 7. 与当前项目的对应建议

从当前项目状态看，已经具备：
- Java 21
- Spring Boot
- Spring MVC
- Spring AI
- Maven
- DeepSeek 真实模型接入
- 同步 / 流式聊天 API
- 最小会话状态管理

这和上面的推荐方向是一致的。

对于当前阶段，建议继续做两个收敛：

### 7.1 页面层和 API 层分开
建议页面入口和聊天接口分开：
- 页面入口 Controller：负责返回 HTML 页面
- ChatController：负责 `/api/chat` 与 `/api/chat/stream`

这样职责更清晰，也更符合后续页面与接口并行演进的方向。

### 7.2 模型接入先收敛到单一路径
当前已验证 DeepSeek 路径，因此建议先继续使用：
- **DeepSeek OpenAI 兼容 API**

先保证主链路稳定，再考虑是否需要引入更多 provider 或本地模型调试方案。

---

## 8. 最终推荐版本

如果只给当前阶段一个最适合的技术栈方案，我建议是：

- **语言**：Java 21
- **后端**：Spring Boot 4 + Spring MVC
- **页面模板**：Thymeleaf
- **前端交互**：原生 JavaScript
- **AI 框架**：Spring AI
- **模型接入**：DeepSeek OpenAI 兼容 API
- **向量检索**：Spring AI PgVector（Phase 3）
- **数据库**：MySQL 8（Phase 7）
- **状态管理**：内存态 SessionStateStore（当前阶段）
- **流式输出**：SSE
- **测试**：JUnit 5 + Spring Boot Test + MockMvc
- **构建部署**：Maven + Docker + Nginx
- **监控**：Actuator + Micrometer

这套方案的核心特点是：**实现路径短、和当前仓库状态一致、能支撑后续 RAG + Tool Calling + 持久化演进。**
