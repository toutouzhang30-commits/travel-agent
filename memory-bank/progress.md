# 当前开发进度

## 已完成
### Phase 1：基线收敛与接口重塑
- 已完成 `sessionId + message` 协议收敛。
- 已完成 Spring MVC + SSE 主链路。
- 已保留同步接口和流式接口。
- 已具备基础 Agent 事件类型。

### Phase 2：Web UI 与流式聊天室
- 已完成网页聊天页最小可用闭环。
- 已完成消息流、状态区、itinerary 展示区、来源区和检索状态区基础展示。
- 页面已能消费结构化 SSE 事件。

### Phase 3A：RAG 最小闭环
- 已完成 Spring AI PgVector 最小闭环接通。
- 已完成北京 / 上海 / 杭州初始文档入库。
- 已支持按城市过滤的向量检索。
- 已支持知识问答、首轮 itinerary 生成和 itinerary 修改链路消费检索结果。
- 已支持返回 RAG 来源信息。
- 已完成 RAG Manifest 幂等入库与重建一致性补强。
- 已完成 PgVector 检索过滤当前 `active_run_id`。
- 已完成 RAG 检索收口：知识问答优先按 city 过滤，不再用模型生成的 topic 做精确硬过滤。

### Phase 3B：RAG 候选治理接入中
- 已新增或已出现 `PgVectorRagRecallService`，用于向量召回入口。
- 已新增或已出现 `LexicalRagRecallService`，使用 Lucene BM25 做词法召回雏形，并具备 `search(RagQuery)` 与 `rebuildIndex()`。
- 已新增或已出现 `HybridRagRetrievalService`，用于合并 vector 与 lexical 候选，并接入 reranker 与 gate。
- 已新增或已出现 `RagRetrievalGate`，用于拦截非 RAG action、低分、来源不足和城市不匹配结果。
- 已新增或已出现 `RagReranker`、`NoOpRagReranker`、`DashScopeRagReranker`，用于重排抽象、禁用兜底和 DashScope 条件化重排。
- 已新增或已出现 `RagRetrievalFlowService` / `RagRetrievalFlowResult`，用于把检索通过/拒绝结果交给编排层。
- 以上能力记录为“接入中/待验证”，不是完整完成；仍需确认 Maven 编译、配置、回归测试和网页实际问答效果。

### 实时工具底座
- 已存在天气 API 调用底座。
- 已存在天气工具输入/输出 DTO、来源信息和失败处理雏形。
- 页面和后端事件协议已支持 `tool_call` / `tool_result` 类型。
- 已完成基于高德 Amap 的 MapsTool 第一版调用闭环，支持路线、距离、交通耗时类实时问题。

### Phase 4A：结构化路由与受控编排闭环
- 已完成 Phase 4A-1：结构化路由落地，主链路由 `IntentRoutingService` 输出的结构化结果驱动。
- 已完成 Phase 4A-2：受控编排闭环，RAG / Tool / itinerary 由路由结果触发，并显式转换为 SSE 事件。
- 已完成关键词/限制词主分流替换，关键词类只允许作为历史债务或短期 fallback，不再承担主流程分流。
- 已完成 WeatherTool 受控调用链路，工具执行过程可通过 `tool_call` / `tool_result` 展示。

### Phase 4B-1：高德 MapsTool
- 已新增并接入 `chat.tool.maps` 工具包。
- 已完成 `MapsTool`、`MapsQueryExtractor`、`MapsAnswerGenerator`、`MapsToolService`、`AmapRouteClient` 和路线 DTO 的第一版闭环。
- 已将 `MAPS_TOOL` 接入 `AgentOrchestratorService` 同步与 SSE 主链路。
- 已支持地图工具来源、更新时间、成功/失败状态和前端工具事件展示。
- PricingTool 仍暂停真实外部调用，继续返回明确未接入提示。

---

## 当前下一步
### Phase 3B：RAG 候选治理代码收口与验证
- MapsTool 第一版调用闭环已经完成，当前重心回到 RAG 候选治理代码收口。
- 下一步验证已有 RAG 候选治理代码是否能稳定运行：BM25 词法召回、vector + lexical 候选合并、reranker fallback、RagRetrievalGate 和 RagRetrievalFlowService。
- 需要优先确认 Maven 编译、依赖版本、DashScope reranker 开关、API key 缺失 fallback、Lucene BM25 索引重建和 active run 绑定。
- 需要在网页上验证来源区能看到同一问题的候选来源标识，例如 VECTOR、LEXICAL / BM25 或 HYBRID_UNION。
- 需要补 RAG 负样本和评估报告，不能只靠手工提问判断“做成功了”。
- Pricing 仍是后续 Phase 4B 工具扩展方向，但当前不展开真实外部调用。

### Phase 5 前置设计：Working Memory + Reflection
- Working Memory 不能等到 Phase 7 才开始，短期先走内存态。
- 当前会话状态仍偏薄，主要保存 requirement / itinerary。
- Working Memory 前移时必须同步设计容量边界、上下文压缩和驱逐机制，否则会污染结构化路由和 Reflection。
- Reflection 尚未进入主链路，后续需要实现最多一次局部修正的最小闭环。

---

## 尚未完成但方向已确认
- Working Memory 前移。
- Working Memory 上下文压缩与驱逐机制。
- Reflection 最小闭环。
- Phase 4B PricingTool 真实接入。
- MapsTool 稳定性补强：高德超时提示、必要时重试策略、交通方式展示和失败降级体验。
- Phase 3B 高级 RAG 验收：当前已有 BM25、reranker、gate、hybrid flow 雏形，但仍需编译、测试、评估和网页验证。
- Phase 3B 未完成项：`RagEvidenceJudge`、真实 PgVector score、上下文注入、来源治理和拒答边界系统化。
- Phase 3B 后置项：RRF、score fusion、系统性评估指标调优、Postgres FTS 探测和网页采集治理。
- MySQL 正式持久化与会话版本化。
- MCP 仅作为后续可选外部工具协议扩展，目前不作为当前必须落地项。

---

## 当前已知后续优化项
- RAG chunk 策略变化必须进入 pipeline hash，触发 manifest 重建。
- RAG Manifest 已作为当前入库幂等基础，后续可继续补充更细粒度的入库观测和清理告警。
- Lucene BM25 索引必须绑定 manifest active run，避免旧 run 内容进入当前召回。
- `RagEvidenceJudge` 通过的批次才允许注入 prompt 和展示为来源依据。
- Working Memory 需要默认容量边界：最近 5 轮对话、最近 5 次关键决策，并支持目的地切换清理。
- 非试点城市与实时问题的边界仍需继续细化。
- 工具来源结构仍需统一。
- Orchestrator 后续需要通过路由、记忆、反思逐步卸压。

---

## 当前一句话结论
当前项目已经完成“旅游聊天骨架 + Web 页 + RAG 最小闭环 + RAG Manifest 幂等入库 + RAG 检索收口 + Phase 4A 结构化路由与受控编排闭环 + WeatherTool 受控工具展示 + 高德 MapsTool 第一版调用闭环”。当前下一步是 **Phase 3B RAG 候选治理收口**：BM25、hybrid union、reranker、gate 和 retrieval flow 已有代码雏形，但仍需编译、测试、评估和网页验证；PricingTool 仍暂停真实外部调用。
