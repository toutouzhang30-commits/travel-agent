# 旅游 AI 助手架构文档（RAG + Tool-Calling Agent 版）

## 1. 当前目标架构
当前项目的目标已经从“验证 DeepSeek 最小对话调用”升级为一个面向 Web 的旅游规划代理系统，核心目标是同时解决三类问题：

1. **模型理解与组织表达**：负责理解用户意图、补齐需求、生成和修改 itinerary
2. **知识可信性**：通过 RAG 检索非结构化旅游知识，减少城市背景与攻略类内容的幻觉
3. **实时数据准确性**：通过工具调用接入地图、天气、价格等实时数据，减少时效性信息编造

因此，这个系统不再是单一聊天接口，而是一个：

- 带网页聊天界面的 Spring MVC 应用
- 带 SSE 流式事件输出的 Agent 系统
- 同时具备结构化目录、RAG 知识库和工具调用能力的旅游规划后端

---

## 2. 核心技术决策

### 2.1 Web 层
- 使用 **Spring MVC** 作为唯一 Web 主栈
- 使用 **SSE** 向网页端推送流式事件
- 使用 **Thymeleaf + HTML + JavaScript** 构建 MVP 聊天页面
- 不引入 WebSocket
- 不同时维护 MVC 和 WebFlux 两套模式

### 2.2 AI 接入层
- 使用 **Spring AI + OpenAI 兼容接口** 连接 DeepSeek
- 继续保留一条真实模型接入路径，避免多 Provider 复杂度
- 模型主要负责：
  - 用户意图理解
  - 需求追问
  - 结合检索/工具结果组织回答
  - 结构化 itinerary 生成与修改

### 2.3 Agent 编排层
- 由单一聊天服务升级为 **Agent Orchestrator**
- Agent 主流程采用 **模型结构化路由 + 代码受控执行**：
  - 模型先输出小而稳定的路由决策
  - 路由决策必须绑定结构化输出 schema，不允许自然语言路由后再做字符串解析
  - 结构化输出解析失败必须进入明确 fallback
  - 代码根据路由决策执行 RAG、工具调用、行程生成或行程修改
  - 不再把关键词/限制词判断作为 RAG 或 Tool 的主分流机制
- 编排层负责：
  - 通过 `IntentRoutingService` 判断用户当前意图
  - 判断当前会话阶段
  - 识别是否需要继续追问
  - 判断是否需要做 RAG 检索
  - 判断是否需要调用工具
  - 组织 itinerary 生成或修改
  - 汇总来源、不确定性与最终响应

### 2.4 知识与数据层
系统数据分成三类：

#### A. 结构化业务数据
用于支撑正式业务对象和会话恢复：
- ChatSession
- ConversationTurn
- TripRequirementProfile
- ItineraryVersion
- ItineraryDay
- ItineraryItem
- SourceReference

**推荐存储：** MySQL

#### B. 结构化候选目录
用于城市、景点、标签、预算区间、来源等稳定结构化数据。

MVP 初期可先从静态种子数据开始，后续再持久化入库。

#### C. 非结构化知识库（RAG）
用于城市背景、区域玩法、旅游攻略摘要、季节建议等文本知识。

**当前决策：** 使用 Spring AI `PgVector` 作为正式向量存储，以满足持久化和后续检索扩展需求。

向量检索设计约束：
- 通过 Spring AI 自动装配 `EmbeddingModel` 与 `VectorStore`
- 向量维度必须与 embedding 模型输出维度严格一致
- RAG 文档需支持按城市、主题等元数据过滤
- 业务结构化数据与会话持久化仍与向量存储分层管理

#### D. Web 知识采集与高级 RAG
Phase 3 的最小闭环完成后，RAG 必须从“本地文档向量检索”升级为可治理的知识获取与检索架构：
- 支持受控网页采集，用于补充攻略、景点说明、官方公告、区域玩法等非实时知识
- 采集必须遵守来源白名单、robots/站点规则、速率限制和去重策略
- 网页内容进入知识库前必须经过清洗、抽取、切块、元数据标注和来源校验
- 检索不再只依赖单路向量召回，必须支持多路召回与重排

### 2.5 工具调用层
通过 Spring AI Function Calling / `@Tool` 机制接入真实外部能力。

当前工具调用决策：
- 实时数据能力使用 Spring AI `@Tool` 描述工具边界和参数 schema
- 工具执行采用受控执行方式，由编排器显式捕获工具调用、执行工具并生成 `tool_call` / `tool_result` SSE 事件
- 不把工具调用完全隐藏在模型内部自动执行流程中，避免前端无法展示代理执行过程

首批工具目标：
- **WeatherTool**：实时天气 / 天气摘要 / 出行提醒
- **MapsTool**：路线、距离、区域关系、交通耗时
- **PricingTool**：门票或价格参考

工具层必须满足：
- 有清晰输入/输出 DTO
- 有来源信息
- 有调用时间
- 工具失败时可显式暴露失败状态

### 2.6 短期记忆层
短期记忆不能等到 Phase 7 正式数据库持久化后才出现。它必须在 Agent 编排阶段提前成为主链路能力。

短期记忆至少包括：
- 当前需求画像：目的地、天数、预算、节奏、兴趣偏好
- 当前有效 itinerary 摘要
- 最近若干轮用户修改意图
- 最近一次 RAG 检索摘要与来源
- 最近一次工具调用摘要与失败状态

短期记忆的短期实现仍可使用内存态 `SessionStateStore`，但必须从“只保存 requirement / itinerary”升级为“可供路由、检索、工具调用和反思使用的会话工作记忆”。正式持久化仍放在 Phase 7。

### 2.7 反思与纠错层
Agent 不能只生成结果，还需要最小 Reflection 闭环，用于发现明显错误并触发修正。

Reflection 不暴露内部推理，只输出用户可见的校验结果和必要修正。它至少检查：
- 行程是否满足用户核心约束
- 天数、预算、节奏是否一致
- 是否错误使用过期或不适合的来源
- 工具失败时是否明确提示不确定性
- 路线、时间、节奏是否存在明显不合理
- 用户修改请求是否真的反映到新版本 itinerary

---

## 3. 推荐模块划分

```text
src/main/java/com/xingwuyou/travelagent/
  ├─ web/
  │   ├─ page/                 # 页面入口 Controller
  │   ├─ api/                  # 聊天 API / SSE API
  │   └─ dto/                  # 请求、响应、事件 DTO
  ├─ agent/
  │   ├─ AgentOrchestratorService.java
  │   ├─ AgentDecision.java
  │   ├─ AgentEvent.java
  │   ├─ routing/              # IntentRoutingService 与结构化路由决策
  │   └─ reflection/           # 结果校验、反思与纠错
  ├─ chat/
  │   ├─ model/                # 现有需求、itinerary 等领域对象可逐步迁移
  │   ├─ extraction/
  │   ├─ generation/
  │   └─ modification/
  ├─ retrieval/
  │   ├─ crawler/              # 受控网页采集与来源白名单
  │   ├─ document/
  │   ├─ ingest/
  │   ├─ recall/               # 多路召回
  │   ├─ rerank/               # 重排
  │   ├─ vector/
  │   └─ RetrievalService.java
  ├─ tools/
  │   ├─ weather/
  │   ├─ maps/
  │   ├─ pricing/
  │   └─ ToolResult.java
  ├─ source/
  │   ├─ SourceReference.java
  │   ├─ ConfidenceLevel.java
  │   └─ SourceAssembler.java
  ├─ session/
  │   ├─ SessionState.java
  │   ├─ SessionService.java
  │   ├─ ConversationMemory.java
  │   └─ WorkingMemoryService.java
  ├─ persistence/
  │   ├─ entity/
  │   ├─ repository/
  │   └─ mapper/
  ├─ config/
  │   ├─ ChatClientConfig.java
  │   ├─ VectorStoreConfig.java
  │   └─ ToolConfig.java
  └─ TravelAgentApplication.java
```

说明：
- 当前仓库里的 `chat` 包可以继续作为迁移起点
- 但后续不应再把所有职责继续堆到 `chat/DeepSeekChatService.java`
- 需要逐步把“编排、检索、工具、来源、会话”拆开

---

## 4. 请求链路（新目标）

### 4.1 网页聊天主链路

```text
浏览器聊天页
  ↓
发送消息（sessionId + message）
  ↓
POST /api/chat/stream
  ↓
Chat API Controller
  ↓
AgentOrchestratorService
  ├─ Intent Router（模型结构化路由）
  ├─ 短期工作记忆读取
  ├─ 需求完整度判断
  ├─ RAG 检索（多路召回 + 重排）
  ├─ @Tool 工具调用（由代码受控执行）
  ├─ itinerary 生成/修改
  ├─ Reflection 校验与必要修正
  └─ 来源与不确定性整合
  ↓
SSE 事件流返回浏览器
```

核心原则：

```text
用户消息 -> Intent Router -> 编排器 -> RAG / Tool / 行程生成 / 行程修改 -> SSE
```

模型负责理解语义并给出结构化路由；代码负责执行流程和边界控制。

### 4.2 SSE 事件建议
网页端应消费结构化事件，而不是只接自然语言文本。

建议最小事件类型：
- `status`
- `retrieval`
- `tool_call`
- `tool_result`
- `answer`
- `itinerary`
- `done`
- `error`

每类事件都应该是**用户可见、可解释的执行状态**，而不是内部隐藏思维过程。

---

## 5. 页面与 API 边界

### 5.1 页面层职责
页面层负责：
- 渲染聊天页面
- 展示消息流
- 展示工具状态
- 展示来源区
- 展示 itinerary 卡片

页面层不负责：
- 拼 Prompt
- 编排工具调用
- 存储业务状态

### 5.2 API 层职责
API 层负责：
- 接收网页端消息
- 启动 Agent 编排
- 返回结构化 SSE 事件或同步结果
- 统一处理参数错误与可恢复失败

---

## 6. 状态管理策略

### 6.1 当前短期策略
在正式持久化前，可先使用**内存态 session state**，但协议层必须切换到：

```json
{
  "sessionId": "...",
  "message": "杭州 3 天，预算 3000，轻松一点"
}
```

不再让前端把完整 `currentRequirement` / `currentItinerary` 作为正式协议长期保留。

### 6.2 后续正式策略
接入 MySQL 后，会话状态应从数据库恢复，包括：
- 历史对话
- 当前需求画像
- 当前有效 itinerary 版本
- 来源引用

---

## 7. RAG 的边界
RAG 在本项目中只解决**非结构化知识补充**问题，不解决所有问题。

RAG 的触发不再依赖穷举关键词表。主路径应由 `IntentRoutingService` 输出的结构化决策驱动，例如：
- 静态旅游知识、攻略、住宿区域、玩法建议：触发 RAG
- 实时天气、实时票价、路线耗时：不触发 RAG，交给工具层
- 行程生成或修改：由编排器根据目的地、主题和当前任务决定是否补充检索

### 7.1 高级 RAG 主链路
Phase 3B 后，RAG 主链路应升级为：

```text
受控网页采集 / 本地文档
  -> 内容清洗与结构化抽取
  -> 切块与元数据标注
  -> 向量索引 + 关键词索引 + 元数据索引
  -> 多路召回
  -> 重排
  -> 上下文压缩
  -> 带来源的回答 / itinerary 生成
```

多路召回至少包含：
- 向量召回：语义相关内容
- 关键词召回：景点名、区域名、专有名词
- 元数据过滤：城市、主题、来源类型、更新时间、可信度
- 会话上下文召回：结合当前目的地、偏好、已有 itinerary

重排至少按以下信号排序：
- 与用户问题的语义相关度
- 与当前会话目的地和主题的一致性
- 来源可信度
- 更新时间
- 内容片段完整性
- 是否能支撑最终回答或行程条目

### 7.2 适合进 RAG 的内容
- 城市背景
- 区域玩法说明
- 常见出游节奏建议
- 季节性攻略
- 非实时攻略摘要
- 官方景区介绍、交通说明、开放规则的稳定部分
- 高质量网页攻略中可被来源化引用的非实时内容

### 7.3 不适合进 RAG 的内容
- 实时天气
- 实时票价
- 路线耗时
- 高频变化营业状态

这些内容应交给工具调用层处理，而不是伪装成知识库答案。

### 7.4 网页采集边界
网页采集必须是受控的数据工程流程，不是运行时随意抓网页：
- 只采集白名单或明确配置的来源
- 记录 `sourceUrl`、`sourceName`、`fetchedAt`、`verifiedAt`、`contentHash`
- 不把未经清洗和校验的网页正文直接塞进 Prompt
- 不采集登录后、付费墙后、用户私有或权限不明的内容
- 对疑似过期内容降低可信度或排除

---

## 8. 来源与不确定性表达
系统输出必须能回答“这些信息从哪来、靠不靠谱、哪些要再确认”。

建议统一来源结构至少包括：
- `sourceType`
- `sourceName`
- `sourceUrl`
- `verifiedAt`
- `confidenceLevel`
- `summary`

当出现以下情况时，必须显式提示不确定性：
- 工具调用失败
- 来源缺失
- 来源过旧
- 多来源冲突
- 实时信息无法确认

---

## 9. 与当前代码的差距和迁移重点

### 9.1 当前代码仍可复用的部分
- [TravelAgentApplication.java](../src/main/java/com/xingwuyou/travelagent/TravelAgentApplication.java)
- [ChatClientConfig.java](../src/main/java/com/xingwuyou/travelagent/chat/config/ChatClientConfig.java)
- [ChatController.java](../src/main/java/com/xingwuyou/travelagent/chat/controller/ChatController.java)
- [ChatRequest.java](../src/main/java/com/xingwuyou/travelagent/chat/dto/ChatRequest.java)
- [ChatResponse.java](../src/main/java/com/xingwuyou/travelagent/chat/dto/ChatResponse.java)
- [AgentEvent.java](../src/main/java/com/xingwuyou/travelagent/chat/dto/AgentEvent.java)
- [AgentEventType.java](../src/main/java/com/xingwuyou/travelagent/chat/dto/AgentEventType.java)
- [TripRequirement.java](../src/main/java/com/xingwuyou/travelagent/chat/dto/TripRequirement.java)
- [RequirementCompletenessChecker.java](../src/main/java/com/xingwuyou/travelagent/chat/component/RequirementCompletenessChecker.java)
- [Itinerary.java](../src/main/java/com/xingwuyou/travelagent/chat/model/Itinerary.java)
- [ItineraryGenerator.java](../src/main/java/com/xingwuyou/travelagent/chat/component/ItineraryGenerator.java)
- [ItineraryModifier.java](../src/main/java/com/xingwuyou/travelagent/chat/component/ItineraryModifier.java)
- [AgentOrchestratorService.java](../src/main/java/com/xingwuyou/travelagent/chat/service/AgentOrchestratorService.java)
- [RagIngestionService.java](../src/main/java/com/xingwuyou/travelagent/chat/rag/ingest/RagIngestionService.java)
- [RagRetrievalService.java](../src/main/java/com/xingwuyou/travelagent/chat/rag/retrieval/RagRetrievalService.java)
- [RagAnswerGenerator.java](../src/main/java/com/xingwuyou/travelagent/chat/component/RagAnswerGenerator.java)

### 9.2 当前最需要推进的部分
- **实时工具层尚未落地**
  - 当前天气、票价、路线等问题仍只能走兜底提示，缺少真实工具调用能力
- **意图路由层尚未落地**
  - 当前 RAG / Tool 触发仍依赖关键词或限制词判断，下一步需要升级为模型结构化路由
- **Tool Calling 事件流尚未打通**
  - 页面与后端已支持 `tool_call` / `tool_result` 事件类型，但尚未接入受控 `@Tool` 执行链路
- **工具来源结构尚未统一**
  - 当前来源展示已覆盖 RAG 文档来源，下一步需要扩展到实时工具来源
- **RAG 质量优化属于后续增量优化项**
  - 当前 RAG 最小闭环已完成，但网页采集、多路召回、重排、上下文压缩和增量重建尚未落地
- **短期记忆能力仍偏薄**
  - 当前会话状态主要保存 requirement / itinerary，下一步需要升级为可支撑路由、检索、工具调用和反思的工作记忆
- **Reflection 闭环尚未落地**
  - 当前生成/修改后缺少自动校验与纠错步骤，后续需要在 Phase 5 纳入主链路

---

## 10. 当前明确不做的事情
为了保证新路线聚焦，当前明确不做：

1. 不做 PDF 导出
2. 不恢复 HTML 转 PDF 方案
3. 不引入 WebSocket
4. 不一开始就做复杂前后端分离
5. 不为了 Agent 化而引入重量级工作流引擎
6. 不把实时信息伪装成 RAG 结果
7. 不继续扩大单个聊天服务的职责
8. 不继续把关键词/限制词列表作为主分流方案

---

## 11. 一句话架构策略
**系统以 Spring MVC + SSE 网页聊天室为入口，以“模型结构化路由 + 代码受控编排”为核心，结合 RAG 检索非结构化旅游知识和受控 `@Tool` 获取实时数据，在可追溯来源与不确定性提示的约束下生成与修改旅游 itinerary。**
