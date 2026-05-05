# 旅游 Agent 架构文档（统一阶段版）

## 1. 文档目的
这份文档同时承担两件事：

1. 描述项目真正想演进到的**目标架构**
2. 诚实记录当前仓库的**实际现状**与**迁移路径**

它不是一张脱离代码的理想图，也不是对现有实现的机械抄写。它的作用是：
- 防止后续开发继续沿着“关键词分流 + 纯对话骨架”惯性前进
- 让 memory-bank 中的目标态、现状态和实施顺序保持一致
- 明确高级 RAG、Spring AI `@Tool`、Working Memory 和 Reflection loop 在系统中的定位

---

## 2. 系统目标
当前项目的目标已经从“验证真实 LLM 能否生成旅行对话和最小 itinerary”升级为一个面向 Web 的旅游 Agent 系统。

这个系统要同时解决四类问题：
1. **语义理解与路由**：识别用户当前是要补信息、问知识、查实时信息、生成行程还是修改行程
2. **知识可信性**：通过内部知识库和外部证据降低旅游知识类幻觉
3. **实时数据准确性**：通过工具层获取天气、路线、价格等强时效信息
4. **结果可修正性**：通过工作记忆和反思闭环，让结果能被持续修正，而不是一次性输出后失控

因此，这个系统不再只是一个聊天接口，而应是一个：
- 带网页聊天界面的 Spring MVC 应用
- 带 SSE 流式事件输出的 Agent 系统
- 同时具备知识层、工具层、工作记忆和反思闭环的旅游规划后端

---

## 3. 当前现状与差距
### 3.1 当前已经具备的能力
从现有仓库代码看，当前已经具备：
- Spring MVC + SSE 主链路
- Web 聊天页
- `sessionId + message` 协议
- 最小需求抽取
- 最小 itinerary 生成与修改
- 最小 RAG 闭环
- RAG 来源展示
- RAG Manifest 幂等入库与 active run 检索过滤
- 结构化路由主链路
- RAG / Tool / itinerary 受控编排闭环
- WeatherTool 受控工具调用与 SSE 状态展示
- 天气 API 调用底座

### 3.2 当前最主要的问题
当前架构距离目标态还有明显差距：
- RAG 仍偏单路向量检索，缺少评估基准、负样本、真实向量分数、中文词法召回、RRF 融合、专业重排和拒答阈值
- RAG 上下文压缩与来源治理还不完整
- MCP 在代码中还没有真实接入点，且不属于当前必须落地项
- 工作记忆仍太薄
- Reflection 还没有进入主链路

### 3.3 核心迁移原则
接下来所有改动都应遵守这些原则：
- **不再继续强化关键词主分流**
- **不让单个 orchestrator 再次膨胀成不可维护的大类**
- **不把实时问题伪装成 RAG 结果**
- **不把外部搜索结果直接等同于可信知识**
- **不暴露不可验证的隐藏推理**

---

## 4. 目标架构总览

```text
浏览器聊天页
  ↓
Spring MVC Controller
  ↓
AgentOrchestratorService
  ├─ Intent Routing
  ├─ Working Memory
  ├─ Requirement Handling
  ├─ Hybrid Retrieval
  ├─ Spring AI @Tool Layer
  ├─ Itinerary Generation / Modification
  ├─ Reflection Loop
  └─ Source / Uncertainty Assembler
  ↓
SSE 事件流
```

目标主线不是：
```text
关键词判断 -> if/else -> 调一个组件
```

而是：
```text
用户消息 -> 结构化路由 -> 受控编排 -> 检索/工具/生成/修改 -> 反思修正 -> SSE
```

---

## 5. 核心架构层

### 5.1 Web 层
**目标定位：**
- 使用 Spring MVC 作为唯一 Web 主栈
- 使用 SSE 提供流式事件
- 使用 Thymeleaf + HTML + JavaScript 提供 MVP 网页聊天页

**当前现状：**
- 这条路线已经基本成立，可继续保留

**约束：**
- 不引入 WebSocket
- 不同时维护 MVC 和 WebFlux
- 页面层不承担业务编排职责

### 5.2 编排层（Agent Orchestrator）
**目标定位：**
- 成为唯一主入口
- 所有业务分流都从这里发起，但不把所有细节写死在一个类里

**长期职责：**
- 读取 Working Memory
- 调用 Intent Routing
- 判断当前是补需求、知识问答、实时问答、生成还是修改
- 触发 Hybrid Retrieval
- 触发 Spring AI `@Tool` / 统一工具层
- 调用 itinerary 生成/修改下游能力
- 执行 Reflection loop
- 汇总最终输出与 SSE 事件

**当前现状：**
- 现有 `AgentOrchestratorService` 已是事实主入口
- 但当前仍深度依赖关键词布尔判断，不符合长期目标

### 5.3 路由层（Intent Routing）
**目标定位：**
- Intent Routing 是所有业务分流入口
- 用模型结构化路由替代关键词主分流
- 让路由输出进入强类型 schema，而不是字符串包含判断

**长期输出建议：**
- action
- retrievalMode
- toolIntent
- city
- topic
- timeScope
- confidence
- reason

**当前现状：**
- 已通过 `IntentRoutingService` 落地主链路结构化路由
- RAG / Tool / itinerary 生成和修改已经由结构化路由结果驱动
- `RagRetrievalDecider`、`ToolCallDecider`、`ItineraryModificationDetector` 只允许作为历史债务、短期 fallback 或 guard，不再承担主流程分流

**迁移方向：**
- 这些类只能短期保留为 fallback / guard
- 不应再作为长期主分流机制继续扩展
- 当前第一优先级已经转为 Phase 3B 高级 RAG：评估基准、负样本、多路召回、重排、拒答阈值和上下文压缩

### 5.4 Working Memory 层
**目标定位：**
- 让系统记住“当前在做什么”，而不是每轮只看一句用户消息
- Working Memory 是有限上下文窗口，不是无界历史仓库

**最少需要保存：**
- `stableProfile`：用户稳定需求画像
- `activeItinerarySummary`：当前 itinerary 摘要
- `recentTurns`：最近少量对话轮次
- `recentDecisions`：最近路由、检索、工具和反思摘要
- `volatileEvidence`：天气、票价、路线等有时效性的工具结果

**当前现状：**
- 内存态会话已经存在
- 但当前只保存 requirement / itinerary，两者不足以支撑新链路

**迁移方向：**
- 正式持久化前，先把 `SessionStateStore` 演进成 Working Memory 容器
- Working Memory 必须支持摘要压缩、容量驱逐、目的地切换清理和时效性失效
- 后续再映射到持久化模型

**压缩与驱逐原则：**
- RAG 只保存摘要、来源、主题、城市和时间，不保存完整 chunk。
- Tool 只保存参数、结果摘要、成功/失败、来源和更新时间，不保存完整原始响应。
- Reflection 只保存结论和修正动作摘要，不保存隐藏推理。
- `recentTurns` 和 `recentDecisions` 默认各保留最近 5 条，超出后驱逐或压缩。
- 用户切换目的地时，清理旧目的地强绑定的 RAG / Tool / itinerary 摘要。
- Phase 7 负责长期持久化，不能替代 Phase 5 的短期上下文压缩。

### 5.5 知识层（Hybrid Retrieval）
**目标定位：**
- 采用“内部知识库 + 外部证据补充”的混合检索路线

#### A. 内部知识库
用于承载：
- 城市背景
- 区域玩法
- 季节建议
- 整理过的攻略摘要
- 可长期保留的非实时知识

#### B. 外部证据能力
用于补充：
- freshness
- 知识库未覆盖的信息
- 可被进一步治理入库的高价值来源

**当前现状：**
- 已具备内部向量检索最小闭环
- 启动入库已具备数据库级 manifest 幂等保护，避免每次启动重复切分和写入 PgVector
- 检索已过滤当前 `active_run_id`，知识问答检索已收口为 city 优先，避免模型生成 topic 与入库 category 不一致导致 0 命中
- 还没有外部检索统一接入
- 还没有真实 PgVector score、Postgres FTS / Lucene BM25、RRF、DashScope Reranker、高级压缩和来源治理

**迁移方向：**
- 不用外部搜索完全替代内部知识库
- 不把运行时网页正文直接塞进最终 prompt 当结论
- 当前先让 vector 与 BM25 两路结果进入候选合并、专业重排、证据裁判、上下文注入和不确定性表达
- 词法召回第一版采用 Lucene BM25 内存索引，并预留后续 Postgres 中文 FTS（`zhparser` / `pg_jieba`）探测
- Reranker 第一候选为 DashScope `gte-rerank-v2`，失败时 fallback 到合并候选原始排序
- RRF / score fusion 和系统性分数评估放到 BM25 + Reranker + RagEvidenceJudge 链路稳定之后

目标 Hybrid Retrieval 链路：
```text
Vector Recall + BM25 Recall
  -> candidate union / dedup
  -> DashScope Reranker
  -> RagEvidenceJudge
  -> Context Injection
```

Reranker 负责排序，不负责判断证据是否足够回答；`RagEvidenceJudge` 是证据充分性判断层，不替代 reranker。`RagEvidenceJudge` 可以按“子代理式裁判”设计，但对外必须封装为受控 Spring Service，并输出结构化 DTO。第一版 judge 批处理策略固定为每批最多 5 条 snippet、约 3000 字符上限、最多 3 批，并停止于首个 `evidenceEnough=true` 的批次。

#### C. RAG 入库一致性
内部知识库已经具备基础稳定的启动入库策略：
- 使用 `rag_ingestion_manifest` 表记录 bootstrap 文档的 `content_hash`、`pipeline_hash`、`active_run_id` 和 `status`。
- `status=COMPLETED` 且内容指纹、处理策略指纹一致时，启动应跳过切分、embedding 和写入。
- 文档内容、chunk 策略、metadata 结构、embedding 模型或维度变化时，通过 `pipeline_hash` 触发重建。
- 重建时先写新 `run_id`，成功后切换 manifest 的 active run，再清理旧 run，避免失败后知识库为空。
- 检索优先过滤当前 `active_run_id`，避免新旧 chunk 混合命中。
- 当前知识问答检索不再使用模型生成的 topic 做精确硬过滤；topic 后续更适合作为 Phase 3B 重排信号。

### 5.6 工具层（Spring AI `@Tool` 优先，MCP 后续可选）
**目标定位：**
- 当前工具调用主线是 Spring AI `@Tool`
- 编排器应面向统一工具抽象，而不是面向很多零散本地服务
- MCP 可作为后续外部工具协议扩展选项，但当前不作为必须落地项

**当前现状：**
- 当前仓库里没有真实 MCP 接入
- 天气工具底座已存在，但它本质仍是本地 service 调用

**分阶段落地原则：**
- 文档要明确当前优先 Spring AI `@Tool`
- 同时保留未来接入 MCP 的扩展空间
- 第一阶段先把 Weather 能力纳入统一工具层抽象
- 后续如确有需要，再逐步接入 MCP search / browser / maps / pricing 能力

**工具层最少能力要求：**
- 明确输入/输出 schema
- 明确来源信息
- 明确调用时间
- 明确失败状态
- 明确可用于 SSE 的 `tool_call` / `tool_result` 表达

### 5.7 Reflection Loop
**目标定位：**
- Reflection 不再是后补优化，而是主链路的一部分

**目标作用：**
- 检查路由是否合理
- 检查知识回答是否证据不足却语气过强
- 检查工具失败是否被透明暴露
- 检查 itinerary 是否满足用户约束
- 检查修改是否真正落实

**推荐控制原则：**
- 有界循环
- 不无限重试
- 不暴露隐藏推理
- 只输出用户可见的修正过程和结果

**当前现状：**
- memory-bank 已经提出需要 Reflection
- 但当前代码主链路中尚未落地

---

## 6. 推荐模块边界（面向迁移）
在当前仓库基础上，建议优先形成下面这些语义边界：

```text
chat/
  ├─ controller/
  ├─ dto/
  ├─ service/
  ├─ routing/
  ├─ reflection/
  ├─ rag/
  ├─ tool/
  │   ├─ weather/
  │   └─ external/             # 后续可选 MCP / 外部工具协议适配
  ├─ session/
  │   └─ model/
  ├─ component/
  ├─ model/
  └─ config/
```

说明：
- 当前 `chat/...` 可以继续作为迁移根包
- 本轮重点不是大范围搬包，而是先把“新能力应该归哪层”写清楚
- 后续新增类应优先进入 `routing` / `reflection` / `tool/weather` / `tool/external` / `session/model` / `rag/...`

---

## 7. SSE 事件原则
网页端看到的必须是**用户可见、可验证**的执行过程，而不是内部隐含推理。

建议长期保留的事件类型：
- `status`
- `retrieval`
- `tool_call`
- `tool_result`
- `answer`
- `itinerary`
- `done`
- `error`

Reflection loop 不一定需要单独新事件类型，第一阶段可以通过 `status` 表达：
- 正在校验结果
- 正在根据校验结果补充信息
- 正在修正行程结果

---

## 8. 来源与不确定性层
系统输出必须能明确回答：
- 这条信息是内部知识、外部知识还是实时工具结果
- 它来自哪里
- 什么时候被验证/更新时间是多少
- 在什么情况下需要二次确认

建议统一来源结构至少包含：
- sourceType
- sourceName
- sourceUrl
- verifiedAt
- effectiveAt
- summary
- confidenceLevel
- toolName（如适用）

当出现以下情况时必须显式提示不确定性：
- 工具调用失败
- 来源缺失
- 来源过旧
- 多来源冲突
- 外部证据不足

---

## 9. 统一阶段主线下的当前重点
### 9.1 目标架构最关键的 4 个支点
1. 结构化路由
2. Working Memory
3. Spring AI `@Tool` 工具层
4. Reflection loop

### 9.2 当前最需要推进的现实缺口
1. 当前混合 RAG 还没有形成完整链路
2. 当前 RAG 缺少项目内评估基准、负样本和可量化报告
3. 当前 RAG 缺少 RagRetrievalGate 底线阈值，低质量检索仍可能污染 prompt
4. 当前 RAG 尚未形成 Vector + BM25 + DashScope Reranker + RagEvidenceJudge 的完整候选治理链路
5. 当前 Working Memory 仍不足以支撑多轮路由上下文
6. 当前 Reflection 还没进入真实编排主链路

### 9.3 迁移顺序建议
1. Phase 3B-0 先稳定 RAG 数据安全与评估前置，确保测试不触发入库、不误清向量表
2. Phase 3B-1 / 3B-2 建立 RAG 评估基准、负样本、报告输出和 RagRetrievalGate
3. Phase 3B-3 保持真实 PgVector distance / similarity 可观测，移除固定兜底分
4. Phase 3B-4 接入 Lucene BM25，并为 `LuceneBm25RagRecallService` 预留 `search(RagQuery)` 与 `rebuildIndex()` 两个入口
5. Phase 3B-5 先做 candidate union / dedup，RRF 与 score fusion 后置
6. Phase 3B-6 接入 DashScope `gte-rerank-v2`，失败时 fallback 到合并候选原始排序
7. Phase 3B-7 引入 RagEvidenceJudge、上下文注入与来源治理
8. SessionState 升级为 Working Memory，先服务路由上下文
9. Reflection loop 前移到主链路
10. 统一工具网关扩展更多 Spring AI `@Tool`，并保留 MCP 作为后续可选扩展

---

## 10. 当前明确不做
为了防止架构目标继续发散，当前明确不做：
- 不恢复 PDF 目标
- 不引入 WebSocket
- 不一开始就做复杂前后端分离
- 不把实时信息伪装成 RAG 结果
- 不继续扩大关键词/限制词列表
- 不把外部搜索结果直接当成最终可信知识
- 不为了 Agent 化而引入重量级 workflow 引擎

---

## 11. 一句话架构策略
系统以 Spring MVC + SSE 网页聊天室为入口，已完成结构化路由前置和受控编排闭环；下一步以 Phase 3B 的 Lucene BM25、DashScope Reranker、RagEvidenceJudge、上下文注入和来源治理为核心，先跑通完整候选治理链路；RRF、score fusion、系统性评估调参和网页采集治理放到链路稳定之后，再前移 Working Memory 与 Reflection loop。
