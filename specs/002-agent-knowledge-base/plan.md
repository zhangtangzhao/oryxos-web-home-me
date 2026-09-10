# Implementation Plan: 给 Agent 增加知识库

**Branch**: `002-agent-knowledge-base` | **Date**: 2026-09-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-agent-knowledge-base/spec.md`

## Summary

为 OryxOS 增加 Agent 中心的知识库能力：管理员创建具名知识库并手动摄取
Markdown/纯文本文档（分段 + 双路索引）；Agent 在 `AGENT.md` 中按名绑定知识库，
在对话循环中以 `kb_search` / `kb_overview` 两个工具检索，回答携带可核对引用；
检索融合语义（外部 OpenAI 兼容嵌入服务）与关键词（中文二元切分 + 倒排索引）
两路并加权 RRF 合并；每次检索经既有 `tool_invocations` 审计落库（含零结果/
降级标志）；嵌入模型身份记录于知识库元数据，不兼容即拒绝检索。增量摄取按
内容指纹只处理变化文档。新增 `oryxos-kb` Maven 模块（结构镜像 `oryxos-memory`：
服务门面 + 工具工厂 + 注册器），索引数据存 SQLite（JPA 实体 + FTS5 派生表），
管理面提供 CLI 子命令组与 REST 端点。

## Technical Context

**Language/Version**: Java 21（同步执行模型，平台线程，禁虚拟线程与响应式框架）

**Primary Dependencies**: Spring Boot 3.4.0、Spring AI 1.0.0-M5（**维持不动**，
仅 `DefaultProviderService` 一处使用裸 `OpenAiApi` 协议类型；嵌入调用走独立
`RestClient` 直连 OpenAI 兼容 `/embeddings`，不扩大 Spring AI 接触面）、
Spring Data JPA + Hibernate community SQLiteDialect、Picocli 4.7.6、SnakeYAML
（AGENT.md frontmatter）、Jackson（schema 与审计 JSON）

**Storage**: SQLite（既有 `oryxos.db`，WAL 拓扑）。新增三张 JPA 表
`kb_knowledge_bases` / `kb_documents` / `kb_chunks`（ddl-auto=update 建新表
可行），外加一张 JPA 无法映射的 FTS5 虚拟表 `kb_chunks_fts`（启动时
`CREATE VIRTUAL TABLE IF NOT EXISTS` 手工建，可随时重建的派生索引）。所有
写路径经既有 `SqliteWriteGate`。知识库原文存 `<root>/kb/<name>/`（工作区内）。

**Testing**: JUnit 5（各模块 `src/test/java`，仓库既有 53 个测试同栈）；
端到端验证按 quickstart.md 场景跑通（构建 + 真实启动 + 会话）

**Target Platform**: 单节点私有部署（Windows/Linux 服务器），数据不出域
（存储语义；嵌入处理可发送内容至所选服务，见 spec Clarifications）

**Project Type**: 多模块 Maven 库 + CLI + Web 服务（10 模块 → 11 模块）

**Performance Goals**: 检索 P99 ≤ 3s（含外部嵌入服务往返，SC-003）；服务端
内部检索计算（向量余量 + 关键词合并）P99 ≤ 200ms；100 篇文档摄取 ≤ 10 分钟
（SC-001，含嵌入 API 往返）

**Constraints**: 同步模型（无异步框架）；写事务全程 `SqliteWriteGate` 互斥；
凭证只走环境变量（宪法 VI）；索引派生数据存工作区内（FR-012）；语义服务为
必需依赖、未配置点名报错（spec 澄清 2）；摄取仅手动触发（spec 澄清 4）；
重排服务本期不配置、留降级槽位（FR-010）

**Scale/Scope**: 不设数量硬上限（spec 澄清 3），按性能预算验收；设计锚点
为百篇文档 / 万级分段量级（向量暴力余弦 + 每 KB 向量缓存即达标；sqlite-vec
留作后续档次）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | 原则 | 影响 | 判定 |
|---|------|------|------|
| I | Java 21 同步执行模型 | 嵌入 HTTP 调用用同步 `RestClient`；检索流水线同步串行；无 Reactor/CompletableFuture | PASS |
| II | ReAct Loop 自实现 | `kb_search`/`kb_overview` 是普通 `OryxTool`，经 `ToolExecutor` 受控执行 + 审计；不触碰 Spring AI tool 执行 | PASS |
| III | Provider 显式映射 | 嵌入服务独立配置 `oryxos.kb.embedding`（base-url/api-key-env/model），不与 chat Provider 混用类型扫描 | PASS |
| IV | 白名单沙箱 | KB 工具读文件经 `Sandbox.enforce(FILE_READ, path)`；`<root>` 默认已在路径白名单。嵌入 HTTP 客户端与 `DefaultProviderService` 同类（Provider 基础设施，不经 Sandbox），文档明示 | PASS |
| V | 审计先行 | 检索审计复用 `tool_invocations`（`ToolExecutor.java:65` 自动落库），结果 JSON 携带 `zero_result`/`degraded`/得分/耗时；不新增第六张核心表 | PASS |
| VI | 敏感配置只走环境变量 | 嵌入服务 `api-key-env` 指向环境变量，YAML 仅占位；缺失时点名报错 | PASS |
| VII | 一个目录 = 一个 Agent | Agent 绑定以 `AGENT.md` frontmatter `knowledge_bases` 列表按名引用（同 channels 模式）；KB 不实现为 Agent 目录 | PASS |
| VIII | Skill ≠ Tool | 知识库 ≠ Skill：Skill 是注入 system prompt 的知识资产，KB 是可检索数据 + 检索工具；两者并存不混淆 | PASS |
| IX | 接口先行 | `EmbeddingClient`、`KbStore`（端口）先定接口，v1 各挂一档实现（OpenAI 兼容 HTTP / SQLite）；重排以 `Reranker` 接口留槽、默认无实现走降级 | PASS |

**模块边界说明**：新增 `oryxos-kb` 而非塞入 `oryxos-tool`。宪法反模式"Tool
模块拆太细"指的是 Tool 家族内容不得从 `oryxos-tool` 再拆分；而 KB 是与
Memory 同构的**能力模块**（服务门面 + 能力工具 + 存储），先例即
`oryxos-memory` 的 `MemoryTools`/`MemoryToolRegistrar` 模式。非违例，架构
判断记录于 research.md D7。

## Project Structure

### Documentation (this feature)

```text
specs/002-agent-knowledge-base/
├── plan.md              # 本文件
├── research.md          # Phase 0：技术决策（嵌入接入/存储形态/检索流水线/升级基线）
├── data-model.md        # Phase 1：实体、表结构、状态机
├── quickstart.md        # Phase 1：端到端验证场景
├── contracts/           # Phase 1：REST / CLI / 工具 schema 契约
│   ├── rest-api.md
│   ├── cli.md
│   └── agent-tools.md
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成，本命令不建）
```

### Source Code (repository root)

```text
oryxos-kb/                                # 新模块（镜像 oryxos-memory 结构）
├── pom.xml
└── src/main/java/com/oryxos/kb/
    ├── EmbeddingClient.java              # 端口接口（宪法 IX）
    ├── Reranker.java                     # 端口接口（本期无实现，降级直通）
    ├── KbStore.java                      # 端口接口（知识库元数据/文档/分段存取）
    ├── KbChunker.java                    # 分段接口
    ├── KbSearchService.java              # 检索编排：嵌入→双路→RRF→（重排槽）→引用
    ├── KbIngestService.java              # 摄取编排：指纹比对→分段→批量嵌入→落索引
    ├── DefaultKbService.java             # KbService 门面（管理操作）
    ├── OpenAiCompatEmbeddingClient.java  # RestClient 直连 /embeddings
    ├── HeadingAwareChunker.java          # 标题感知分段（唯一 v1 实现）
    ├── LocalKbStore.java                 # SQLite 实现（JPA + FTS5 + SqliteWriteGate）
    ├── NoopReranker.java                 # 降级直通实现
    ├── KbProperties.java                 # @ConfigurationProperties("oryxos.kb")
    ├── KbNotFoundException.java          # → 404
    ├── KbConflictException.java          # 重名/嵌入模型不兼容 → 409
    ├── KbTools.java                      # kb_search / kb_overview 工厂（模式同 MemoryTools）
    └── KbToolRegistrar.java              # @Component 注册进 ToolRegistry（同 MemoryToolRegistrar）

oryxos-storage/src/main/java/com/oryxos/storage/
├── KbEntity.java                         # kb_knowledge_bases
├── KbDocumentEntity.java                 # kb_documents
├── KbChunkEntity.java                    # kb_chunks
├── KbRepository.java / KbDocumentRepository.java / KbChunkRepository.java
└── KbFtsIndex.java                       # FTS5 虚拟表维护（JdbcTemplate，SqliteWriteGate 内）

oryxos-core/src/main/java/com/oryxos/core/
├── Profile.java                          # + List<String> knowledgeBases
└── agent/AgentLoader.java                # + knowledge_bases 解析（strList 模式）
└── workspace/WorkspaceInitializer.java   # SUBDIRS += "kb"

oryxos-cli/src/main/java/com/oryxos/cli/
└── KbCommand.java                        # kb create/list/show/delete/add/ingest/eval

oryxos-web/src/main/java/com/oryxos/web/
└── KbApiController.java                  # /api/v1/kbs/**（7 端点，见 contracts/rest-api.md）

oryxos-boot/
├── src/main/java/.../OryxOsApplication.java   # scanBasePackages += com.oryxos.kb
├── src/main/java/.../OryxOsLauncher.java      # "kb" 归入重命令（Spring 启动）
└── src/main/resources/application.yml         # oryxos.kb 段 + sandbox 白名单核对

pom.xml                                   # <module>oryxos-kb</module> + dependencyManagement
oryxos-boot/pom.xml / oryxos-cli/pom.xml / oryxos-web/pom.xml  # + oryxos-kb 依赖
```

**Structure Decision**: 新增 `oryxos-kb` 能力模块（结构镜像 `oryxos-memory`），
实体与仓库按既有惯例落在 `oryxos-storage`，CLI/Web 各加一个入口类，core 加
frontmatter 字段与工作区目录。不新增独立数据库、不引入向量库/搜索服务。

## Complexity Tracking

> 无违例需豁免。模块边界判断（oryxos-kb vs oryxos-tool）见 Constitution Check
> 表后说明，属既有先例（oryxos-memory）的复用而非新复杂度。
