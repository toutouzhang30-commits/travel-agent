# 当前开发进度

## 已完成
- Phase 1：完成基线收敛与接口重塑，统一到 `sessionId + message` 协议，并收敛到 Spring MVC + SSE 主链路。
- Phase 2：完成网页聊天页、SSE 流式事件展示、itinerary 展示区、来源区与检索状态区的最小可用闭环。
- Phase 3A：完成 RAG 知识层最小闭环，包括：
  - Spring AI PgVector 向量存储配置接通
  - 北京 / 上海 / 杭州文档可自动入库
  - 按城市过滤的向量检索可用
  - 知识问答、首轮 itinerary 生成与 itinerary 修改链路均可消费检索结果
  - 页面可展示检索状态与文档来源

## 当前下一步
- Phase 3B：高级 RAG、网页采集、多路召回与重排
  - 当前 Phase 3A 只是最小闭环，仍缺少网页采集、内容治理、多路召回、重排和上下文压缩
  - 下一步需要设计受控网页采集：来源白名单、robots/站点规则、速率限制、去重、`contentHash`
  - RAG 检索需要从单路向量召回升级为向量召回 + 关键词召回 + 元数据过滤 + 会话上下文召回 + 重排
- Phase 4A：意图路由 + 受控 `@Tool` WeatherTool 闭环并行准备
  - `IntentRoutingService` 必须绑定结构化输出 schema，不允许自然语言路由后字符串解析
  - RAG 由编排器根据路由结果触发，不再把关键词/限制词作为主分流方案
  - 实时天气通过 Spring AI `@Tool` 接入，并用受控执行显式产生 `tool_call` / `tool_result` 事件
  - Maps / Pricing 暂不真实接入，遇到相关实时问题应返回明确不确定性提示
- 短期工作记忆提前
  - 不能等到 Phase 7 才做记忆管理
  - 需要先在内存态记录需求画像、当前 itinerary 摘要、近期修改意图、RAG/Tool 摘要与 Reflection 结果

## 当前已知后续优化项
- RAG chunk 策略仍可继续优化，并需要进入 Phase 3B 的内容治理流程
- 向量库重建策略仍可从“按表是否为空”升级为更稳定的版本化方案
- 非试点城市与实时问题的边界仍需继续细化
- Phase 5 需要补齐 Reflection 与纠错闭环
- Phase 4A 稳定后，再扩展 MapsTool 与 PricingTool
