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

### 实时工具底座
- 已存在天气 API 调用底座。
- 已存在天气工具输入/输出 DTO、来源信息和失败处理雏形。
- 页面和后端事件协议已支持 `tool_call` / `tool_result` 类型。

---

## 当前下一步
### Phase 3A 稳定性补强：RAG Manifest 幂等入库
- 当前启动入库仍会每次重新读取、切分、embedding 并写入 PgVector，导致启动慢和重复数据风险。
- 下一步先实现 `rag_ingestion_manifest`，用 `content_hash + pipeline_hash + status` 判断是否需要重建。
- manifest 必须记录 `active_run_id`，重建时先写新 run，成功后切换 active run，再清理旧 run。
- chunk 策略、metadata 字段、embedding model 或 dimensions 变化时，必须通过 pipeline version 触发重建。
- 检索应过滤当前 `active_run_id`，避免新旧 chunk 混合命中。

### Phase 4A：结构化路由前置，彻底替换关键词主分流
- `IntentRoutingService` 尚未真正落地。
- 路由必须绑定结构化输出 schema，不允许自然语言路由后字符串解析。
- 当前第一优先级是让 RAG / Tool / itinerary 统一由路由结果驱动。
- `RagRetrievalDecider`、`ToolCallDecider`、`ItineraryModificationDetector` 只能作为短期 fallback / guard，不再承担主流程分流。
- RAG 由编排器根据路由结果触发，不再把关键词/限制词作为主分流方案。
- 实时天气优先通过 Spring AI `@Tool` 接入，并用受控执行显式产生 `tool_call` / `tool_result` 事件。
- Maps / Pricing 暂不真实接入时，应返回明确不确定性提示。

### Phase 3B：路由稳定后的 RAG 质量补强
- 当前 RAG 仍是最小闭环，不代表 RAG 架构成熟。
- 需要补齐受控网页采集、内容治理、多路召回、重排、上下文压缩和来源治理。
- Phase 3B 不取消，但应在结构化路由稳定后重点推进。
- 不能把运行时临时爬网页当成直接回答用户的主路径。

### Phase 5 前置设计：Working Memory + Reflection
- Working Memory 不能等到 Phase 7 才开始，短期先走内存态。
- 当前会话状态仍偏薄，主要保存 requirement / itinerary。
- Reflection 尚未进入主链路，后续需要实现最多一次局部修正的最小闭环。

---

## 尚未完成但方向已确认
- RAG Manifest 幂等入库与重建一致性。
- PgVector 检索过滤当前 active run。
- 结构化路由主链路。
- 关键词/限制词主分流移除。
- RAG / Tool / itinerary 统一由路由结果驱动。
- Working Memory 前移。
- Reflection 最小闭环。
- Phase 3B 高级 RAG。
- Maps / Pricing 等更多工具。
- MySQL 正式持久化与会话版本化。
- MCP 仅作为后续可选外部工具协议扩展，目前不作为当前必须落地项。

---

## 当前已知后续优化项
- RAG chunk 策略变化必须进入 pipeline hash，触发 manifest 重建。
- 向量库重建策略需从 JVM 内布尔值升级为数据库 manifest 状态机。
- 非试点城市与实时问题的边界仍需继续细化。
- 工具来源结构仍需统一。
- Orchestrator 后续需要通过路由、记忆、反思逐步卸压。

---

## 当前一句话结论
当前项目已经完成“旅游聊天骨架 + Web 页 + RAG 最小闭环 + 天气底座”。下一步先修复 **RAG Manifest 幂等入库与重建一致性**，解决启动重复切分和重复写库；随后继续推进 **Phase 4A 结构化路由前置，彻底替换关键词主分流**。
