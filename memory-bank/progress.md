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
- RAG 质量治理方向已调整为“规则校验 + 按需 LLM-as-Judge + 少量人工抽检”；当前规则层已有雏形，按需 judge 和人工抽检流程尚未完整落地。
- 已将 PDF 攻略入库方向调整为“受控知识源补充”：后续 PDF 先由 loader 抽取、清洗并包装成 `RagRawDocument`，再复用 `RagDocumentConverter`，不直接产出 Spring AI `Document`。

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
- MapsTool 当前只代表客观路线查询能力已接入，不代表 itinerary 级路线校验已经完成。
- PricingTool 仍暂停真实外部调用，继续返回明确未接入提示。

---

## 当前下一步
### Phase 3B：RAG 候选治理代码收口与验证
- MapsTool 第一版调用闭环已经完成，当前重心回到 RAG 候选治理代码收口。
- 下一步验证已有 RAG 候选治理代码是否能稳定运行：BM25 词法召回、vector + lexical 候选合并、reranker fallback、RagRetrievalGate 和 RagRetrievalFlowService。
- 需要优先确认 Maven 编译、依赖版本、DashScope reranker 开关、API key 缺失 fallback、Lucene BM25 索引重建和 active run 绑定。
- 需要在网页上验证来源区能看到同一问题的候选来源标识，例如 VECTOR、LEXICAL / BM25 或 HYBRID_UNION。
- 需要补 RAG 负样本和评估报告，报告应记录规则校验结果、judge 触发原因、judge 决策和人工抽检标记，不能只靠手工提问判断“做成功了”。
- LLM-as-Judge 第一版采用按需在线策略，只在规则层低置信、复杂问题、候选冲突或评估场景触发；人工抽检用于校准阈值、prompt 和评估集，不参与每次在线回答。
- 如果接入北京 / 上海 / 杭州攻略 PDF，下一步应新增 `PdfRagDocumentLoader`，只产出 `RagRawDocument`，并把 PDF metadata、清洗规则和实时信息边界纳入 pipeline hash 与评估。
- Pricing 仍是后续 Phase 4B 工具扩展方向，但当前不展开真实外部调用。

### Phase 5：Working Memory + Reflection + Route Validation + 受控 ReAct
- Working Memory 已开始前移，当前为内存态短期窗口，已保存 itinerary 摘要、最近对话、最近工具证据和最近 Reflection 摘要。
- itinerary 生成链路已接入最小 Reflection：生成草稿后结合天气 / 地图证据检查，并最多触发一次局部修正。
- 当前确认技术分工：知识问答、itinerary 生成和 itinerary 修改更适合 Reflection；工具调用链路更适合受控 ReAct / Tool-use。
- itinerary 最终确认新增方向：MapsTool 提供路线、距离、耗时和交通方式等客观数据，`RouteValidationAgent` 判断路线合理性、节奏压力和绕路风险，并输出分数、问题和修改建议。
- 受控 ReAct / Tool-use 的目标流程是：参数提取 -> 工具调用 -> 观察结果 -> 最多一次补救或降级 -> 最终回答，不做开放式无限 Agent。
- Phase 5 仍未完成：缺 stableProfile、recentDecisions、目的地切换清理、工具证据时效驱逐、知识问答 Reflection、itinerary 修改 Reflection、RouteValidationAgent 路线校验闭环和工具受控 ReAct。

---

## 尚未完成但方向已确认
- Working Memory 深化：stableProfile、recentDecisions、目的地切换清理和工具证据时效驱逐。
- Working Memory 上下文压缩与驱逐机制。
- Reflection 补齐：知识问答和 itinerary 修改链路仍需接入；itinerary 生成链路已有最小雏形。
- RouteValidationAgent：尚未实现，后续归属 `chat.agent.route`；MapsTool 继续只提供客观路线数据，路线校验发现明显问题时最多触发一次局部修正。
- 工具调用受控 ReAct / Tool-use：参数补全、工具结果观察、一次补救或降级、失败透明表达。
- Phase 4B PricingTool 真实接入。
- MapsTool 稳定性补强：高德超时提示、必要时重试策略、交通方式展示和失败降级体验。
- Phase 3B 高级 RAG 验收：当前已有 BM25、reranker、gate、hybrid flow 雏形，但仍需编译、测试、评估和网页验证。
- 受控 PDF 攻略入库：PDF 抽取、清洗、metadata 包装、配置化路径、manifest hash 联动和检索来源展示仍未实现。
- Phase 3B 未完成项：按需 `RagEvidenceJudge`、人工抽检流程、真实 PgVector score、上下文注入、来源治理和拒答边界系统化。
- Phase 3B 后置项：RRF、score fusion、系统性评估指标调优、Postgres FTS 探测和网页采集治理。
- MySQL 正式持久化与会话版本化。
- MCP 仅作为后续可选外部工具协议扩展，目前不作为当前必须落地项。

---

## 当前已知后续优化项
- RAG chunk 策略变化必须进入 pipeline hash，触发 manifest 重建。
- PDF 抽取、清洗、metadata 字段或 PDF 文件内容变化必须进入 content / pipeline hash，触发 manifest 重建。
- RAG Manifest 已作为当前入库幂等基础，后续可继续补充更细粒度的入库观测和清理告警。
- Lucene BM25 索引必须绑定 manifest active run，避免旧 run 内容进入当前召回。
- `RagRetrievalGate` 先做规则校验；按需 `RagEvidenceJudge` 通过的批次才允许注入 prompt 和展示为来源依据；人工抽检只用于校准，不替代在线决策。
- `RouteValidationAgent` 不能伪造路线耗时；MapsTool 失败时必须保留缺少实时路线依据的不确定性。
- Working Memory 需要默认容量边界：最近 5 轮对话、最近 5 次关键决策，并支持目的地切换清理。
- 非试点城市与实时问题的边界仍需继续细化。
- 工具来源结构仍需统一。
- 工具调用不要用开放式无限 Agent，后续只做有界 ReAct / Tool-use。
- Orchestrator 后续需要通过路由、记忆、反思逐步卸压。

---

## 当前一句话结论
当前项目已经完成“旅游聊天骨架 + Web 页 + RAG 最小闭环 + RAG Manifest 幂等入库 + RAG 检索收口 + Phase 4A 结构化路由与受控编排闭环 + WeatherTool 受控工具展示 + 高德 MapsTool 第一版调用闭环”，并已部分接入 Phase 5 的内存态 Working Memory 与 itinerary 生成 Reflection。当前下一步是 **Phase 3B RAG 三层质量治理收口**；PDF 攻略入库已调整为后续受控知识源补充方向，必须走 `PDF -> RagRawDocument -> RagDocumentConverter`；同时补齐 Phase 5 中知识问答 / itinerary 修改 Reflection、基于 MapsTool 证据的 RouteValidationAgent 路线校验闭环和工具调用受控 ReAct / Tool-use。PricingTool 仍暂停真实外部调用。
