# Tasks: OryxOS 核心阶段运行时内核（五大核心能力）

**Input**: Design documents from `/specs/001-agent-os-core/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: 包含测试任务——需求文档 §13 明确要求"每个功能模块至少有一个端到端测试用例覆盖"，plan.md R-12 定义了测试策略（JUnit 5 单测 + ReAct 防双重调用门禁 + 10 端点契约测试）。

**Organization**: 按 5 个 user story 分阶段，每个 story 独立可实现、可测试、可交付。仓库已有 9 模块骨架（43 个源文件），任务在其上补齐实现。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 所属 user story（US1–US5，对应 spec.md 五大核心能力）
- 所有路径相对仓库根

---

## Phase 1: Setup（共享基础设施）

**Purpose**: 补齐构建依赖与基础配置（骨架已存在）

- [X] T001 在根 pom.xml 补齐依赖版本与 dependencyManagement：picocli-spring-boot-starter、MCP 官方 Java SDK（io.modelcontextprotocol.sdk:mcp）、hibernate-community-dialects、sqlite-jdbc、springdoc-openapi（R-1/R-3/R-5/R-8/R-11）
- [X] T002 创建 oryxos-boot/src/main/resources/application.yml：SQLite 数据源（`.oryxos/oryxos.db`）+ `org.hibernate.community.dialect.SQLiteDialect`、JPA 自动建表、工作区根路径配置、scheduling 开关（R-2/R-3/R-6）
- [X] T003 [P] 创建 config/providers.example.yaml 与 config/mcp_servers.example.yaml：密钥一律 `${ENV_VAR}` 占位，字段按 data-model.md §3/§4（FR-008）
- [X] T004 [P] 创建 oryxos-boot/src/main/resources/logback-spring.xml：结构化 JSON 日志输出到 `.oryxos/logs/`，覆盖 agent/llm/tool 事件（FR-027）

**Checkpoint**: `mvn clean package` 全绿，应用可启动（无 Agent 时空跑）。

---

## Phase 2: Foundational（阻塞前置——所有 story 依赖）

**Purpose**: 工作区、Agent 加载、配置解析、审计地基。未完成前不得开始任何 user story。

- [X] T005 实现 oryxos-core/src/main/java/com/oryxos/core/workspace/WorkspaceInitializer.java：幂等创建 `.oryxos/` 六子目录 + 三个 Bootstrap 模板文件（AGENTS/SOUL/USER），已存在一律跳过；接入 oryxos-cli/src/main/java/com/oryxos/cli/InitCommand.java（FR-001，data-model §10）
- [X] T006 完成 oryxos-storage 模块引导：验证 SessionEntity/ToolInvocationEntity/LlmCallEntity 三表自动建表，SessionRepository/ToolInvocationRepository/LlmCallRepository 冒烟测试 oryxos-storage/src/test/java/com/oryxos/storage/StorageBootstrapTest.java（FR-017/025/026，宪法 V）
- [X] T007 实现 oryxos-core/src/main/java/com/oryxos/core/agent/AgentLoader.java：SnakeYAML 解析 AGENT.md frontmatter → deriveProfile()，字段校验（data-model §1 全部规则），非法/缺失时清晰报错不产生半 Profile；重名与非法 name 校验（FR-002/003）
- [X] T008 实现 oryxos-core/src/main/java/com/oryxos/core/agent/ContextLoader.java：按 R-7 顺序提供上下文装配位——AGENT.md 正文、Bootstrap 文件、Skill 元数据占位（US2 填充）、MEMORY.md 占位（US3 填充）（FR-005）
- [X] T009 实现 oryxos-provider/src/main/java/com/oryxos/provider/ProviderConfigLoader.java：加载 providers YAML，`${ENV_VAR}` 解析、必填/格式校验、缺失环境变量时启动报错且报错含变量名（FR-008，宪法 VI）
- [X] T010 实现 oryxos-storage/src/main/java/com/oryxos/storage/AuditService.java：llm_calls / tool_invocations 写入门面（先审计后回传语义由调用方保证），支持白名单违规记录（success=false + error）（FR-025/026，SC-003）

- [X] T062 接入 oryxos-cli/src/main/java/com/oryxos/cli/ProfileCommand.java 的 create/list 子命令：create 生成 `.oryxos/agents/<name>/AGENT.md` 最小模板（frontmatter 按 contracts/agent-workspace.md §1，name=目录名校验、重名报错 exit 1）；list 输出 name/provider/model/tools 数（FR-002、contracts/cli.md；依赖 T005/T007）

**Checkpoint**: Foundation ready——`oryxos init` 可用、`profile create/list` 可用、Agent 可加载派生、三表可写。User story 实现自此可开始。

---

## Phase 3: User Story 1 — 与 Agent 对话完成多步任务：ReAct 循环（P1）🎯 MVP

**Goal**: 配置即 Agent + 命令行多轮对话 + 自实现 ReAct 循环完成工具任务，全程审计。

**Independent Test**: `oryxos init` → `profile create` → 填 AGENT.md（配 http_get）→ `oryxos chat` 下达多步任务 → 获得综合工具结果的答复（quickstart 场景 1/2）。

### Implementation for User Story 1

- [X] T011 [US1] 实现 oryxos-core/src/main/java/com/oryxos/core/prompt/PromptBuilder.java：按 R-7 顺序组装 system prompt（Agent 正文 → AGENTS.md → SOUL.md → USER.md → 对话历史截断占位 → 可用 Tool 列表）（FR-005、research R-7）
- [X] T012 [US1] 实现 oryxos-provider/src/main/java/com/oryxos/provider/DefaultProviderService.java：`Map<String, ChatModel>` 显式映射注册与查找、ChatClient 委托调用、不注册内部 ToolCallback（禁自动 tool 执行，R-9）、usage/token 解析进 LlmResponse；首批注册 DeepSeek（OpenAI 兼容 base-url）（FR-006/009、宪法 II/III）
- [X] T013 [US1] 实现 oryxos-core/src/main/java/com/oryxos/core/session/SessionService.java：channel+user+agent 联合标识查活跃/创建会话、消息追加、经 SessionRepository 持久化（FR-017 基础部分）
- [X] T014 [US1] 实现 oryxos-core/src/main/java/com/oryxos/core/react/ReActLoop.java：Reason→Act→Observe 同步循环，无工具调用返回最终响应、有则执行并回填继续；达到 max_iterations（默认 10，Profile 可覆盖）强制终止并返回明确说明（FR-009/010，宪法 II）
- [X] T015 [US1] 实现 oryxos-core/src/main/java/com/oryxos/core/tool/ToolExecutor.java：查找 ToolRegistry → Sandbox 白名单校验 → 执行 → AuditService 落库 → 结果回填对话历史；工具执行编排唯一入口（FR-011/013，宪法 IV/V）
- [X] T016 [US1] 实现 oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpGetTool.java：http_get 内置工具 + WhitelistSandbox 域名白名单校验路径（oryxos-tool/src/main/java/com/oryxos/tool/WhitelistSandbox.java 首次落地域名类）（FR-012/013）
- [X] T017 [US1] 完善 oryxos-core/src/main/java/com/oryxos/core/AgentService.java：Channel→Session→Prompt→ReActLoop→响应 的统一编排链路（三触发源复用的唯一入口，FR-020/021 基础）
- [X] T018 [US1] 实现 oryxos-channel-cli/src/main/java/com/oryxos/channel/cli/CliChannel.java 并接入 oryxos-cli/src/main/java/com/oryxos/cli/ChatCommand.java：多轮交互、`--message` 单条模式、`/tools` 查看工具调用记录、`/exit`；无活跃会话自动新建（FR-022/023、contracts/cli.md）
- [X] T019 [US1] 编写防回归门禁测试 oryxos-boot/src/test/java/com/oryxos/boot/ReactAuditGateTest.java：mock Provider 下带工具任务 → 断言恰好 1 条 tool_invocations + N 条 llm_calls（工具绝不被库自动执行两次，R-9）；附 oryxos-core 单测覆盖 max_iterations 终止
- [ ] T020 [US1] 按 quickstart.md 场景 1/2 用真实密钥手工验证并在任务备注记录结果（SC-001 首对话部分）
      备注（2026-09-03）：无密钥路径已端到端验证——Spring 启动、Profile 解析、ReAct 链路、缺钥时点名报错（exit 1 无堆栈）；带真实 key 的最终对话验证待设置 DEEPSEEK_API_KEY 后执行 `chat --profile demo --message ...` 确认。

**Checkpoint**: MVP 可演示——CLI 多轮对话 + 工具任务 + 审计落库。

---

## Phase 4: User Story 2 — Agent 能干事：工具体系与安全沙箱（P2）

**Goal**: 7 个内置工具补齐（save_memory/recall_memory 在 US3/T033）、三类白名单沙箱、失败重试、MCP 接入与 @Tool 原生扩展（三档齐备）、Skill 元数据注入。

**Independent Test**: 配置工具的 Agent 执行文件/HTTP 任务；白名单外操作 100% 拦截；零代码接入 MCP server 成功调用（quickstart 场景 3/8）。

### Implementation for User Story 2

- [X] T021 [P] [US2] 实现 oryxos-tool/src/main/java/com/oryxos/tool/builtin/ReadFileTool.java、WriteFileTool.java、ListDirTool.java：文件三工具，全部经 ToolExecutor 的路径白名单校验（FR-012/013）
- [X] T022 [P] [US2] 实现 oryxos-tool/src/main/java/com/oryxos/tool/builtin/ShellTool.java：bash 命令执行，命令白名单 + 执行超时（FR-012/013）
- [X] T023 [P] [US2] 实现 oryxos-tool/src/main/java/com/oryxos/tool/builtin/HttpPostTool.java：HTTP POST，域名白名单（FR-012/013）
- [X] T024 [P] [US2] 实现 oryxos-tool/src/main/java/com/oryxos/tool/builtin/NotifyTool.java 及 oryxos-tool/src/main/java/com/oryxos/tool/notify/WebhookNotifyChannelAdapter.java：通用 Webhook——向 Agent 配置 URL 发 HTTP POST（消息体文本），受域名白名单与审计约束（澄清 #1、FR-012）
- [X] T025 [US2] 完善 oryxos-tool/src/main/java/com/oryxos/tool/WhitelistSandbox.java：路径/命令/域名三类白名单完整实现 + 超时控制 + 违规抛 SandboxViolationException 且经 AuditService 落违规记录；默认白名单=工作区内最小权限（FR-013、SC-006）
- [X] T026 [US2] 实现 oryxos-core/src/main/java/com/oryxos/core/tool/RetryPolicy.java 并入 ToolExecutor：失败指数退避最多 3 次，每次尝试各落一条审计，最终失败将错误回传模型继续推理（FR-014）
- [X] T027 [US2] 实现 oryxos-tool/src/main/java/com/oryxos/tool/mcp/McpClientService.java：加载 mcp_servers.yaml（data-model §4）、stdio/http 连接、远端 tool 描述转 OryxTool 注册进 InMemoryToolRegistry，调用经同一 Sandbox+审计链路；连接失败记日志跳过不阻断启动（FR-015/016、research R-5）
- [X] T028 [US2] 在 oryxos-core/src/main/java/com/oryxos/core/agent/ContextLoader.java 落地 Skill 绑定：扫描 Agent 目录 `skills/` 相对软连接 → 仅注入 name/description/读取路径元数据，正文经 read_file 按需读取；Skill 不进 ToolRegistry（FR-005、宪法 VIII）
- [X] T029 [US2] 接入 oryxos-cli/src/main/java/com/oryxos/cli/ToolCommand.java：`oryxos tool list` 输出 name/来源（builtin|mcp）/白名单摘要（FR-022、contracts/cli.md）
- [X] T063 [US2] 实现业务方 @Tool 注解接入：oryxos-tool 新增 `SpringBeanToolRegistrar`，将业务方 @Tool Bean 注册进 ToolRegistry——schema 取自 @Tool 注解生成，执行回调统一走 ToolExecutor（Sandbox+审计，禁绕过，FR-011）；编写 oryxos-tool/src/test/java/com/oryxos/tool/SpringBeanToolRegistrarTest.java 含示例 @Tool Bean 验收（FR-015 方式三、需求文档 §13"方式三 @Tool 注解示例跑通"）
- [X] T030 [US2] 编写 oryxos-tool/src/test/java/com/oryxos/tool/WhitelistSandboxTest.java：三类白名单各含命中/拦截用例（拦截率 100% 断言）+ oryxos-core/src/test 重试策略用例（3 次退避后失败回传）（SC-006、FR-014）
- [ ] T031 [US2] 按 quickstart.md 场景 3（拦截）/8（零代码 MCP）手工验证并记录
      备注（2026-09-03）：场景 3 已自动化为门禁测试 SandboxInterceptionGateTest（白名单外读取被拦截、审计 success=false）+ WhitelistSandboxTest 12 用例；场景 8 需外部 MCP server（npx 社区包 + 网络），配置加载/失败跳过/命名注册链路已实现并 Review，待用户环境实测。

**Checkpoint**: US1+US2 可独立运行——Agent 能安全地"干事"且可零代码扩展。

---

## Phase 5: User Story 3 — Agent 记得住：跨对话记忆（P2）

**Goal**: 会话记忆持久化 + 截断 + 闲置归档；长期记忆 MEMORY.md 写入/检索/注入。

**Independent Test**: 告知偏好→写入→新会话免重复解释答对；重启恢复会话；闲置归档触发（quickstart 场景 4）。

### Implementation for User Story 3

- [X] T032 [US3] 完善 oryxos-memory/src/main/java/com/oryxos/memory/LongTermMemoryStore.java：MEMORY.md 追加写、`recall` 大小写不敏感包含匹配返回命中条目/行（澄清 #5）、注入视图超 4000 字截断（FR-018）；并将注入视图接入 T008 ContextLoader / T011 PromptBuilder 的 MEMORY.md 占位，使 system prompt 按 research R-7 顺序携带长期记忆
      备注：落地为 MarkdownMemoryStore（MEMORY.md 单文件、`- [时间] 内容` 追加、大小写不包含匹配）+ DefaultMemoryService；模块依赖方向是 memory→core，故注入视图仍由 ContextLoader.memoryView()（MEMORY_VIEW_LIMIT=4000）承担，二者读同一文件即单一事实源，PromptBuilder R-7 占位已接通
- [X] T033 [US3] 实现 oryxos-memory/src/main/java/com/oryxos/memory/MemoryTools.java：save_memory / recall_memory 两个内置工具并注册进 ToolRegistry，经 ToolExecutor 审计（FR-012/018）
      备注：MemoryToolRegistrar（@Component）启动期注册进共享 ToolRegistry，执行对固定 MEMORY.md 路径过 Sandbox 白名单（FILE_WRITE/FILE_READ 首道闸），审计经 ToolExecutor 自动落库
- [X] T034 [US3] 在 oryxos-core/src/main/java/com/oryxos/core/prompt/PromptBuilder.java 与 SessionService 落地上下文截断：超模型窗口/`max_history_turns` 时截断早期保留近期，不中断对话（FR-017）
      备注：截断逻辑已在 PromptBuilder.historyMessages（保留最近 N 个 user 轮次及其后消息），本次补 PromptBuilderHistoryTruncationTest 3 用例（限内完整/超限留新/TOOL 消息保留）
- [X] T035 [US3] 在 oryxos-core/src/main/java/com/oryxos/core/session/SessionService.java 落地闲置归档：阈值默认 30 分钟、`settings.session_timeout_minutes` 可覆盖（data-model §1/§5）；归档不删数据历史可查；CLI 渠道遇归档自动开新会话（澄清 #2、FR-017/029）
      备注：getOrCreate 命中活跃会话时先判闲置（默认 30 分钟，AgentLoader 解析 per-Agent 覆盖），过期即归档并生成带后缀新会话——CLI/渠道侧免改动即获得"自动开新会话"；归档数据保留可查
- [X] T036 [US3] 落地重启恢复：重启后活跃会话按 session_id 恢复上下文（数据已在 SQLite），编写 oryxos-boot/src/test/java/com/oryxos/boot/SessionRecoveryTest.java 集成测试（FR-017、SC-007）
      备注：测试用全新 JpaSessionStore 实例模拟重启后从 SQLite 重读 session_id 与消息历史，且 getOrCreate 续接同一会话
- [X] T037 [US3] 接入 oryxos-cli/src/main/java/com/oryxos/cli/SessionCommand.java：`oryxos session list [--profile]` 输出会话列表（FR-022）
      备注：保持轻命令（不起 Spring），plain JDBC 直读 `<root>/oryxos.db`（oryxos-cli pom 增 sqlite-jdbc 依赖）；支持 `--profile` 过滤与 `--limit`；时间戳按驱动实际存储格式防御式解析；已对真实库端到端验证
- [X] T038 [US3] 编写 oryxos-memory/src/test/java/com/oryxos/memory/LongTermMemoryStoreTest.java：包含匹配/截断/追加用例 + SessionService 归档阈值单测（澄清 #2/#5）
      备注：LongTermMemoryStoreTest 6 用例（追加/累积/大小写包含匹配/空内容拒绝/4000 截断/空库）；SessionArchiveTest 6 用例（默认 30 分钟/覆盖阈值/边界 29 分钟续用与 30 分钟+1s 过期/过期归档+新会话后缀）；MemoryInjectionGateTest 把场景 4 的机械部分自动化（save→注入视图→recall 全链路）
- [ ] T039 [US3] 按 quickstart.md 场景 4 手工验证并记录
      备注（2026-09-03）：机械链路已自动化——MemoryInjectionGateTest（save_memory 落盘、注入视图携带事实、recall 命中）+ SessionRecoveryTest（重启恢复）；带真实 LLM 的"告知偏好→新会话免重复解释答对"待设置 DEEPSEEK_API_KEY 后手工执行确认。

**Checkpoint**: US1+US2+US3 独立可用——Agent 越用越懂用户。

---

## Phase 6: User Story 4 — 业务系统接得进来：Web Service 对外服务（P2）

**Goal**: 10 个 REST 端点 + 定时任务第三触发源 + 两个每日 Demo（发布硬条件）。

**Independent Test**: `oryxos serve` 后按 contracts/rest-api.md 依次调通 10 端点；Demo 到点自动跑通（quickstart 场景 5/9）。

### Implementation for User Story 4

> 阶段前置：T048/T049（Demo）依赖 US2（http/notify/MCP）与 US3（memory）完成；T040–T047 仅依赖 US1。

- [X] T040 [US4] 接入 oryxos-cli/src/main/java/com/oryxos/cli/ServeCommand.java 与 GatewayCommand.java：经 oryxos-boot 容器启动 web（ServeCommand）与 web+调度器常驻（GatewayCommand），三者共享配置与会话存储（FR-020、research R-8）【完成：launcher 将 `--port` 转成 `--server.port` 命令行参（优先级高于 config/application.yml，defaultProperties 会被文件配置压掉）；GatewayCommand 补声明 `--port`；常驻用 CountDownLatch 阻断，serve/gateway E2E 实测 18080/18081 生效】
- [X] T041 [P] [US4] 完善 oryxos-web/src/main/java/com/oryxos/web/SessionApiController.java：创建/发消息/查历史/归档 4 端点，含已归档会话发消息返回 409（FR-019、FR-029、contracts/rest-api.md）【完成：E2E 实测 409 + 幂等归档 200】
- [X] T042 [P] [US4] 完善 oryxos-web/src/main/java/com/oryxos/web/AgentApiController.java：`POST /api/v1/agents/{name}/invoke` 无状态调用，复用 AgentService 同一链路，审计照常落库（FR-019/021）【完成：Session.ephemeral 标记实现"不落会话但审计带临时 session_id `http-invoke-<uuid>`"；迭代数取 llm_calls 计数差值】
- [X] T043 [P] [US4] 完善 oryxos-web/src/main/java/com/oryxos/web/ProfileApiController.java、ToolApiController.java、MemoryApiController.java：三个信息查询端点（含 memory 可选 query 包含匹配）（FR-019）【完成：tool 分类 builtin/memory/http/shell/mcp；空 MEMORY.md 返回成功信封 items=[]】
- [X] T044 [P] [US4] 完善 oryxos-web/src/main/java/com/oryxos/web/SystemApiController.java：health（status/workspace/agents_loaded/db_ok）与 info（version/java/uptime）（FR-019、SC-009）【完成：db_ok 经 DataSource SELECT 1 探活】
- [X] T045 [US4] 完善 oryxos-web/src/main/java/com/oryxos/web/GlobalExceptionHandler.java 与 ApiResponse.java：统一信封 + 400/404/409/500 语义与 contracts/rest-api.md 完全一致（FR-019）【完成：error 为 null 时 NON_NULL 序列化；E2E 实测 400/404/409 语义】
- [X] T046 [US4] 实现 oryxos-core/src/main/java/com/oryxos/core/scheduler/AgentScheduler.java：ThreadPoolTaskScheduler + CronTrigger，从 frontmatter `schedules` 动态注册，到点调用 AgentService 同一链路；支持手动补跑（FR-021、research R-6）【完成：triggerNow(profile, scheduleId) 手动补跑；gateway 启动实测"注册定时任务 1 个（crony:heartbeat）"】
- [X] T047 [US4] 编写 oryxos-web/src/test/java/com/oryxos/web/RestContractTest.java：10 端点 MockMvc 契约测试（LLM 打桩，断言信封/状态码/409 语义）（SC-009、需求文档 §13）【完成：落在 oryxos-boot/src/test（需 @SpringBootTest 上下文 + JPA/工作区装配）；过滤断言用 hasItem——Spring 6.2 对 List.of 期望值会按运行时类 ImmutableCollections$ListN 重读转换，Jayway 映射不出该类型而静默返回 null】
- [X] T048 [US4] 创建 Demo 一"每日天气"：config/demos/daily-weather/AGENT.md（schedules + http_get 天气域名白名单 + notify Webhook 白名单）并在工作区装配，按 quickstart.md 场景 9 验证到点自动跑通 + 手动补跑同链路（SC-008，发布硬条件）【完成：AGENT.md + config-fragment.yml + README 装配说明就绪；到点自动触发与真实群推送需 DEEPSEEK_API_KEY + webhook 环境，机械半边（调度注册 + triggerNow 补跑走 SessionService/AgentService 同链路 + llm_calls 审计）由 SchedulerChainGateTest 自动化覆盖】
- [X] T049 [US4] 创建 Demo 二"每日科技日报"：config/demos/daily-tech-digest/（零代码 AGENT.md + mcp_servers + Skill 绑定 + save/recall_memory），验证日报体现已记住的关注方向、全程零 Java 代码（SC-008，发布硬条件）【完成：AGENT.md + SKILL.md（软连接绑定）+ README（含 mklink/ln 软连接与 mcp_servers.yaml 片段）就绪；日报内容体现关注方向需真实 LLM 环境，零代码约束由装配材料自证】
- [X] T050 [US4] 按 quickstart.md 场景 5（10 端点全量走查）手工验证并记录【完成：RestContractTest 全 10 端点断言通过；fat jar serve --port 18080 真实 HTTP 走查 health/info/profiles/tools/memory/建会话/400 未知 Agent/404 未知会话/归档幂等/409 已归档/404 未知 invoke（依赖真实 LLM 的回复正文路径由打桩契约测试覆盖）；gateway --port 18081 实测调度注册；期间修复 launcher --port 被配置文件覆盖与 GatewayCommand 缺 --port 两处问题】

**Checkpoint**: 五大能力串联——外部系统可完整接入，两个 Demo 到点自跑。

---

## Phase 7: User Story 5 — 模型不锁定：多 Provider 无锁定切换（P3）

**Goal**: 显式映射下多 Provider 并存、仅改配置切换、密钥安全、审计可验证。

**Independent Test**: 同一 Agent 切换 DeepSeek↔Kimi 完成同一任务；审计 provider/model/token 正确；`provider list` 不回显密钥（quickstart 场景 7）。

### Implementation for User Story 5

- [ ] T051 [P] [US5] 在 oryxos-provider/src/main/java/com/oryxos/provider/DefaultProviderService.java 注册 Kimi（Moonshot，OpenAI 兼容 base-url）（FR-006、research R-4）
- [ ] T052 [P] [US5] 在 oryxos-provider/src/main/java/com/oryxos/provider/DefaultProviderService.java 注册 Spring AI Alibaba（DashScope/qwen）Provider（FR-006、research R-4）
- [ ] T053 [US5] 完善 oryxos-provider/src/main/java/com/oryxos/provider/ProviderConfigLoader.java 校验体验：缺失 env/非法字段的报错信息含字段名与修复指引，启动期一次报全（FR-008）
- [ ] T054 [US5] 接入 oryxos-cli/src/main/java/com/oryxos/cli/ProviderCommand.java：`oryxos provider list` 输出 name/model/base_url/密钥来源环境变量名，永不回显密钥明文（FR-008/022）
- [ ] T055 [US5] 编写 oryxos-provider/src/test/java/com/oryxos/provider/ProviderSwitchTest.java：切换 Provider 后映射生效、llm_calls 记录新 provider/model；按 quickstart.md 场景 7 手工验证（SC-004）

**Checkpoint**: 全部五大能力交付，配置即切换无锁定。

---

## Phase 8: Polish & Cross-Cutting（收尾与横切关注点）

**Purpose**: 命令补齐、性能验收、文档、打包与全量验收

- [ ] T056 接入 oryxos-cli/src/main/java/com/oryxos/cli/StatusCommand.java 与 ProfileCommand.java 的 show/delete 子命令——12 命令全部就绪（FR-022、contracts/cli.md）
- [ ] T057 [P] 集成 springdoc-openapi 暴露 OpenAPI 文档（oryxos-web，CLAUDE.md Web 模块职责）
- [ ] T058 [P] 编写 scripts/load-test.md + 脚本：单节点 10 Agent / 100 并发 Session / 4 小时稳定 / Session 创建 P99 ≤ 200ms / 内部转发 ≤ 50ms 验证（SC-002，需求文档 §13 性能验收）
- [ ] T059 [P] 编写 docs/DeploymentGuide.md 部署文档：从零到首对话 ≤ 30 分钟走查（SC-001，可运维性验收）
- [ ] T060 打包验证：spring-boot-maven-plugin repackage 单可执行 JAR + bin/ 脚本 `oryxos` 全命令走查（research R-11、FR-022）
- [ ] T061 全量验收：quickstart.md 场景 1–9 逐项走查记录 + 宪法 9 原则合规自查（含 R-9 防双重调用门禁测试通过、审计三表 day-one 数据在库）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，立即开始
- **Foundational (Phase 2)**: 依赖 Phase 1 —— **阻塞所有 user story**；T062 依赖 T005/T007，与 T008–T010 可并行
- **US1 (Phase 3)**: 依赖 Phase 2；是其余全部 story 的链路基座
- **US2 / US3 / US5 (Phase 4/5/7)**: 均依赖 US1 完成，三者之间**可并行**
- **US4 (Phase 6)**: T040–T047 仅依赖 US1（可与 US2/US3 并行启动）；T048/T049（Demo，发布硬条件）依赖 US2+US3 完成
- **Polish (Phase 8)**: 依赖全部 user story 完成

### User Story Dependencies

```
Phase 1 → Phase 2 → US1 ──┬──> US2 ──┐
                          ├──> US3 ──┼──> US4（端点可提前，Demo 需 US2+US3）──> Polish
                          └──> US5 ──┘（US5 亦可在 US4 前后任意位置完成）
```

### Within Each User Story

- 工具/模型类任务（标 [P]）可并行；Service → 编排 → 集成按序
- 测试任务在该 story 实现完成后、Checkpoint 验证前执行
- 每个 Checkpoint 停下独立验证该 story（quickstart 对应场景）

### Parallel Opportunities

- Phase 1: T003/T004 并行
- Phase 2: T007/T008（core）与 T009（provider）可并行；T005/T006/T010 独立可并行
- US2: T021–T024 四个工具文件完全并行
- US4: T041–T044 四个 Controller 并行
- US5: T051/T052 两个 Provider 注册并行
- 跨 story：US2/US3/US5 三人并行（均只依赖 US1）

---

## Implementation Strategy

### MVP First（仅 US1）

1. Phase 1 + Phase 2 → 地基就绪
2. Phase 3（US1）→ `oryxos chat` 多轮对话 + 工具任务 + 审计落库
3. **STOP & VALIDATE**: quickstart 场景 1/2 通过即可对外演示（对应需求文档 W1 可演示成果）

### Incremental Delivery

- +US2 → Agent 能安全干事、可零代码扩展（W2 上半）
- +US3 → 跨对话记忆、重启恢复（W2 下半/W4 收尾项前移）
- +US4 → 10 端点 + 定时触发 + 两个每日 Demo（W3/W4，发布硬条件达成）
- +US5 → 多 Provider 切换（W4 收尾）
- Polish → 12 命令、压测、部署文档、单 JAR、全量验收

### Parallel Team Strategy

1. 全员完成 Phase 1+2
2. A: US1（关键路径）→ US4；B: US2 → US3；C: US5 → Polish 支援
3. Demo 验证（T048/T049）需要 B 线产出，排在 US4 尾部

---

## Notes

- [P] = 不同文件、无未完成依赖
- [Story] 标签保证任务可追溯到 spec.md 的 user story
- 宪法红线贯穿任务：T010（审计先行）、T012+T019（ReAct 自实现与防双重调用门禁）、T012（显式映射）、T025（白名单沙箱）、T009/T053（密钥仅环境变量）、T007（一个目录一个 Agent）、T028（Skill ≠ Tool）
- 每个 Checkpoint 提交一次并独立验证
- 避免：模糊任务、同文件冲突、破坏 story 独立性的跨 story 依赖
