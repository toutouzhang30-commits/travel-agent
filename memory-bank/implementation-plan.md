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
1. **Phase 4A-1 结构化路由落地**：必须绑定结构化输出 schema，先彻底拿掉关键词/限制词主分流。
2. **Phase 4A-2 受控编排闭环**：RAG / Tool / itinerary 统一由路由结果驱动。
3. **Working Memory 前移**：先内存态，优先服务路由上下文，不等 MySQL。
4. **Reflection 最小闭环**：最多一次局部修正，不做无限循环。
5. **Phase 3B 高级 RAG**：在主路由稳定后补强网页采集、多路召回、重排、上下文压缩。
6. **工具扩展**：当前主线是 Spring AI `@Tool`，MCP 只作为后续可选扩展。

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
- 天气 API 调用底座已存在。

### 3.2 未完成但容易误判为已完成的能力
- 结构化路由尚未真正替代关键词/限制词主分流。
- Working Memory 仍偏薄，当前主要保存 requirement / itinerary。
- Reflection 还没有进入主链路。
- Phase 3B 高级 RAG 尚未落地。
- Maps / Pricing 等更多工具尚未进入主链路。
- MCP 尚未接入，且不是当前必须落地项。

### 3.3 迁移原则
- 保留已验证的 Web、SSE、RAG、天气 API、itinerary 基础能力。
- 不做大范围包迁移，先改职责边界。
- 不把网页采集变成运行时随意爬网页。
- 不让高级 RAG 和多工具扩展抢在主路由治理之前。

---

## 4. 阶段总览

| 阶段 | 目标 | 当前状态 |
| --- | --- | --- |
| Phase 1 | 基线收敛与接口重塑 | 已完成 |
| Phase 2 | Web UI 与流式聊天室 | 已完成 |
| Phase 3A | RAG 最小闭环 | 已完成 |
| Phase 3B | 高级 RAG、网页采集、多路召回、重排 | 路由稳定后补强 |
| Phase 4A | 结构化路由 + 受控 `@Tool` WeatherTool | 当前第一优先级 |
| Phase 4B | Maps / Pricing 等更多工具 | 后续 |
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

### 当前稳定性补强：Manifest 幂等入库
当前 RAG 启动入库不能继续依赖 JVM 内存布尔值判断是否已经入库。下一步需要补齐“内容指纹 + 处理策略指纹 + manifest 状态机”的幂等入库能力，避免每次程序启动都重新切分 Markdown、调用 embedding 并重复写入 PgVector。

关键要求：
- 新增 `rag_ingestion_manifest` 表记录 `namespace`、`content_hash`、`pipeline_hash`、`active_run_id`、`status`、`document_count`、`chunk_count`、`started_at`、`updated_at`、`error_message`。
- `content_hash` 基于 `classpath:/rag/*.md` 的文件名和内容计算。
- `pipeline_hash` 基于 `travel.rag.ingestion.pipeline-version`、chunk 策略、metadata 字段、embedding model 和 embedding dimensions 计算。
- 只有当 `content_hash`、`pipeline_hash` 一致且 `status=COMPLETED` 时，启动入库才跳过。
- 入库状态使用 `IN_PROGRESS / COMPLETED / FAILED`，manifest 更新必须有事务边界。
- 不在重建开始时删除旧 completed 数据；先写新 `run_id`，成功后切换 active run，再清理旧 run。
- 文档内容、chunk 策略、metadata 结构、embedding 模型或维度变化时，必须触发重建。

---

## Phase 3B：高级 RAG、网页采集、多路召回与重排
### 目标
把 RAG 从“本地文档单路向量检索”升级为可治理、可扩展、可解释的知识系统。

### 当前顺序
Phase 3B 不取消，但不能抢在 Phase 4A 之前成为第一主线。原因是：只要 RAG 仍由关键词/限制词触发，高级 RAG 的召回、重排和网页治理都会被错误入口牵制。Phase 3B 应在结构化路由稳定后作为 RAG 质量补强推进。

### 关键能力
- 受控网页采集：来源白名单、robots/站点规则、速率限制、去重。
- 内容治理：HTML 清洗、正文抽取、chunk、元数据标注、低质量内容过滤。
- 多路召回：向量召回、关键词召回、元数据过滤、会话上下文召回。
- 重排：按语义相关度、城市/主题一致性、来源可信度、更新时间、片段完整性排序。
- 上下文压缩：只把最能支撑回答或 itinerary 的内容送入模型。
- 来源展示：保留 `sourceUrl`、`sourceName`、`fetchedAt`、`verifiedAt`、`contentHash`。

### 完成标准
- “杭州雨天怎么玩？”能召回雨天玩法、室内替代和来源信息。
- “上海拍照打卡适合住哪里？”能结合区域、住宿和拍照主题召回。
- 同一问题能看到多路召回后的重排结果，而不是只看单个向量命中。
- 过旧或低可信来源不会优先进入最终上下文。

### 不要做
- 不要运行时临时爬网页来直接回答用户。
- 不要把未经清洗的网页正文直接塞进 Prompt。
- 不要采集登录后、付费墙后、用户私有或权限不明的内容。
- 不要把实时天气、实时票价、路线耗时写进 RAG 知识库。

---

## Phase 4A：结构化路由 + 受控 `@Tool` WeatherTool
### 目标
用模型结构化路由彻底替代关键词/限制词主分流，并用 Spring AI `@Tool` 打通 WeatherTool 的受控工具调用闭环。Phase 4A 是当前第一优先级。

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
在 Phase 4A 稳定后，把同一模式扩展到路线、距离、价格、票务等更多实时工具。

### 关键要求
- 每个工具都有清晰输入/输出 DTO。
- 每个工具都有来源、请求时间、更新时间和失败状态。
- 每个工具都能在 SSE 中展示 `tool_call` / `tool_result`。
- MCP 可作为后续外部工具协议扩展选项，但当前不写成必须落地。

---

## Phase 5：Agent 编排 + Working Memory + Reflection
### 目标
把当前“需求抽取 -> 完整度判断 -> 生成 itinerary”的旧链路，升级为“会路由、会检索、会调用工具、会修改、会反思并纠错”的 Agent 编排流程。

### Working Memory 前置要求
短期记忆不能等到 Phase 7 数据库持久化后才出现。Phase 5 前必须先用内存态 Working Memory 支撑主链路。

最小 Working Memory 包含：
- 当前 requirement profile。
- 当前 itinerary 摘要。
- 最近几轮用户修改意图。
- 最近一次路由结果。
- 最近一次 RAG 摘要与来源。
- 最近一次工具调用摘要与失败状态。
- 最近一次 Reflection 结论与修正结果。

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
Phase 7 不是 Working Memory 的起点，而是 Working Memory 的正式持久化阶段。

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

0. **Phase 3A 稳定性补强：RAG Manifest 幂等入库**：先解决启动重复切分、重复 embedding、重复写 PgVector 的数据库问题。
1. **Phase 4A-1：结构化路由落地**：必须绑定结构化输出 schema，彻底移除关键词/限制词主分流。
2. **Phase 4A-2：RAG / Tool / itinerary 受控编排**：所有下游能力由路由结果驱动。
3. **Working Memory 前移**：先内存态，优先补齐路由上下文。
4. **Reflection 最小闭环**：最多一次局部修正。
5. **Phase 3B 高级 RAG**：路由稳定后补强网页采集、多路召回、重排、上下文压缩。
6. **工具扩展**：先 Spring AI `@Tool`，MCP 仅作为后续可选扩展。

原因：
- RAG 启动入库不幂等会造成启动慢、重复数据和向量库膨胀，必须先修稳。
- 主分流不稳定时，高级 RAG 和更多工具都会被错误入口放大问题。
- Phase 3B 不能抢在结构化路由之前扩展，否则高级 RAG 仍会被关键词触发逻辑牵制。
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
- 不让高级 RAG 和多工具扩展抢在主路由治理之前。

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
当前项目应先沿着“Phase 4A 结构化路由前置 + 受控编排 + Working Memory + Reflection”主线拿掉关键词主分流，再补强 Phase 3B 高级 RAG 和更多 `@Tool` 能力，把现有能跑的聊天骨架逐步收敛成真正的旅游 Agent。
