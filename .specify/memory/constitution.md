<!--
Sync Impact Report
==================
Version change: (uninitialized template) → 1.0.0
Modified principles:
  - (new) I. Java 21 同步执行模型 ← CLAUDE.md 不可违背原则 #1
  - (new) II. ReAct Loop 自实现 ← CLAUDE.md 不可违背原则 #2
  - (new) III. Provider 显式映射 ← CLAUDE.md 不可违背原则 #3
  - (new) IV. 白名单沙箱 ← CLAUDE.md 不可违背原则 #4
  - (new) V. 审计先行 ← CLAUDE.md 不可违背原则 #5
  - (new) VI. 敏感配置只走环境变量 ← CLAUDE.md 不可违背原则 #6
  - (new) VII. 一个目录 = 一个 Agent ← CLAUDE.md 不可违背原则 #7
  - (new) VIII. Skill ≠ Tool ← CLAUDE.md 不可违背原则 #8
  - (new) IX. 接口先行 ← CLAUDE.md 不可违背原则 #9
Added sections: 技术栈与架构约束; 开发流程与质量门禁; Governance
Removed sections: 无（原文件为未填充模板）
Follow-up TODOs: 无
-->

# OryxOS Constitution

OryxOS —— 基于 Java 21 + Spring Boot 3.x 的企业级 Agent OS 宪法。宪法高于仓库内
一切其他实践、文档与代码惯例；冲突时以本宪法为准。

## Core Principles

### I. Java 21 同步执行模型（NON-NEGOTIABLE）

全程使用 JDK 21，不得降版本。执行模型保持同步：禁止引入 Reactor、WebFlux、
CompletableFuture 及任何异步/响应式框架。

**理由**：OryxOS 的核心循环（ReAct）与工具执行是顺序推理过程，同步模型最易推理、
调试与审计；JDK 21 的 Virtual Thread 已在运行时层面覆盖并发需求，无需应用层异步框架。

### II. ReAct Loop 自实现

ReAct 循环（Reason → Act → Observe）必须由 `oryxos-core` 的 `ReActLoop` 自实现。
MUST 禁用 Spring AI 自动 tool 执行；Spring AI 仅用于协议转换与 `@Tool` schema 生成。

**理由**：自动 tool 执行会导致 tool 被调用两次、脱离沙箱校验与审计落库，破坏循环
控制权（最大迭代次数、终止条件、Sandbox 检查均由自实现循环掌控）。

### III. Provider 显式映射

多 Provider 并存时 MUST 使用显式 `Map<String, ChatModel>` 按 Provider name 映射，
禁止依赖 Bean 类型扫描或自动注入歧义解析。

**理由**：多个 Provider 的 ChatModel Bean 类型相同，类型扫描会产生歧义且在运行时
切换 Provider 时不可控；显式映射保证运行时切换无 lock-in 且行为可预测。

### IV. 白名单沙箱

沙箱 MUST 通过 `SandboxChecker` 白名单机制实现：路径白名单（文件类 Tool）、命令
白名单 + 超时（shell）、域名白名单（HTTP 类 Tool）。禁止使用 `SecurityManager`。

**理由**：`SecurityManager` 已被 JDK 弃用且粒度不足；显式白名单可测试、可审计、
可按 Agent/Profile 收敛权限。

### V. 审计先行（Day One 数据地基）

`tool_invocations` 与 `llm_calls` 两张审计表自核心阶段起 MUST 写入 SQLite（经
`oryxos-storage`），不是只打日志。每次 LLM 调用与每次 Tool 调用各落一行。

**理由**：审计数据是后续记忆、计费、排障、合规的基础；事后补数据代价极高。

### VI. 敏感配置只走环境变量

API key 等敏感配置 MUST 且只能通过环境变量注入；绝不明文写入 YAML、代码或提交
记录。YAML 中仅允许 `${ENV_VAR}` 占位。

**理由**：OryxOS 承诺私有部署、数据不出域；配置文件会随仓库分发与归档，明文
密钥即泄露。

### VII. 一个目录 = 一个 Agent

`AGENT.md` 所在的一个目录定义一个 Agent。Agent 目录归 `ContextLoader`（core 模块）
管理，正文注入 system prompt；Agent 目录 MUST NOT 被实现为 Tool。

**理由**：Agent 是被底座运行的实体而非被调用的能力；混淆两者会破坏
"一个目录一个 Agent、一个底座一群 Agent" 的核心模型。

### VIII. Skill ≠ Tool

Skill MUST NOT 进入 `ToolRegistry`。Skill 由 `ContextLoader` 注入元信息，正文经
`read_file`/`shell` 按需读取。

**理由**：Skill 是知识资产（md 文档），Tool 是可执行能力；混入 ToolRegistry 会
让模型把文档当函数调用，且使 Skill 无法被多个 Agent 复用。

### IX. 接口先行

Sandbox、Memory、NotifyChannel 等模块 MUST 先定义抽象接口，核心阶段每接口只挂
一档实现（如 `WhitelistSandbox`）。

**理由**：保证扩展点稳定，后续档次实现（企业级沙箱、情景记忆、多渠道 Notify）
可在不改调用方的前提下替换。

## 技术栈与架构约束

| 组件 | 约束 |
|------|------|
| JDK | 21（见原则 I） |
| 应用框架 | Spring Boot 3.x |
| LLM 抽象 | Spring AI Alibaba 最新稳定版，仅协议转换 + schema 生成 |
| 构建 | Maven 3.9+ 多模块 |
| 持久化 | SQLite + Spring Data JPA |
| CLI | Picocli 4.x |
| 配置解析 | SnakeYAML |
| 文档站点 | VitePress |
| 日志 | Logback + SLF4J 结构化日志 |

**模块边界（9 个 Maven 模块）**：`oryxos-core`（核心抽象与 ReAct/Prompt/Agent 调度）、
`oryxos-provider`（ProviderService 与 Function Calling 适配）、`oryxos-memory`
（MemoryService 门面 + 长期记忆 + MemoryTools）、`oryxos-tool`（内置 Tool、MCP、
ToolRegistry、Sandbox、Notify 适配）、`oryxos-channel-cli`、`oryxos-web`（6 个
ApiController + 全局异常处理）、`oryxos-storage`（SQLite 仓库）、`oryxos-cli`
（Picocli 入口）、`oryxos-boot`（启动与自动配置）。工具相关内容核心阶段统一收敛在
`oryxos-tool` 一个模块内，不得过细拆分。

## 开发流程与质量门禁

1. **构建与验证**：任何代码变更 MUST 通过 `mvn` 全量构建；涉及会话/启动行为的
   变更 MUST 以实际启动 + 会话跑通作为验收（能跑通一条 chat 会话）。
2. **审计门禁**：新增 Tool 或改动 Tool/LLM 调用链路时，对应审计表写入路径 MUST
   同步覆盖（原则 V）。
3. **安全门禁**：新增文件/命令/域名访问能力的 Tool MUST 同步扩展对应白名单并在
   默认配置中保持最小权限（原则 IV、VI）。
4. **开发节奏**：按 4 周主线推进 —— W1 LLM + ReAct；W2 Memory + Tool；W3 Web
   Service；W4 多 Agent + 工程化收尾。每周期以"可演示成果"为准入。
5. **常见陷阱**即反模式清单：自动 tool 执行、Provider 类型扫描、Agent 目录当
   Tool、审计不落库、密钥入 YAML、Skill 入 Registry、Tool 模块过拆、JDK 降版本
   —— 任何 PR/review 触及这些模式即不合规。

## Governance

- **最高权威**：本宪法高于 CLAUDE.md、README、代码注释及任何口头约定；冲突时以
  宪法为准，并在发现冲突时立即修订下位文档。
- **修订流程**：提案 → 说明影响面与迁移方案 → 更新本文件并递增版本号 → 在
  Sync Impact Report 中记录变更。
- **版本策略**：MAJOR = 原则删除/不兼容重定义；MINOR = 新增原则或实质性扩展；
  PATCH = 澄清与措辞修正。
- **合规审查**：所有 PR/review MUST 对照本宪法验证合规；复杂度引入必须给出理由，
  无理由的复杂度一律拒绝（YAGNI）。
- **运行时指引**：日常开发细节参考 `CLAUDE.md` 与 `docs/oryxos.md`；两者与宪法
  冲突时按宪法修订下位文档。

**Version**: 1.0.0 | **Ratified**: 2026-09-02 | **Last Amended**: 2026-09-02
