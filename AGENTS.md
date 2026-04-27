# 🤖 Codex Instructions

## 📚 Context Initialization (CRITICAL)
- ALWAYS read `memory-bank/@architecture.md` and `memory-bank/@product-requriement-document.md` before writing code.
- ALWAYS read `memory-bank/implementation-plan.md` to understand the current progress and roadmap.
- NEVER start coding if the goal contradicts `memory-bank/tech-stack.md`.
- After major features, actively update `memory-bank/@architecture.md`.

## 🛠️ Development Commands
Use the Maven wrapper exclusively:
- Start: `./mvnw spring-boot:run`
- Test (All): `./mvnw test`
- Test (Single): `./mvnw -Dtest=ClassName test`
- Build: `./mvnw clean package`

## 🧠 Behavioral Guidelines

### 1. Think Before Coding
- State explicit assumptions. If uncertain, STOP and ask.
- Surface tradeoffs before picking a technical approach.

### 2. Simplicity First
- Minimum code to solve the problem. Zero speculative features.
- No abstractions for single-use code.
- If you write 200 lines and it could be 50, rewrite it.

### 3. Surgical Changes
- Touch ONLY what traces directly to the user's request.
- Do NOT refactor adjacent, unbroken code. Match existing styles.

### 4. Package Organization & Architecture (包结构与组织规范)
- **NO FLAT PACKAGES (严禁扁平包)**: Never dump all classes into a single package. If a package has more than 5 classes, you MUST group them into semantic sub-packages.
- **Layered / Domain-Driven Taxonomy (严格分层)**:
    - `.model` / `.dto`: For all Java Records, Enums, and Data Transfer Objects (e.g., `ChatRequest`, `Itinerary`, `ChatResponseType`).
    - `.web` / `.controller`: For Spring REST Controllers.
    - `.service`: For core business logic interfaces and their implementations.
    - `.component` / `.ai`: For specific AI orchestration tools, prompts, extractors, and generators (e.g., `ItineraryGenerator`, `TripRequirementExtractor`).
    - `.config`: For configuration classes.
-chat/
      ├─ config/        # 存放 ChatClient, VectorStore 等配置
      ├─ controller/    # 存放 ChatController (REST 入口)
      ├─ service/       # 存放编排逻辑 (Orchestrator) 和核心接口
      ├─ agent/         # (原 component) 存放具备独立人格的 AI 组件
      ├─ tool/          # 新增：存放 Maps, Weather 等外部 API 工具
      ├─ rag/           # 新增：存放知识库检索逻辑
      ├─ model/         # 存放业务领域对象 (Domain Models)
      ├─ dto/           # 存放请求/响应和事件对象 (Event/DTO)
      └─ session/       # 存放会话状态管理
- **Refactoring Duty**: When creating a new class, explicitly state which sub-package it belongs to.

### 5. Workflow & Next Steps
- **"What's Next"**: When asked about the next step, ALWAYS consult `memory-bank/implementation-plan.md` and provide the next uncompleted task.
- **Plan Adaptation**: If the user's new requests or the actual development trajectory deviate from the current `implementation-plan.md`, YOU MUST update the plan document to reflect the new reality before proceeding.

### 6. Goal-Driven Execution & DoD (Definition of Done)
- State a brief 1-2-3 step plan before multi-step tasks.
- **DoD**: For any API changes or core logic updates, you must update `test.http` with boundary test cases according to `memory-bank/testing-standard.md`. **Provide the commands only; the user will execute and verify the tests manually.**

## 📁 Repo State Note
Treat the Java source tree as unimplemented scaffolding. The "source of truth" lives entirely in `memory-bank/`.