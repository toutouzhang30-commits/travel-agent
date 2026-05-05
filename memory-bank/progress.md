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
### Phase 4B：Maps / Pricing 工具接入
- 当前 RAG 先阶段性收口，用于网页问答验证、来源展示和基础边界观察；这不代表高级 RAG 已完成。
- 下一步优先补齐实时 / 半实时工具边界，先接 MapsTool，再接 PricingTool。
- MapsTool 负责路线、距离、交通耗时类问题，例如“从西湖到灵隐寺大概要多久？”。
- PricingTool 负责门票、价格、票务类问题，例如“上海迪士尼今天门票多少钱？”。
- Maps / Pricing 都复用 WeatherTool 的模式：结构化参数抽取 DTO、受控 Tool 执行、AnswerGenerator、`tool_call` / `tool_result` SSE 展示。
- 工具失败时必须明确说明不可用或不确定，不能编造路线耗时或票价。
- RAG 后续仍会继续推进 BM25、DashScope Reranker、RagEvidenceJudge、RRF / score fusion 和评估报告，但放到 Maps / Pricing 工具边界补齐之后。

### Phase 4B 具体实施步骤
1. 新增 `chat.tool.maps` 包：
   - `dto/MapsToolRequest.java`
   - `dto/MapsToolResponse.java`
   - `client/MapsClient.java`
   - `client/MapsClientResponse.java`
   - `MapsQueryExtractor.java`
   - `MapsTool.java`
   - `MapsAnswerGenerator.java`
2. 新增 `chat.tool.pricing` 包：
   - `dto/PricingToolRequest.java`
   - `dto/PricingToolResponse.java`
   - `client/PricingClient.java`
   - `client/PricingClientResponse.java`
   - `PricingQueryExtractor.java`
   - `PricingTool.java`
   - `PricingAnswerGenerator.java`
3. 修改 `IntentRoutingService`：
   - 补充 `MAPS_TOOL` 示例：路线、距离、交通耗时。
   - 补充 `PRICING_TOOL` 示例：门票、票价、余票、价格。
4. 修改 `AgentOrchestratorService`：
   - 参考 `executeWeatherToolFlow` 增加 `executeMapsToolFlow`。
   - 参考 `executeWeatherToolFlow` 增加 `executePricingToolFlow`。
   - 在工具分支中将 `MAPS_TOOL` / `PRICING_TOOL` 从“不支持兜底”切换为真实工具调用。
5. 修改前端工具展示：
   - 复用已有 `tool_call` / `tool_result` 展示。
   - 工具 source 存在时进入来源区；source 为空时不展示为可靠来源。
6. 补充回归测试：
   - 路由测试。
   - 参数抽取测试。
   - 工具失败降级测试。
   - RAG 边界测试。

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
- Phase 4B Maps / Pricing 工具真实接入。
- Phase 3B 高级 RAG：Lucene BM25、`rebuildIndex()`、DashScope Reranker、RagEvidenceJudge、上下文注入、来源治理和拒答边界。
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
当前项目已经完成“旅游聊天骨架 + Web 页 + RAG 最小闭环 + RAG Manifest 幂等入库 + RAG 检索收口 + Phase 4A 结构化路由与受控编排闭环 + WeatherTool 受控工具展示”。当前 RAG 先阶段性收口，下一步推进 **Phase 4B Maps / Pricing 工具接入**：先接路线、距离、交通耗时，再接门票、票价、票务查询，并确保工具失败时明确不确定性；随后再回到 BM25、Reranker、RagEvidenceJudge 等高级 RAG 深化。
