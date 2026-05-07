# 旅游 Agent MVP 实施计划

## 1. 文档目的
本计划用于把当前仓库从“可运行的旅游聊天骨架”收敛到“面向 Web 的旅游 Agent 主链路”。

它只回答实施顺序和阶段边界，不把目标态写成已完成状态。当前主线统一使用 `Phase 1/2/3A/3B/4A/4B/5/7/8`，不再使用 `Phase A-F` 作为主阶段体系。

---

## 2. 当前执行总原则
从现在开始，项目不再继续强化：
- 关键词/限制词主分流
- 单个 orchestrator 里的大段 if/else
- 把实时问题伪装成 RAG 静态知识
- 把目标态能力写成当前已完成

新的主线是：
1. **Phase 4B-1 MapsTool 已完成第一版调用闭环**：基于高德 Amap 的路线、距离、交通耗时工具已进入同步与 SSE 主链路，后续只做稳定性和体验补强。
2. **Pricing 暂不实施**：票价、门票、余票等 PricingTool 暂时只保留路由与未接入提示，不做真实外部调用。
3. **Working Memory 前移**：先内存态，优先服务路由上下文，不等 MySQL。
4. **Reflection 最小闭环**：最多一次局部修正，不做无限循环。
5. **RAG 收口继续推进**：MapsTool 完成后，已有 Phase 3B RAG 候选治理代码回到当前收口重点，继续验证编译、配置、回归测试、网页问答和失败降级。
6. **工具扩展技术路线**：当前主线是 Spring AI `@Tool` + 本地 service/client 受控执行，MCP 只作为后续可选扩展。

---

## 3. 当前仓库真实状态

### 3.1 已完成能力
- Spring Boot 应用可启动。
- DeepSeek OpenAI 兼容接口已接通。
- `POST /api/chat` 与 `POST /api/chat/stream` 已存在。
- Spring MVC + `SseEmitter` 主链路可用。
- Web 聊天页已存在。
- `sessionId + message` 协议已建立。
- 最小需求抽取、完整度判断、itinerary 生成和修改已存在。
- Phase 3A 的 RAG 最小闭环已存在。
- RAG 来源展示与检索区展示已有基础。
- RAG Manifest 幂等入库、active run 检索过滤和知识问答检索收口已完成。
- RAG 候选治理相关代码已开始出现：`PgVectorRagRecallService`、`LexicalRagRecallService`、`HybridRagRetrievalService`、`RagRetrievalGate`、`RagReranker` / `DashScopeRagReranker`、`RagRetrievalFlowService` 已在代码树中。
- Phase 4A-1 结构化路由已完成，主流程由 `IntentRoutingService` 的结构化结果驱动。
- Phase 4A-2 受控编排闭环已完成，RAG / Tool / itinerary 由路由结果触发并显式转换为 SSE 事件。
- 天气 API 调用底座已存在。
- Phase 4B-1 高德 MapsTool 已完成第一版主链路接入，可处理路线、距离、交通耗时问题，并在同步与 SSE 链路产生工具调用结果。

### 3.2 未完成但容易误判为已完成的能力
- Working Memory 仍偏薄，当前主要保存 requirement / itinerary。
- Reflection 还没有进入主链路。
- Phase 3B 高级 RAG 已有部分代码雏形，但尚未完成验收：仍需确认编译、配置、回归测试、网页问答效果和失败降级。
- PricingTool 尚未进入真实外部调用；票价、门票、余票仍保持明确未接入提示。
- MCP 尚未接入，且不是当前必须落地项。

### 3.3 迁移原则
- 保留已验证的 Web、SSE、RAG、天气 API、itinerary 基础能力。
- 不做大范围包迁移，先改职责边界。
- 不把网页采集变成运行时随意爬网页。
- Phase 4B-1 已完成后，继续收口 RAG 候选治理质量、稳定性和可评估性；PricingTool 仍暂停真实调用。

---

## 4. 阶段总览

| 阶段 | 目标 | 当前状态 |
| --- | --- | --- |
| Phase 1 | 基线收敛与接口重塑 | 已完成 |
| Phase 2 | Web UI 与流式聊天室 | 已完成 |
| Phase 3A | RAG 最小闭环 | 已完成 |
| Phase 3B | 高级 RAG 候选治理：BM25、hybrid union、reranker、gate、评估 | 接入中 / 待验证，MapsTool 完成后回到当前收口重点 |
| Phase 4A | 结构化路由 + 受控 `@Tool` WeatherTool | 已完成 |
| Phase 4B | Maps / Pricing 等更多实时工具 | 高德 MapsTool 第一版已完成；Pricing 暂不做 |
| Phase 5 | Agent 编排 + Working Memory + Reflection | 后续，但需前置设计 |
| Phase 7 | 正式持久化、会话与版本化 | 后续 |
| Phase 8 | 规则层、监控与上线准备 | 后续 |

---

## 5. 分阶段说明

## Phase 1：基线收敛与接口重塑
### 目标
把旧版纯对话骨架收敛为可承载 Web UI、RAG 和 Tool Calling 的基础设施。

### 已完成内容
- API 协议收敛到 `sessionId + message`。
- 保留同步接口作为 debug / fallback。
- 流式接口基于 Spring MVC + SSE。
- 事件类型支持 `status`、`retrieval`、`tool_call`、`tool_result`、`answer`、`itinerary`、`done`、`error`。
- 会话状态先采用内存态。

### 后续不再扩展的方向
- 不回到 `message + currentRequirement + currentItinerary` 的前端回传协议。
- 不继续扩大单个聊天服务职责。

---

## Phase 2：Web UI 与流式聊天室
### 目标
建立可直接演示的网页聊天界面，让用户在浏览器里看到 Agent 的用户可见执行过程。

### 已完成内容
- `/` 与 `/chat` 页面入口。
- Thymeleaf + HTML + 原生 JavaScript 聊天页。
- 浏览器端消费 SSE 流式响应。
- 页面展示消息、状态、itinerary、检索状态、工具状态和来源区。

### 后续不再扩展的方向
- 不在当前阶段引入 React / Vue 等复杂前端框架。
- 不把业务编排逻辑放到前端脚本里。

---

## Phase 3A：RAG 最小闭环
### 目标
完成“文档入库 -> 向量检索 -> 聊天链路消费检索结果”的最小闭环。

### 已完成内容
- Spring AI PgVector 向量存储配置已接通。
- 北京 / 上海 / 杭州初始文档可入库。
- 支持按城市过滤的向量检索。
- 知识问答、首轮 itinerary 生成与 itinerary 修改链路均可消费检索结果。
- 页面可展示检索状态与文档来源。

### 当前边界
Phase 3A 只代表 RAG 能跑通，不代表 RAG 架构成熟。

### 已完成稳定性补强：Manifest 幂等入库与检索收口
当前 RAG 启动入库已从 JVM 内存布尔值判断升级为“内容指纹 + 处理策略指纹 + manifest 状态机”的幂等入库流程，避免每次程序启动都重新切分 Markdown、调用 embedding 并重复写入 PgVector。

关键要求：
- 新增 `rag_ingestion_manifest` 表记录 `namespace`、`content_hash`、`pipeline_hash`、`active_run_id`、`status`、`document_count`、`chunk_count`、`started_at`、`updated_at`、`error_message`。
- `content_hash` 基于 `classpath:/rag/*.md` 的文件名和内容计算。
- `pipeline_hash` 基于 `travel.rag.ingestion.pipeline-version`、chunk 策略、metadata 字段、embedding model 和 embedding dimensions 计算。
- 只有当 `content_hash`、`pipeline_hash` 一致且 `status=COMPLETED` 时，启动入库才跳过。
- 入库状态使用 `IN_PROGRESS / COMPLETED / FAILED`，manifest 更新必须有事务边界。
- 不在重建开始时删除旧 completed 数据；先写新 `run_id`，成功后切换 active run，再清理旧 run。
- 文档内容、chunk 策略、metadata 结构、embedding 模型或维度变化时，必须触发重建。
- PgVector 检索已过滤当前 `active_run_id`，避免新旧 chunk 混合命中。
- 知识问答检索已完成收口：优先按 city 过滤，不再把模型生成的 `topic` 作为精确硬过滤条件。

---

## Phase 3B：高级 RAG、网页采集、多路召回与重排
### 目标
把 RAG 从“本地文档单路向量检索”升级为可治理、可扩展、可解释、可评估的知识系统。Phase 3B 内部按“先稳定，再增强”的顺序推进：先保证数据安全、评估和拒答边界，再逐步实施真实向量分数、中文词法召回、RRF 融合、专业 reranker、上下文压缩和来源治理。

### 当前顺序
Phase 4A-1 结构化路由和 Phase 4A-2 受控编排闭环已经完成，Phase 3B 现在成为当前第一优先级。但 Phase 3B 不应一次性铺开所有高级检索能力。

当前仓库已经先行出现了一部分高级 RAG 代码：Lucene BM25 词法召回、vector + lexical 候选合并、DashScope reranker 条件化实现、NoOp fallback、RagRetrievalGate 和 RagRetrievalFlowService。它们应记录为“接入中/待验证”，不能写成完整完成。

接下来优先级应先从“继续新增能力”改为“验证和收口已有 RAG 代码”：确认 Maven 编译、依赖、配置、fallback、网页来源展示、负样本边界和检索失败降级，再继续补 `RagEvidenceJudge`、评估报告和 RRF / score fusion。

当前路线调整为先跑通完整候选治理链路，不先做 RRF、score fusion 和系统性评估调参。当前主链路为：

```text
Vector Recall + BM25 Recall
  -> candidate union / dedup
  -> DashScope Reranker
  -> RagEvidenceJudge
  -> context injection
  -> answer
```

其中 BM25 负责补召回，DashScope Reranker 负责排序，`RagEvidenceJudge` 负责判断“证据是否足以回答”。RRF、score fusion、系统性分数评估和指标调优放到这条链路稳定之后。

### 关键能力
- 数据安全：评估测试不能触发入库，入库失败不能清空旧 active run，manifest 必须和向量表真实数据一致。
- 评估基准：正样本、负样本、拒答指标和报告输出必须先建立。
- 拒答阈值：低相关、错城市、无来源、实时问题误入 RAG 时拒绝进入最终 prompt。
- 真实向量评分：从 Spring AI Document metadata 或 PgVector SQL 取得真实 distance / similarity，不能继续使用固定兜底分。
- 中文词法召回：当前先采用 Lucene BM25 内存索引，后续再调研阿里云 PostgreSQL 中文 FTS。
- 候选治理：当前先做 vector + BM25 的 candidate union / dedup，不做 RRF / score fusion。
- 专业重排：DashScope `gte-rerank-v2` 作为第一候选 reranker，失败时 fallback 到合并候选原始排序。
- 证据裁判：新增 `RagEvidenceJudge`，以子代理式裁判判断证据是否足够，但对外封装为受控 Spring Service。
- 上下文压缩与来源治理：只把真正支撑回答或 itinerary 的内容送入模型，并保留来源、时间和不确定性。
- 受控网页采集：来源白名单、robots/站点规则、速率限制、去重。
- 内容治理：HTML 清洗、正文抽取、chunk、元数据标注、低质量内容过滤。
- 来源展示：保留 `sourceUrl`、`sourceName`、`fetchedAt`、`verifiedAt`、`contentHash`。

### Phase 3B-0：数据安全与评估前置
在继续扩展高级 RAG 之前，必须先确保评估和启动流程不会破坏阿里云 PgVector 数据。

要求：
- `RagRetrievalEvaluationTest` 使用 `@ActiveProfiles("test")`，测试 profile 中 `travel.rag.ingestion.enabled=false`。
- 测试只读取现有向量库，不触发 `RagBootstrapRunner` 的重新入库。
- 入库时如果切分得到 0 个 documents，必须 fail fast，不能 `markCompleted`，不能清理旧 run。
- `vectorStore.add(...)` 后必须检查当前 run 在向量表中真实存在数据；否则不能切换 active run。
- manifest completed 且 hash 一致时，也必须确认 active run 对应向量数量大于 0 才能跳过入库。
- 评估测试前后 `public.travel_knowledge` 行数不能减少。

### Phase 3B-1：RAG 评估基准与负样本
高级 RAG 不能只看“是否召回到了内容”，还必须评估“是否在不该召回时拒绝召回”。第一版评估基准采用项目内可控数据，不先引入大规模真实网页数据。

评估集建议放在 `src/test/resources/rag/eval/travel-rag-eval.jsonl`，第一版至少 40 条：
- 正样本 30 条：北京 / 上海 / 杭州各 10 条。
- 负样本 10 条：实时天气、票价、路线、非试点城市、越界问题。

评估阶段候选召回数建议提升到 `topK=10`，为后续 reranker 提供更充分候选池。`topK=10` 只代表候选池大小，不代表最终展示 10 条来源；最终来源仍由 reranker、gate 和上下文压缩共同决定。

评估样例字段：
- `id`
- `caseType`：`POSITIVE` / `NEGATIVE`
- `question`
- `city`
- `expectedAction`：`RETRIEVE` / `NO_RETRIEVAL` / `TOOL_REQUIRED` / `UNSUPPORTED`
- `expectedTopics`
- `expectedSourceNames`
- `expectedKeywords`
- `mustRetrieveAny`
- `negativeReason`
- `notes`

负样本要求：
- “杭州明天会下雨吗？”应走 WeatherTool，不应由 RAG 给实时天气结论。
- “上海迪士尼今天门票多少钱？”应走 PricingTool 或未接入提示，不应由 RAG 编造票价。
- “成都三天怎么玩？”当前不应伪装成三城知识库覆盖。
- “帮我写 Java 简历”应拒绝 RAG。

### Phase 3B-2：RagRetrievalGate 拒答阈值
新增 `RagRetrievalGate`，位置建议为 `chat.rag.retrieval`。它负责对重排后的 topK 结果判断“是否允许进入最终回答”。

输出 `RagGateDecision`：
- `allowed`
- `reason`
- `bestScore`
- `matchedSourceCount`
- `keywordHitRate`
- `cityMatched`
- `recommendedFallback`

`recommendedFallback` 枚举：
- `ANSWER_WITH_RAG`
- `ASK_CLARIFICATION`
- `ROUTE_TO_TOOL`
- `UNSUPPORTED`
- `ANSWER_WITH_UNCERTAINTY`

第一版使用规则阈值，不调用模型裁判。默认阈值：
- `MIN_BEST_SCORE = 0.45`
- `MIN_SOURCE_COUNT = 1`
- `MIN_KEYWORD_HIT_RATE = 0.20`
- `MIN_CITY_MATCH_REQUIRED = true`
- `MAX_CONTEXT_CHARS = 3000`

正常允许条件：
```text
allowed =
    bestScore >= 0.45
    AND matchedSourceCount >= 1
    AND keywordHitRate >= 0.20
    AND cityMatched == true
    AND route.action == KNOWLEDGE_QA / GENERATE_ITINERARY / MODIFY_ITINERARY
```

必须拒绝 RAG 的情况：
- `route.action == WEATHER_TOOL / PRICING_TOOL / MAPS_TOOL`
- city 不在支持范围且没有可用来源
- topK 为空
- bestScore 低于阈值
- 命中内容来自错误城市
- 负样本评估中出现 RAG 高置信命中

拒绝后的行为：
- 实时问题：转工具或返回工具未接入提示。
- 非试点城市：说明当前知识库主要覆盖北京 / 上海 / 杭州，可给通用建议但不引用内部来源。
- 证据不足：回答“不确定”，并说明缺少可靠知识库来源。
- 用户问题太宽：追问具体城市、天数或主题。

`AgentOrchestratorService` 集成原则：
- `allowed=true`：正常发 `retrieval` SSE，进入 RAG answer / itinerary 生成。
- `allowed=false`：仍发 `retrieval` SSE，但 message 必须说明“检索结果不足/不适合使用 RAG”。
- gate 拒绝的低质量 chunk 不进入 prompt。
- gate 拒绝时不把低质量来源展示为“参考依据”。

### Phase 3B-3：真实 PgVector 分数获取
当前临时评分不能继续依赖固定兜底分。Phase 3B-3 需要从真实检索结果中获得向量距离或相似度。

优先顺序：
1. 尝试从 Spring AI `Document.metadata` 中读取 `score` / `distance` / `similarity`。
2. 如果 Spring AI 不暴露真实分数，则使用 `JdbcTemplate` 直查 PgVector。
3. 使用 embedding model 生成 query embedding，SQL 侧计算 `embedding <=> queryEmbedding` 得到 distance。

规则：
- 当前配置为 `COSINE_DISTANCE` 时，`vectorSimilarity = clamp(1 - distance, 0, 1)`。
- 原始 distance、转换后的 similarity、最终参与融合的 score 都要记录到评估报告。
- 禁止继续把 `0.60` 作为固定向量分兜底。

### Phase 3B-4：中文词法召回，优先 Postgres FTS，fallback Lucene BM25
词法召回不再写死为 Java 内存 2-gram / 3-gram 方案。当前实施顺序调整为：第一版先接入 Lucene BM25 内存索引，后续再调研数据库侧中文全文检索。

当前第一版：
- 新增 `LuceneBm25RagRecallService`。
- 必须提供 `search(RagQuery)` 作为词法召回入口。
- 必须预留 `rebuildIndex()` 钩子，负责按 manifest active run 重建 Lucene 索引。
- Lucene 索引必须绑定 manifest active run。
- active run 不变时复用索引；active run、chunk count 或 content hash 变化时触发 `rebuildIndex()`。
- 第一版 Lucene 索引用内存态，不写入仓库。

后续再探测阿里云 PostgreSQL 能力：
- `zhparser`
- `pg_jieba`
- 其他可用中文 FTS 扩展

如果后续 Postgres 中文 FTS 可用：
- 新增 `PostgresFtsRecallService`。
- 使用 active run 数据，保留 namespace、runId、city、topic 过滤。
- 使用 `ts_rank` / `ts_rank_cd` 或扩展提供的排名分作为 lexical raw score。
- 该 raw score 不能直接和向量分相加，必须进入 Phase 3B-5 的融合处理。

### Phase 3B-5：RRF 倒数排名融合 + 分数平滑映射
RRF 和 score fusion 不作为当前第一实施任务。当前阶段先做 vector + BM25 的 candidate union / dedup，再交给 DashScope Reranker 排序。

后续如果需要融合多路召回分数，多路召回结果不能直接用 raw score 相加。执行 merge 和加权融合前必须进行 rank / score 处理。

融合主策略：
```text
rrfScore = Σ 1 / (k + rank)
```

默认：
- `k = 60`
- vector recall 和 lexical recall 各自保留 rank。
- 同一文档同时命中 vector 与 lexical 时，RRF 自然提高排序。
- 只命中一路召回的文档仍可保留，不因另一侧缺失被清零。

分数平滑：
- PgVector distance 先转 similarity。
- BM25 / FTS raw score 不直接与向量 score 相加。
- 保留经验平滑：`score / (score + 10)`。
- 保留 Sigmoid 作为后续可配置方案。
- 避免 min-max 在小 batch 内把相对较低但绝对值尚可的文档“一棍子打死”。

### Phase 3B-6：DashScope Reranker 专业重排
Reranker 第一候选为 DashScope `gte-rerank-v2`。

要求：
- 当前输入为 vector + BM25 candidate union / dedup 后的 candidates。
- 后续如引入 RRF，则输入可升级为 RRF pre-rank 后的 candidates。
- 输出记录 `rerankScore`、`rerankRank`、`scoreSource`。
- 必须记录 `rerankTimeMs`，用于评估专业重排的延迟成本。
- reranker 负责排序，不负责判断证据是否足够回答。
- reranker 失败、超时或 API key 缺失时，不中断 RAG，fallback 到合并候选原始排序。
- `DASHSCOPE_API_KEY` 通过环境变量注入，不写入仓库。

### Phase 3B-7：RagEvidenceJudge + 上下文压缩与来源治理
在 reranker 之后新增 `RagEvidenceJudge`。它是证据充分性判断层，不替代 reranker。

要求：
- `RagEvidenceJudge` 是子代理式裁判，但对外必须封装为受控 Spring Service。
- judge 只判断证据是否足够回答，不直接生成最终答案。
- judge 必须输出结构化 DTO，例如 `evidenceEnough`、`sourceActuallyAnswersQuestion`、`freshnessRequired`、`reason`、`fallback`。
- 不暴露隐藏推理过程。

批处理策略：
- 按 reranker 排序后的候选构造 snippet。
- 每批最多 5 条 snippet。
- 每批约 3000 字符上限。
- 最多处理 3 批。
- 停止于首个 `evidenceEnough=true` 的批次。
- 所有批次都失败时，拒绝 RAG 或转澄清/不确定性回答。

在 gate 允许后，进入 prompt 的内容必须经过压缩与来源治理。

要求：
- 默认 `MAX_CONTEXT_CHARS = 3000`。
- 只保留 judge 允许批次中对问题最有支撑的内容。
- 来源展示只展示 judge 允许并实际进入上下文的文档。
- 保留 `sourceName`、`sourceUrl`、`verifiedAt`、`fetchedAt`、`contentHash`、`scoreSource`。
- 过旧、低可信、错城市或未进入最终上下文的内容不能展示为“参考依据”。

### RAG 评估指标与报告
评估报告必须输出具体参考数据，而不是只给主观描述。

当前阶段先跑通 BM25 + Reranker + RagEvidenceJudge 主链路。RAG 分数评估、阈值校准、RRF 增益评估和系统性指标提升放到链路稳定之后统一推进。

正样本指标：
- `Recall@3`
- `MRR@5`
- `KeywordHitRate`
- `NoSourceRate`
- `AvgLatencyMs`
- `P95LatencyMs`
- `rerankTimeMs`
- `AvgRerankTimeMs`
- `P95RerankTimeMs`
- `ContextTokenEstimate`

负样本与拒答指标：
- `FalsePositiveRetrievalRate`
- `ToolMisrouteRate`
- `UnsupportedRecallRate`
- `RejectPrecision`
- `RejectRecall`
- `FalseAcceptRate`
- `FalseAcceptRate before/after rerank`
- `FalseRejectRate`

第一版验收目标：
- 正样本 `Recall@3 >= 0.80`
- 正样本 `MRR@5 >= 0.60`
- 正样本 `NoSourceRate = 0`
- 负样本 `FalsePositiveRetrievalRate <= 0.10`
- 负样本 `ToolMisrouteRate = 0`
- `RejectRecall >= 0.90`
- `FalseAcceptRate <= 0.10`
- `FalseRejectRate <= 0.20`
- `P95LatencyMs <= 800ms` 作为参考目标，初期不作为硬失败条件。

报告输出建议：
- `target/rag-eval/report.json`
- `target/rag-eval/report.md`
- 每条样例输出 `gateAllowed`、`gateReason`、`bestScore`、`lexicalScore`、`rerankScore`、`rerankTimeMs`、`fallback`、实际 topK、各阶段分数和失败原因。

### 完成标准
- “杭州雨天怎么玩？”能召回雨天玩法、室内替代和来源信息。
- “上海拍照打卡适合住哪里？”能结合区域、住宿和拍照主题召回。
- 同一问题能看到 vector + BM25 多路候选和专业重排后的结果，而不是只看单个向量命中。
- `LuceneBm25RagRecallService` 具备 `search(RagQuery)` 与 `rebuildIndex()` 两个明确入口。
- `RagEvidenceJudge` 使用批处理策略判断证据充分性，并且只允许通过批次进入上下文。
- 过旧或低可信来源不会优先进入最终上下文。
- 实时天气、票价、路线类问题不会被 RAG 当成静态知识确定回答。
- 低相关、错城市、无来源或负样本命中的结果会被 `RagRetrievalGate` 拒绝。
- RAG 评估报告能给出 Recall、MRR、负样本误召回率、拒答指标、延迟数据、vector score、lexical score、RRF score、rerank score 和最终 gate reason。

### 不要做
- 不要运行时临时爬网页来直接回答用户。
- 不要把未经清洗的网页正文直接塞进 Prompt。
- 不要采集登录后、付费墙后、用户私有或权限不明的内容。
- 不要把实时天气、实时票价、路线耗时写进 RAG 知识库。

---

## Phase 4A：结构化路由 + 受控 `@Tool` WeatherTool
### 目标
用模型结构化路由彻底替代关键词/限制词主分流，并用 Spring AI `@Tool` 打通 WeatherTool 的受控工具调用闭环。Phase 4A 已完成，后续只做稳定性修补和新增工具复用。

### 结构化路由要求
- 必须使用 Spring AI structured output / `BeanOutputConverter` / 等价强类型机制。
- 不允许模型输出自然语言后再做字符串包含判断。
- 路由结果至少包含 `action`、`requiresRetrieval`、`toolName`、`city`、`topic`、`confidence`、`reason`。
- 解析失败必须进入显式 fallback，例如 `ROUTING_FAILED`。
- `RagRetrievalDecider`、`ToolCallDecider`、`ItineraryModificationDetector` 只能作为短期 fallback / guard，不能继续承担主流程分流。

### Phase 4A-1：结构化路由落地
- 新增 `IntentRoutingService` 作为所有分流入口。
- RAG、Tool、itinerary 生成、itinerary 修改都读取路由结果，不再各自读取关键词表。
- 关键词类不再新增词表，不再决定主流程，只保留短期兜底。

### Phase 4A-2：受控编排闭环
- 编排器根据路由结果触发 RAG / Tool / itinerary 下游能力。
- 每个执行步骤都显式转换为 SSE 事件。
- WeatherTool 先接入受控 `@Tool` 闭环，Maps / Pricing 暂不真实接入时返回明确不确定性。

### 工具调用要求
- 当前工具主线使用 Spring AI `@Tool`。
- WeatherTool 作为第一批真实工具。
- 工具执行采用受控执行，必须显式产生 `tool_call` / `tool_result`。
- 工具失败必须保留失败状态、来源和时间信息。
- Maps / Pricing 当前不真实接入时，必须返回明确不确定性提示。

### 完成标准
- “杭州雨天怎么玩？”走 RAG，不走 WeatherTool。
- “杭州明天会下雨吗？”走 WeatherTool。
- “上海迪士尼门票多少钱？”不走 RAG 编造，返回未接入 PricingTool 的明确提示。
- 页面能看到工具调用状态和结果摘要。

---

## Phase 4B：Maps / Pricing 等更多工具
### 目标
在 Phase 4A 稳定后，把同一模式扩展到路线、距离、交通耗时等更多实时工具。Phase 4B-1 已完成基于高德 Amap 的 MapsTool 第一版调用闭环；PricingTool 暂不做真实调用。

### 关键要求
- 每个工具都有清晰输入/输出 DTO。
- 每个工具都有来源、请求时间、更新时间和失败状态。
- 每个工具都能在 SSE 中展示 `tool_call` / `tool_result`。
- MCP 可作为后续外部工具协议扩展选项，但当前不写成必须落地。

### 当前状态
Phase 4B 现在拆分执行：
- `MAPS_TOOL`：第一版已完成，继续使用高德 Amap 调用，覆盖路线、距离、交通耗时，并进入同步与 SSE 工具事件闭环。
- `PRICING_TOOL`：暂不实施真实外部调用，继续返回未接入 PricingTool 的明确提示，不能由 RAG 编造票价。
- Phase 3B RAG 候选治理：已有代码继续保留为接入中 / 待验证，MapsTool 完成后回到当前收口重点。

当前仓库已经出现 `chat.tool.maps` 真实工具包，`AgentOrchestratorService` 已将 `MAPS_TOOL` 接入受控工具流；对 `PRICING_TOOL` 仍应视为明确不可用兜底。

### Phase 4B-1：高德 MapsTool 接入（已完成第一版）
目标是复用 WeatherTool 的受控工具模式，把路线问题纳入 Spring AI `@Tool` + 本地 service/client + SSE 事件闭环。当前第一版已完成，后续只做稳定性、错误提示和结果展示体验补强。

已落地包结构：
```text
chat/tool/maps/
  ├─ MapsTool.java
  ├─ common/
  │   ├─ MapsQueryExtractor.java
  │   └─ MapsAnswerGenerator.java
  ├─ service/
  │   └─ MapsToolService.java
  ├─ client/
  │   └─ AmapRouteClient.java
  └─ dto/
      ├─ MapsRouteRequest.java
      ├─ MapsRouteResponse.java
      ├─ RouteOption.java
      └─ TravelMode.java
```

各类归属：
- `MapsTool.java` 属于 `chat.tool.maps`，暴露 Spring AI `@Tool` 入口。
- `MapsQueryExtractor.java` 属于 `chat.tool.maps.common`，用 structured output 把自然语言路线问题转成 `MapsRouteRequest`。
- `MapsAnswerGenerator.java` 属于 `chat.tool.maps.common`，把工具结果组装成用户可读回答。
- `MapsToolService.java` 属于 `chat.tool.maps.service`，放执行 guardrail、失败降级和来源组装。
- `AmapRouteClient.java` 属于 `chat.tool.maps.client`，只负责高德 API 调用。
- `MapsRouteRequest`、`MapsRouteResponse`、`RouteOption`、`TravelMode` 属于 `chat.tool.maps.dto`。

第一版 DTO 结构：
```java
public record MapsRouteRequest(
        String city,
        String origin,
        String destination,
        TravelMode travelMode,
        String timeExpression,
        String rawQuestion
) {
}

public enum TravelMode {
    WALKING,
    TRANSIT,
    DRIVING,
    CYCLING
}

public record RouteOption(
        String mode,
        String durationText,
        Integer durationMinutes,
        String distanceText,
        Integer distanceMeters,
        String instructionSummary
) {
}

public record MapsRouteResponse(
        boolean success,
        String city,
        String origin,
        String destination,
        List<RouteOption> options,
        SourceReferenceDto source,
        String updatedAt,
        String errorMessage
) {
}
```

`MapsTool` 第一版形态：
```java
@Component
public class MapsTool {
    private final MapsToolService mapsToolService;

    public MapsTool(MapsToolService mapsToolService) {
        this.mapsToolService = mapsToolService;
    }

    @Tool(description = """
            查询两个地点之间的路线、距离和交通耗时。
            只用于实时路线、交通方式、距离、耗时问题。
            不用于回答旅游玩法、区域推荐或静态攻略问题。
            """)
    public MapsRouteResponse queryRoute(
            @ToolParam(description = "路线查询结构化参数，包含城市、起点、终点、交通方式、时间表达和原始问题。")
            MapsRouteRequest request
    ) {
        return mapsToolService.execute(request);
    }
}
```

`MapsToolService` 第一版职责：
- 校验 city / origin / destination 是否足够，不足时返回失败状态或让编排器追问。
- 调用 `AmapRouteClient`，继续使用现有高德配置体系。
- 统一封装 `SourceReferenceDto`，sourceName 建议为 `高德地图`。
- 工具失败时返回 `success=false`、`errorMessage`、`updatedAt`，不能转 RAG 编造路线耗时。
- 工具层 guardrail 放在 service/client，不放进自然语言解析逻辑。

`AgentOrchestratorService` 接入点：
- `orchestrateByRoute` 中已将 `MAPS_TOOL` 接入 `executeMapsToolFlow(...)`。
- `streamChat` 中已将工具分支扩展到 `MAPS_TOOL`，可发送 `tool_call` 和 `tool_result`。
- `PRICING_TOOL` 继续走未接入兜底。

验收用例：
- “从西湖到灵隐寺坐公交多久” 应走 `MAPS_TOOL`，页面显示 MapsTool 的 `tool_call` / `tool_result`。
- “北京故宫到南锣鼓巷多远” 应走 `MAPS_TOOL`，不走 RAG。
- “上海外滩到武康路怎么去” 应走 `MAPS_TOOL`，最终展示高德路线来源、距离、耗时和人类可读交通方式。
- “杭州雨天怎么玩” 仍走 `KNOWLEDGE_QA`，不走 MapsTool。
- “上海迪士尼今天门票多少钱” 仍走 `PRICING_TOOL` 未接入提示，不走 RAG 编造票价。
- 高德 API 失败、缺 key 或请求超时时，最终回答明确说明实时路线工具失败，不伪装成静态攻略。

### Phase 4B-2：PricingTool 暂停
PricingTool 暂不实施真实外部调用。当前只保留结构化路由到 `PRICING_TOOL` 后的明确未接入提示，并继续要求 RAG gate 拒绝票价、门票、余票类实时问题。

---

## Phase 5：Agent 编排 + Working Memory + Reflection
### 目标
把当前“需求抽取 -> 完整度判断 -> 生成 itinerary”的旧链路，升级为“会路由、会检索、会调用工具、会修改、会反思并纠错”的 Agent 编排流程。

### Working Memory 前置要求
短期记忆不能等到 Phase 7 数据库持久化后才出现。Phase 5 前必须先用内存态 Working Memory 支撑主链路。

最小 Working Memory 必须是有限上下文窗口，而不是无界历史仓库。结构建议拆为：
- `stableProfile`：用户稳定需求画像，如目的地、天数、预算、节奏、兴趣。
- `activeItinerarySummary`：当前有效 itinerary 摘要，不保存整份历史版本。
- `recentTurns`：最近少量对话轮次，默认保留最近 5 轮。
- `recentDecisions`：最近路由、RAG、Tool、Reflection 摘要，默认保留最近 5 次关键决策。
- `volatileEvidence`：天气、票价、路线等有时效性的工具结果。

### Working Memory 上下文压缩与驱逐机制
Working Memory 每轮结束后都必须执行压缩与驱逐，避免上下文膨胀、旧信息污染路由、模型输入越来越慢。

压缩规则：
- RAG 只保存命中文档摘要、来源、主题、城市和时间，不保存完整 chunk。
- Tool 只保存工具名、参数、结果摘要、成功/失败、来源和更新时间，不保存完整原始响应。
- Reflection 只保存结论、是否修正、修正目标，不保存隐藏推理。
- itinerary 只保存当前版本摘要；完整版本历史交给 Phase 7 持久化承接。
- 对话历史只保存最近窗口和必要摘要，不把完整历史长期塞入模型上下文。

驱逐规则：
- `recentTurns` 超过 5 轮后，只保留最近轮次和更早轮次摘要。
- `recentDecisions` 超过 5 次后，驱逐最旧决策摘要。
- 工具结果按时效驱逐：天气结果只在当前会话短期有效；票价/路线结果必须带更新时间。
- 用户切换目的地时，清理与旧目的地强绑定的 RAG / Tool / itinerary 摘要。
- 用户生成新 itinerary 时，旧 itinerary 只保留极短摘要或交给 Phase 7 版本化处理。

### Reflection 要求
- 最多一次局部修正，不做无限循环。
- 优先局部修正，不整份重生成。
- 只检查可验证问题，不暴露 chain-of-thought。
- 检查 itinerary 是否满足天数、预算、节奏、兴趣偏好。
- 检查工具失败是否被明确提示。
- 检查用户修改请求是否真实落到新 itinerary。

### 完成标准
- 多轮对话不再只依赖当前一句 message。
- “第二天太累了，轻松一点”能结合已有 itinerary 局部修改。
- 工具失败时，最终回答不会假装拿到实时数据。
- 预算/节奏明显不合理时，Reflection 能提示风险或做一次局部修正。
- 多轮对话超过 5 轮后，Working Memory 不再保留完整旧轮次，只保留摘要。
- 用户从“杭州 3 天”切换到“上海周末”后，杭州相关天气、RAG、itinerary 摘要不再影响上海路由。

---

## Phase 7：正式持久化、会话与版本化
### 目标
把 Phase 5 中已经跑通的短期 Working Memory 映射到 MySQL 正式持久化模型。

### 关键要求
- 重新启用并接入 MySQL。
- 优先实现 `ChatSession`、`ConversationTurn`、`TripRequirementProfile`、`ItineraryVersion`、`ItineraryDay`、`ItineraryItem`、`SourceReference`。
- 每次生成和修改建立版本记录，不原地覆盖。
- 会话恢复后应能继续聊天与修改 itinerary。
- PgVector 继续作为 RAG 向量存储，不与 MySQL 职责混淆。

### 边界
Phase 7 不是 Working Memory 的起点，也不替代短期上下文压缩。Phase 7 负责长期保存正式会话和版本；Phase 5 负责当前会话可用上下文、压缩、驱逐和时效性失效。

---

## Phase 8：规则层、监控与上线准备
### 目标
让结果更稳定、可观测，并满足 MVP 上线前质量要求。

### 关键能力
- 最小规则层：预算档位、路线合理性、下雨替代方案、节奏过满提醒。
- 最小可观测性：健康检查、工具调用成功率、检索命中率、对话主流程日志。
- 配置缺失检查。
- 主流程手工验证清单。

---

## 6. 当前优先级
当前不是所有能力同时推进。优先级如下：

1. **Phase 3B 当前代码收口**：MapsTool 第一版已完成后，当前优先确认已有 BM25、hybrid union、reranker、gate、retrieval flow 能编译、能运行、能在网页问答中稳定展示来源与拒答边界。
2. **PricingTool 暂不做**：`PRICING_TOOL` 继续返回明确未接入提示，不做票价、门票、余票真实调用，也不允许 RAG 编造价格。
3. **Working Memory 前移**：先内存态，优先补齐路由上下文，后续服务地图参数补全、行程修改和工具结果时效驱逐。
4. **Reflection 最小闭环**：最多一次局部修正。
5. **Phase 3B 评估与后续深化**：最后补评估集、负样本、`RagEvidenceJudge`、真实 PgVector score、RRF / score fusion、上下文压缩和来源治理。
6. **MapsTool 稳定性补强**：按真实测试结果补充超时提示、重试策略、交通方式展示和高德失败降级，但不扩大到 Pricing。

原因：
- 用户当前确认地图工具调用完成，因此当前重心回到 RAG 候选治理收口和后续 Agent 能力。
- 路线、距离、交通耗时是典型实时工具问题，已经有 `MAPS_TOOL` 路由枚举和高德配置底座，适合复用 WeatherTool 的受控工具闭环。
- PricingTool 涉及票价和余票来源可靠性，当前暂不实施，避免无来源价格幻觉。
- 已有 RAG 候选治理代码继续保留为待验证，现在需要恢复为当前收口重点。
- 没有 Working Memory，路由、工具参数补全、修改和 Reflection 都缺上下文。
- 没有 Reflection，系统仍像单轮输出器，不像 Agent。

---

## 7. memory-bank 同步原则
实施时，这几份文档必须持续保持一致：
- `product-requriement-document.md`
- `architecture.md`
- `implementation-plan.md`
- `tech-stack.md`
- `progress.md`

一致性要求：
- PRD 说清产品目标和边界。
- architecture 说清目标态、现状和模块关系。
- implementation-plan 说清迁移顺序。
- tech-stack 说清为什么选这些技术。
- progress 诚实记录当前真实进展，不把目标态写成已完成状态。

---

## 8. 当前明确不做
- 不恢复 PDF 目标。
- 不继续扩大关键词/限制词列表。
- 不把 MCP 写成当前必须落地。
- 不把 Phase 3B 高级 RAG 写成已完成。
- 不把 Reflection 做成无限循环。
- 不在缺少评估基准和拒答阈值时盲目扩大网页采集范围。

---

## 9. 最终验收方向
只有当下面这些能力同时成立时，才算真正接近新的 MVP 主体：
1. 问题类型主要由结构化路由决定，而不是关键词表。
2. 系统能利用 Working Memory 理解连续多轮对话。
3. 实时问题优先走工具层，不再伪装成 RAG 答案。
4. RAG 支持网页采集治理、多路召回、重排和来源展示。
5. itinerary 可以持续生成、修改，并保留来源与不确定性边界。
6. Reflection 能对明显问题做一次有界局部修正。
7. SSE 可以展示用户可见的执行过程。

---

## 10. 一句话执行策略
当前项目已经完成 Phase 4A 结构化路由前置、RAG 最小闭环、WeatherTool 受控工具展示和基于高德 Amap 的 MapsTool 第一版调用闭环；路线、距离、交通耗时问题已进入受控 `@Tool` + SSE 闭环。PricingTool 暂不做真实调用，当前继续推进 RAG 候选治理收口和评估深化。
