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

### 实时工具底座
- 已存在天气 API 调用底座。
- 已存在天气工具输入/输出 DTO、来源信息和失败处理雏形。
- 页面和后端事件协议已支持 `tool_call` / `tool_result` 类型。

### Phase 4A：结构化路由与受控编排闭环
- 已完成 Phase 4A-1：结构化路由落地，主链路由 `IntentRoutingService` 输出的结构化结果驱动。
- 已完成 Phase 4A-2：受控编排闭环，RAG / Tool / itinerary 由路由结果触发，并显式转换为 SSE 事件。
- 已完成关键词/限制词主分流替换，关键词类只允许作为历史债务或短期 fallback，不再承担主流程分流。
- 已完成 WeatherTool 受控调用链路，工具执行过程可通过 `tool_call` / `tool_result` 展示。

---

## 当前下一步
### Phase 3B：Lucene BM25 + DashScope Reranker + RagEvidenceJudge
- 当前 RAG 仍是最小闭环，不代表 RAG 架构成熟。
- 当前主线调整为：`Vector Recall + BM25 Recall -> candidate union / dedup -> DashScope Reranker -> RagEvidenceJudge -> context injection -> answer`。
- 下一步优先补齐 `LuceneBm25RagRecallService`，必须包含 `search(RagQuery)` 与 `rebuildIndex()` 两个入口。
- BM25 第一版采用 Lucene 内存索引，按 manifest active run 绑定；active run、chunk count 或 content hash 变化时触发重建。
- DashScope Reranker 仍进入主链路，负责候选排序；失败时 fallback 到合并候选原始排序。
- `RagEvidenceJudge` 作为证据充分性判断层，采用子代理式裁判，但对外封装为受控 Spring Service。
- `RagEvidenceJudge` 批处理策略：每批最多 5 条 snippet、每批约 3000 字符上限、最多 3 批、停止于首个 `evidenceEnough=true` 的批次。
- 当前不做 RRF / score fusion / 系统性评估调参；这些放到 BM25 + Reranker + Judge 链路稳定之后。
- 负样本应覆盖实时天气、票价、路线、非试点城市和越界问题，防止 RAG 越权回答。
- 新增 `RagRetrievalGate` 作为拒答机制底线：低分、无来源、错城市、实时问题误入 RAG 时拒绝进入最终 prompt。
- Phase 4A 主链路已完成，Phase 3B 现在可以作为当前重点推进。
- 不能把运行时临时爬网页当成直接回答用户的主路径。

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
- Phase 3B 高级 RAG：Lucene BM25、`rebuildIndex()`、DashScope Reranker、RagEvidenceJudge、上下文注入、来源治理和拒答边界。
- Phase 3B 后置项：RRF、score fusion、系统性评估指标调优、Postgres FTS 探测和网页采集治理。
- Maps / Pricing 等更多工具。
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
当前项目已经完成“旅游聊天骨架 + Web 页 + RAG 最小闭环 + RAG Manifest 幂等入库 + RAG 检索收口 + Phase 4A 结构化路由与受控编排闭环”。下一步推进 **Lucene BM25 + DashScope Reranker + RagEvidenceJudge**：先补 BM25 召回与 `rebuildIndex()`，再让候选经 reranker 排序和 judge 裁判后注入上下文；RRF、score fusion 和系统性评估调参放到链路稳定之后。
