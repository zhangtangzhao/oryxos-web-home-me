# Research: OryxOS 核心阶段运行时内核

**Feature**: `001-agent-os-core` | **Date**: 2026-09-02 | **输入**: spec.md 29 条 FR + 宪法 9 原则 + 需求文档 §12 未决事项

本文件解决 Technical Context 与需求文档遗留的全部未决事项。每条按 Decision / Rationale / Alternatives 结构记录。

## R-1. Provider 抽象形态：自建薄接口 + 显式映射委托 Spring AI

**Decision**: `oryxos-provider` 自建 `ProviderService` 薄接口（core 只认该接口），实现内部以 `Map<String, ChatModel>` 显式注册各厂商 `ChatModel`，调用时经 Spring AI 做协议转换；Function Calling 的 tool schema 由 `@Tool` 注解生成、但工具执行必须回环到自实现 `ToolExecutor`。

**Rationale**: 宪法 II（禁 Spring AI 自动 tool 执行）与 III（显式映射）直接决定了形态；薄接口让 core 不感知 Spring AI 类型，未来换协议库不扩散。需求文档 §12 未决事项就此关闭。

**Alternatives considered**: ① 直接暴露 Spring AI `ChatClient` 给 core —— core 与协议库强耦合，违反接口先行；② 完全自研 HTTP 客户端对接各厂商 —— 重复造轮子，违反"自实现核心、底层协议适配复用成熟库"设计原则。

## R-2. 持久化引擎：SQLite（经 JPA）

**Decision**: SQLite 单文件 `.oryxos/oryxos.db`，通过 Spring Data JPA 访问，落 sessions / tool_invocations / llm_calls 三表。

**Rationale**: 需求文档 §10 数据模型明确"Session 持久化到 SQLite"；单文件形态契合"一个工作区一个实例"与私有部署叙事；JPA 仓库层已搭好。

**Alternatives considered**: H2（纯 Java、无 JNI 依赖）—— 需求文档已按 SQLite 表述工作区产物 `oryxos.db`，且 SQLite 运维工具链（CLI/BI 工具）更普及；内存 Map —— 违反 FR-017（重启恢复）与宪法 V。

## R-3. SQLite + Hibernate 方言

**Decision**: 引入 `org.hibernate.orm:hibernate-community-dialects`，JPA url 指定 `SQLiteDialect`；DDL 由 JPA 自动建表（核心阶段不做迁移脚本）。

**Rationale**: Hibernate 6.x 官方主包不含 SQLite 方言，社区包是一等支持的补充包；核心阶段表结构由实体注解驱动即可满足"day one 数据地基"。

**Alternatives considered**: 自写方言 —— 不必要的复杂度（宪法 IX：接口先行、一档实现即可）；引第三方方言包（如 com.github.gwenn）—— 多一个外部依赖，社区官方包已够。

## R-4. LLM 接入路径：OpenAI 兼容协议先行

**Decision**: 显式映射首批注册：DeepSeek、Kimi（Moonshot）——均为 OpenAI 兼容协议，经 spring-ai openai starter 配 base-url/model 接入；Spring AI Alibaba（DashScope/通义）作为同映射下的另一注册项。核心阶段只保证 DeepSeek + Kimi 端到端跑稳（spec US5）。

**Rationale**: 需求文档 §12 风险表明示"核心阶段先把 OpenAI 协议跑稳"；三家协议成熟、密钥经环境变量注入即可测试。

**Alternatives considered**: 五家全接（文心/智谱/混元/豆包）—— 里程碑版本 API 不稳，回归成本超预算；留扩展阶段。

## R-5. MCP Client：官方 MCP Java SDK，封装为 McpClientService

**Decision**: `oryxos-tool` 内以 MCP 官方 Java SDK 实现 `McpClientService`：读取 `mcp_servers.yaml`，stdio/HTTP 连接 server，把远端 tool 描述转成 `OryxTool` 注册进工具池，调用经同一 Sandbox 白名单 + 审计链路。

**Rationale**: 需求文档流程四要求"MCP Tool 通过 MCP 协议转发执行"；独立封装让 MCP 生命周期（连接、重连、能力发现）与 ToolRegistry 解耦，符合宪法 IX。

**Alternatives considered**: Spring AI 的 MCP 粘合层 —— 与 R-1 的"Spring AI 只管 LLM 协议转换"边界冲突，且 M5 里程碑版本组合风险高。

## R-6. 定时任务（AgentScheduler）：Spring 内置调度器 + 动态注册

**Decision**: 用 spring-context 自带 `ThreadPoolTaskScheduler` + `CronTrigger`；启动时从各 Agent frontmatter `schedules` 派生触发规则动态注册，到点调用与 CLI/REST 完全相同的 `AgentService` 调用链路。

**Rationale**: FR-021 要求三触发源同链路；不引入 Quartz 等新依赖，符合"分阶段克制"。

**Alternatives considered**: Quartz（持久化调度、集群）—— 核心阶段单机单实例，无此需求；自研 cron 解析 —— 重复造轮子。

## R-7. Bootstrap 文件加载顺序与优先级

**Decision**: system prompt 组装顺序固定为：`AGENT.md` 正文（任务指令）→ `AGENTS.md`（项目级行为）→ `SOUL.md`（人格）→ `USER.md`（用户偏好）→ 绑定 Skill 元数据 → `MEMORY.md` 全文 → 对话历史 + Tool 列表。Agent frontmatter `bootstrap` 列表可禁用/挑选文件；同名冲突时 Agent 级配置覆盖项目级。

**Rationale**: 关闭需求文档 §12 未决事项；指令最贴近模型注意力焦点，人格/偏好按"由项目到个人"递进，Skill/Memory 作为附加上下文殿后。

**Alternatives considered**: MEMORY.md 置顶 —— 长期记忆最长（4000 字截断上限），置顶会挤占指令注意力；仅拼接不排序 —— 行为不可预期，验收无法断言。

## R-8. CLI 与 Spring 容器融合：picocli-spring-boot-starter

**Decision**: `oryxos-cli` 以 Picocli 为命令骨架，经 picocli-spring-boot-starter 让每个子命令在 Spring 容器内取用 `AgentService` 等 Bean；`serve`/`gateway` 复用同一容器启动 Web 与调度。

**Rationale**: 12 个命令与 Web/守护进程共享配置、会话存储与审计（FR-020），必须同容器；starter 官方维护、约定成熟。

**Alternatives considered**: 手动 new 依赖图 —— FR-020 共享存储要求落空；命令行独立容器再连 SQLite —— 双容器写同一库，锁冲突。

## R-9. Spring AI 自动 tool 执行的关闭方式

**Decision**: 构建 `ChatClient`/`ChatModel` 调用时显式不注册内部 ToolCallback（不走 `.tools()`/`.functions()` 自动执行路径），tool schema 以只读方式提供给自实现 `ReActLoop`/`ToolExecutor`；启动时以集成测试断言"一次带 tool 意图的调用只产生一条 tool_invocations 审计记录"作为防回归门禁。

**Rationale**: CLAUDE.md 常见陷阱 #1（tool 被调两次）；测试门禁比依赖库版本行为更可靠。

**Alternatives considered**: 依赖配置项关闭 —— 里程碑版本配置语义不稳定，不做唯一防线。

## R-10. 会话上下文截断与归档参数

**Decision**: 上下文超限截断保留近期对话（简单截断，无摘要）；归档阈值默认 30 分钟闲置，按 Agent `settings.session_timeout_minutes` 覆盖；长期记忆注入上限 4000 字截断。三者均为启动加载、重启生效。

**Rationale**: 对应 spec 澄清 #2/#5 与 FR-017/018；核心阶段不做摘要压缩（需求文档明确留扩展阶段）。

**Alternatives considered**: token 精确计数截断 —— 需要每模型 tokenizer，成本高收益低；全局（非 Agent 级）超时 —— 与"配置即 Agent"粒度不一致。

## R-11. 打包与分发：单可执行 JAR

**Decision**: `oryxos-boot` 经 spring-boot-maven-plugin repackage 产出单一可执行 JAR，`bin/` 提供 `oryxos` 启动脚本（`java -jar` 包装）；核心阶段不做 GraalVM Native Image（需求文档：核心阶段结束后再评估）。

**Rationale**: 需求文档核心特性"单可执行 JAR 单二进制部署"；复用现有 Java 运维工具链。

**Alternatives considered**: GraalVM Native —— 启动快但反射/MCP/SQLite JNI 兼容成本高，留扩展阶段；os-shell 安装包 —— 超出 4 周范围。

## R-12. 测试与验收基线

**Decision**: 每模块 JUnit 5 单测；跨模块集成测试聚焦两条：① ReAct 单链路（mock Provider + 真实 ToolExecutor，断言审计条数）；② 10 端点契约测试（MockMvc 级，LLM 打桩）。端到端验收不自动化进 CI，按 quickstart.md 手工执行（依赖真实 LLM key 与外网信息源）。

**Rationale**: 宪法/需求文档验收标准以"可演示成果"为准；CI 无法稳定持有真实密钥与外网依赖。

**Alternatives considered**: 端到端全自动进 CI —— 外部依赖不稳定，红绿噪声大；完全不写集成测试 —— 无法守住 R-9 的防回归门禁。
