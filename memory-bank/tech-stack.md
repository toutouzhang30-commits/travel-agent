# 旅游 Agent 技术栈（统一阶段版）

## 1. 文档目的
这份技术栈文档不只回答“用什么技术”，还回答：
- 为什么这些技术适合当前项目
- 这些技术在目标架构里分别承担什么角色
- 当前哪些技术已经落地，哪些还只是迁移方向

因此本文档会同时保留：
- **目标态技术路线**
- **当前代码现状与阶段性落地强度**

---

## 2. 当前项目的技术目标
根据最新 PRD、架构文档和实施计划，这个项目的核心已经不是“做一个能聊天的旅游页面”，而是做一个：
- 具备网页聊天体验的旅游 Agent
- 能做语义路由而不是关键词分流
- 能同时利用内部知识与外部证据
- 能通过 Spring AI `@Tool` 主线处理实时问题
- 能用 Working Memory 和 Reflection loop 持续修正结果

因此技术栈需要优先满足以下要求：
1. 能支撑 Web 聊天页 + SSE 流式过程展示
2. 能支撑结构化输出、强类型路由和 itinerary 生成/修改
3. 能支撑内部知识库与外部证据的混合检索
4. 能支撑 Spring AI `@Tool` 主线，并保留后续 MCP 扩展空间
5. 能支撑 Working Memory、Reflection 和后续持久化

---

## 3. 推荐技术栈总览

| 层级 | 推荐技术 | 在本项目中的角色 | 当前状态 |
| --- | --- | --- | --- |
| 开发语言 | Java 21 | 后端主语言 | 已确定 |
| 后端框架 | Spring Boot 4 | 应用启动、配置、整合主框架 | 已确定 |
| Web 层 | Spring MVC | 页面与 API 主栈 | 已落地 |
| 页面模板 | Thymeleaf | MVP 网页聊天页 | 已落地 |
| 前端交互 | 原生 JavaScript | 发送消息、消费 SSE、更新页面 | 已落地 |
| 流式输出 | SSE | 展示用户可见执行过程 | 已落地 |
| AI 编排基础 | Spring AI | 结构化输出、模型调用、后续工具整合 | 已落地 |
| 模型接入 | DeepSeek OpenAI 兼容 API | 当前主模型路径 | 已落地 |
| 内部知识库 | Spring AI PgVector | 内部 RAG 知识检索 | 已完成最小闭环 |
| RAG 入库幂等 | PostgreSQL manifest 表 + JdbcTemplate | 控制启动入库、重建和 active run | 当前稳定性补强 |
| 网页治理采集 | RestClient + Jsoup | 受控获取和清洗公开网页知识 | 目标态，未完整落地 |
| 外部证据能力 | 受控网页采集 / 后续可选 MCP search/browser | 为高级 RAG 补 freshness 与覆盖率 | Phase 3B 目标，MCP 未落地 |
| 主工具层 | Spring AI `@Tool` | 实时能力主工具调用方式 | 当前主线 |
| 后续工具协议 | MCP（可选） | 未来外部工具协议扩展 | 后续选项，当前非必须 |
| 过渡工具底座 | 本地 service/client + 统一工具网关 | 承接天气等已存在能力，向 `@Tool` 主线过渡 | 部分已存在 |
| Working Memory | 内存态 SessionState 扩展 | 支撑多轮路由、检索、工具、反思 | 现状偏薄，需升级 |
| Reflection | Java 规则 + LLM 结构化校验 | 有界自检与局部修正 | 目标态，未入主链路 |
| 业务数据存储 | MySQL 8 | 会话、版本、来源快照等持久化 | 目标态，未进入主线 |
| ORM | Spring Data JPA | 正式持久化阶段的数据访问 | 依赖已在项目中 |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc | 单元测试、接口测试 | 适用 |
| 构建 | Maven | 构建和依赖管理 | 已使用 |
| 部署 | Docker + Nginx | 后续部署与反向代理 | 目标态 |
| 可观测性 | Actuator + Micrometer | 健康检查、链路监控、工具/检索指标 | 后续阶段 |

---

## 4. 为什么这套技术栈适合当前项目

### 4.1 适合当前 Web 聊天式 Agent 形态
当前项目已经明确要保留：
- 网页聊天页
- SSE 事件流
- 后端主导的编排逻辑

所以 Web 层继续采用：
- Spring MVC
- Thymeleaf
- 原生 JavaScript
- SSE

这条路线的优点是：
- 实现路径短
- 与当前仓库状态一致
- 更适合快速验证“聊天 + 检索 + 工具 + itinerary + 过程展示”的整体闭环
- 不会过早把注意力带到复杂前端工程化上

### 4.2 适合结构化路由与 itinerary 生成/修改
项目的关键问题已经不是“调用模型能不能回话”，而是“系统能不能稳定判断当前到底该干什么”。

Spring AI 在这里最重要的价值不是 Prompt 拼接，而是：
- 结构化输出
- 强类型 schema
- 便于接入 itinerary 生成 / itinerary 修改 / 路由结果输出
- 为后续工具和检索编排提供统一入口

因此，Spring AI 依旧是合适的主 AI 框架。

### 4.3 适合混合 RAG
旅游知识不应该只靠内部向量库，也不应该完全依赖外部搜索。

所以技术上需要两层能力：

#### A. 内部知识库
- Spring AI PgVector
- 受控文档入库
- 元数据过滤
- 后续多路召回与重排
- PostgreSQL manifest 表记录内容指纹、pipeline 指纹、active run 和入库状态

它负责稳定、可治理、可复用的旅游知识。

#### C. 入库幂等与重建
- 使用 `JdbcTemplate` 管理 `rag_ingestion_manifest` 表。
- 使用 SHA-256 计算 `rag/*.md` 文件名 + 内容的 `content_hash`。
- 使用配置化 `pipeline-version`、chunk 策略、metadata 字段、embedding model / dimensions 生成 `pipeline_hash`。
- 只有 manifest 为 `COMPLETED` 且两个 hash 都一致时，才跳过启动入库。
- 通过 `IN_PROGRESS / COMPLETED / FAILED` 状态和事务边界避免半完成入库被误认为成功。

#### B. 外部证据能力
- 受控网页采集，以及后续可选 MCP search / browser 等能力
- 用于补 freshness、补覆盖、补知识库缺口

它负责让系统在知识库未覆盖时仍能拿到新鲜证据，但这些证据仍需进入来源、排序和不确定性约束。

### 4.4 适合工具层分阶段落地
当前阶段工具调用主线应是 Spring AI `@Tool`，MCP 只作为后续可选外部工具协议扩展。

但从当前仓库看：
- 还没有真实 MCP integration
- 已有天气 API service/client
- 现有工具仍更像本地 service 调用

所以技术路线不应写成“当前就是 MCP-first fully integrated”，而应写成：
- **Spring AI `@Tool` 是当前工具调用主线**
- 当前先保留本地工具底座
- 先通过统一工具网关让编排器面向统一接口
- 后续如确有需要，再逐步接入真实 MCP tool 能力

这比“一步到位重做所有工具集成”更稳。

### 4.5 适合 Working Memory 与 Reflection loop
旅游 Agent 不是一次性回答系统，多轮上下文很重要。

因此技术栈必须支撑：
- 内存态 Working Memory
- 结构化反思结果
- 有界修正循环

这里当前最合适的路线是：
- 先用内存态 `SessionStateStore` 演进为 Working Memory 容器
- Reflection 用 Java 规则 + LLM 结构化校验组合
- 正式持久化延后到 MySQL 阶段

这样既能尽快把链路跑通，也不会把当前阶段拖入过重的持久化设计。

---

## 5. 分层技术角色

### 5.1 Web 层
**推荐技术：** Spring MVC + Thymeleaf + 原生 JavaScript + SSE

**职责：**
- 渲染聊天页
- 接收用户消息
- 消费结构化 SSE 事件
- 展示消息、来源、工具状态和 itinerary

**不承担：**
- Prompt 拼接
- 工具编排
- 路由决策
- 状态持久化逻辑

### 5.2 编排层
**推荐技术：** Spring Boot Service + Spring AI

**职责：**
- 读取 Working Memory
- 执行结构化路由
- 调度检索 / 工具 / itinerary 生成与修改
- 执行 Reflection loop
- 组织 SSE 输出

### 5.3 内部知识层
**推荐技术：** Spring AI PgVector

**职责：**
- 保存稳定旅游知识
- 按城市、主题等条件过滤
- 为知识问答、行程生成和修改提供基础检索能力

### 5.4 外部证据层
**推荐技术：** 受控网页采集 + 后续可选 MCP search / browser 能力 + 治理流程

**职责：**
- 补知识库没有的公开信息
- 补 freshness
- 为高价值内容提供后续治理入库候选

### 5.5 主工具层
**当前主线技术：** Spring AI `@Tool`

**后续可选技术：** MCP

**职责：**
- 提供天气、路线、票价、搜索、浏览等能力
- 输出规范化工具结果、来源、时间和失败状态
- 与 SSE 的 `tool_call` / `tool_result` 对齐

### 5.6 Working Memory
**当前推荐技术：** 内存态 SessionState 扩展

**职责：**
- 保存当前 requirement profile
- 保存 itinerary 摘要
- 保存最近检索 / 工具 / 反思上下文
- 为下一轮路由和修正提供上下文基础

### 5.7 Reflection
**推荐技术：** Java 规则校验 + LLM 结构化校验

**职责：**
- 检查结果是否满足用户约束
- 检查知识或工具证据是否不足
- 检查修改是否落实
- 触发一次有界修正

### 5.8 持久化层
**推荐技术：** MySQL 8 + Spring Data JPA

**职责：**
- 保存正式会话
- 保存 itinerary 版本
- 保存来源快照
- 保存后续可恢复状态

**当前状态：**
- 还不是主链路优先项
- 需在 Working Memory 路线稳定后再正式接入

---

## 6. 当前技术取舍建议
### 当前应该优先坚持的
1. Spring MVC 主栈
2. SSE 事件流
3. Spring AI 结构化输出能力作为路由主基础
4. 先修复 RAG Manifest 幂等入库，避免重复切分、重复 embedding、重复写库
5. 先替换关键词/限制词主分流，再扩展高级 RAG
6. PgVector 作为内部知识库路线
7. 内存态 Working Memory 先行，优先服务路由上下文
8. Spring AI `@Tool` 作为当前工具主线，MCP 仅作为后续可选扩展
9. Reflection 尽早进入主链路

### 当前不建议优先推进的
- React / Vue 等复杂前端框架
- 微服务拆分
- 重量级 workflow 引擎
- 把所有外部网页搜索直接等价成 RAG
- 把 MCP 写成“现在必须落地”或“已经完全可用”的现状
- 在主分流还不稳定时先铺开大量新工具
- 在关键词主分流还存在时优先扩展 Phase 3B 高级 RAG
- PDF 导出

---

## 7. 当前代码与技术栈的对应关系
### 已经和路线一致的部分
- Java 21
- Spring Boot
- Spring MVC
- SSE
- Thymeleaf
- Spring AI
- DeepSeek OpenAI 兼容 API
- PgVector 基础方向
- 最小天气 API service/client 底座

### 还需要补齐的部分
- RAG Manifest 幂等入库、pipeline version 和 active run 检索过滤
- 结构化路由真正落地，并替换关键词/限制词主分流
- RAG / Tool / itinerary 统一由路由结果驱动
- Working Memory 扩展，先服务路由上下文
- Reflection loop 主链路化
- 统一工具网关
- 后续可选的 MCP integration
- 外部证据能力
- 路由稳定后的混合 RAG 排序与压缩

---

## 8. 最终推荐技术路线
如果只用一句话总结当前阶段最合适的技术栈策略，那就是：

- **Web 与交互**：Spring MVC + Thymeleaf + 原生 JavaScript + SSE
- **模型与结构化输出**：Spring AI + DeepSeek OpenAI 兼容 API，先用于结构化路由
- **内部知识库**：Spring AI PgVector
- **入库一致性**：PostgreSQL manifest 表 + JdbcTemplate + content/pipeline hash
- **外部证据与工具方向**：Spring AI `@Tool` 当前优先，MCP 后续可选；高级 RAG 在路由稳定后补强
- **上下文与修正**：内存态 Working Memory + Java/LLM 结合的 Reflection loop，优先支撑路由和局部修正
- **持久化**：后续以 MySQL + JPA 承接正式状态

这条路线的关键优点是：**与当前仓库状态衔接自然，同时能支撑从“聊天骨架”演进到“真正旅游 Agent”。**
