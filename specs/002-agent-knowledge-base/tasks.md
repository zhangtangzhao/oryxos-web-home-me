---
description: "Task list for feature 002-agent-knowledge-base"
---

# Tasks: 给 Agent 增加知识库

**Input**: Design documents from `/specs/002-agent-knowledge-base/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: 已包含（仓库既有 53 测试同栈 JUnit 5；plan.md Technical Context 明确测试基线）

**Organization**: 按用户故事分组；每故事可独立实现与验证（quickstart.md 场景一一对应）

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: 所属用户故事（US1–US6）
- 所有 Java 路径省略 `src/main/java/` 前缀写法见各项；根目录为仓库根

## Path Conventions

- 新模块 `oryxos-kb/src/main/java/com/oryxos/kb/`（下文简写 `kb:`）
- 存储 `oryxos-storage/src/main/java/com/oryxos/storage/`（简写 `storage:`）
- 核心 `oryxos-core/src/main/java/com/oryxos/core/`（简写 `core:`）
- CLI `oryxos-cli/src/main/java/com/oryxos/cli/`；Web `oryxos-web/src/main/java/com/oryxos/web/`
- 测试在各模块 `src/test/java/` 同包路径

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 模块骨架与配置接线

- [X] T001 创建 oryxos-kb 模块骨架：`oryxos-kb/pom.xml`（依赖 oryxos-core、oryxos-tool、oryxos-storage、spring-boot-starter，镜像 oryxos-memory/pom.xml）；根 `pom.xml` 增 `<module>oryxos-kb</module>`；`oryxos-boot/pom.xml`、`oryxos-cli/pom.xml`、`oryxos-web/pom.xml` 各增 oryxos-kb 依赖；`mvn -pl oryxos-boot -am package -DskipTests` 通过
- [X] T002 [P] 工作区与扫描接线：`core:workspace/WorkspaceInitializer.java` SUBDIRS 增 `"kb"`；`oryxos-boot/.../OryxOsApplication.java` scanBasePackages 增 `"com.oryxos.kb"`
- [X] T003 [P] 配置段与属性类：`oryxos-boot/src/main/resources/application.yml` 增 `oryxos.kb` 段（embedding{base-url 留空, api-key-env, model, dimensions} / search{top-k=5, candidate-pool=50, rrf-k=60, semantic-weight=0.7, keyword-weight=0.3} / chunk{target-chars=500, overlap-chars=50}，注释按 research.md D9）+ `kb:KbProperties.java` `@ConfigurationProperties(prefix="oryxos.kb")`（含默认值，未配置嵌入不得在启动时崩溃——点名报错发生在使用时）

**Checkpoint**: `mvn -pl oryxos-boot -am package -DskipTests` 全绿，空模块被启动扫描

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 存储层与端口层——所有用户 story 的公共地基

**?? CRITICAL**: 任何用户 story 开工前必须完成本阶段

- [X] T004 存储实体与仓库（data-model.md 三表）：`storage:KbEntity.java`（kb_knowledge_bases：name PK, description, embedding_model, embedding_dimensions, created_at, updated_at）、`storage:KbDocumentEntity.java`（kb_documents：id PK, kb_name, doc_path, content_hash, size_bytes, status, error_message, chunk_count, ingested_at）、`storage:KbChunkEntity.java`（kb_chunks：id PK, document_id, kb_name, chunk_ordinal, heading_path, content TEXT, embedding TEXT）+ `storage:KbRepository.java`、`storage:KbDocumentRepository.java`、`storage:KbChunkRepository.java`（JpaRepository + 派生查找器；TEXT 列 columnDefinition 同既有实体惯例）
- [X] T005 FTS5 派生索引与中文二元切分：`storage:BigramTokenizer.java`（CJK 2 字滑窗 + ASCII 整词小写，空格连接；纯静态工具）+ `storage:KbFtsIndex.java`（JdbcTemplate：启动 `CREATE VIRTUAL TABLE IF NOT EXISTS kb_chunks_fts USING fts5(chunk_id UNINDEXED, tokens)`；replaceByDocument/deleteByDocument/search BM25 top-N/rebuild；全部写操作在 `SqliteWriteGate.write` 内；FTS5 不可用时降级 LIKE 匹配并在结果对象标注）
- [X] T006 KB 存储端口与实现：`kb:KbStore.java` 端口接口（create/list/find/delete 级联、document upsert/findByPath/findChanged、chunk replaceAllForDocument、embeddingIdentity 读写、overview 大纲聚合）+ `kb:LocalKbStore.java`（组合 KbRepository/KbDocumentRepository/KbChunkRepository/KbFtsIndex；全部写包 `SqliteWriteGate`）+ `kb:KbNotFoundException.java`、`kb:KbConflictException.java`（携带 code：KB_CONFLICT / EMBEDDING_MISMATCH）
- [X] T007 嵌入与重排端口：`kb:EmbeddingClient.java`（`List<float[]> embed(List<String>)` + `int dimensions()`）+ `kb:OpenAiCompatEmbeddingClient.java`（Spring RestClient 同步 POST `{base-url}/embeddings`，批 16，model/dimensions 取 KbProperties；**未配置 base-url 或 api-key-env 对应环境变量缺失 → EmbeddingNotConfiguredException 点名缺失配置项**；调用失败包 EmbeddingUnavailableException）+ `kb:Reranker.java` 端口 + `kb:NoopReranker.java` 直通实现（research D2/D4）
- [X] T008 [P] 地基单测：`oryxos-storage/src/test/java/.../BigramTokenizerTest.java`（中英混排切分）、`KbFtsIndexTest.java`（临时库 roundtrip：写入→BM25 命中→删除失效→rebuild）、`oryxos-kb/src/test/java/.../LocalKbStoreTest.java`（CRUD + 级联删除 + 身份记录）

**Checkpoint**: 建表 + FTS + 端口层可用，`mvn -pl oryxos-boot -am test` 全绿

---

## Phase 3: User Story 1 — 管理员创建知识库并摄取文档 (Priority: P1) ?? MVP

**Goal**: 管理员可创建具名知识库、添加 md/txt 文档、手动摄取为可检索形态、查看状态；重名/失败路径明确

**Independent Test**: quickstart.md 场景 1（init→create→add→ingest→show→重名拒绝），无需 Agent/LLM 参与

### Tests for User Story 1

- [X] T009 [P] [US1] `kb:src/test/.../HeadingAwareChunkerTest.java`：ATX 标题路径提取（`部署指南 > Windows`）、500 字符打包/50 重叠、纯文本段落打包退化、空文档
- [X] T010 [P] [US1] `kb:src/test/.../KbIngestServiceTest.java`（假 EmbeddingClient 返回固定向量）：同指纹跳过、变更重嵌、模型身份不符抛 KbConflictException(EMBEDDING_MISMATCH)、未配置抛 EmbeddingNotConfiguredException、摘要计数

### Implementation for User Story 1

- [X] T011 [US1] `kb:KbChunker.java` 接口 + `kb:HeadingAwareChunker.java` 实现（ATX 标题切分保留标题路径；段落打包目标 chunk.target-chars、重叠 overlap-chars；纯文本退化；使 T009 通过）
- [X] T012 [US1] `kb:KbIngestService.java`：逐文档 sha256 指纹比对（JDK MessageDigest）→ 变更/新建走 分段→批量嵌入→`SqliteWriteGate` 事务内替换 kb_chunks + FTS 行→status=ready；docs/ 消失的文档删除分段与索引；嵌入身份与 KbEntity 记录不符 → 拒绝；摘要 {processed, skipped, removed, failed, duration_ms}（使 T010 通过）
- [X] T013 [US1] `kb:DefaultKbService.java` 门面（create 校验 `[a-z0-9][a-z0-9_-]{0,63}` + 重名拒绝 + 建 `<root>/kb/<name>/docs/`；list；show；delete 级联清 FTS/表行/目录；addDocument 文件复制入 docs/ 或直传内容，仅 .md/.markdown/.txt，记 pending）+ `kb:KbModuleConfiguration.java`（@Configuration：装配 properties/store/chunker/ingest/search/tools/registrar，模式同 MemoryModuleConfiguration）
- [X] T014 [US1] `oryxos-cli:KbCommand.java`：`@Command(name="kb")` 组 + create/list/show/add/ingest/delete 嵌套子命令（contracts/cli.md 契约与退出码；--yes 跳过 delete 确认；经 PicocliSpringFactory 注入——核对 `oryxos-boot/.../OryxOsLauncher.java` 的 LIGHT_COMMANDS **不含** "kb"）
- [X] T015 [US1] `oryxos-web:KbApiController.java`：POST /api/v1/kbs、GET /api/v1/kbs、GET /{name}、POST /{name}/documents、POST /{name}/ingest、DELETE /{name}（contracts/rest-api.md 响应形状，ApiResponse 包裹）+ `oryxos-web:GlobalExceptionHandler.java` 增映射：KbNotFoundException→404 KB_NOT_FOUND、KbConflictException→409 KB_CONFLICT/EMBEDDING_MISMATCH、EmbeddingUnavailableException→502、EmbeddingNotConfiguredException→503 EMBEDDING_NOT_CONFIGURED（message 点名缺失配置项）
- [X] T016 [US1] E2E 验证 quickstart.md 场景 1 全流程 + `mvn -pl oryxos-boot -am test` 全绿 + 轻命令（status/profile list）亚秒回归

**Checkpoint**: US1 独立可用——管理员能建库、摄取、看状态、REST/CLI 双通

---

## Phase 4: User Story 2 — 绑定知识库的 Agent 检索并给出带引用回答 (Priority: P1)

**Goal**: Agent frontmatter 绑定知识库；chat 中 `kb_search` 检索返回带引用候选；零结果/越权/多库分支明确

**Independent Test**: quickstart.md 场景 2（绑定 chat 问答含事实+引用；审计行核对；未绑定负例不触发）

### Tests for User Story 2

- [X] T017 [P] [US2] `kb:src/test/.../KbSearchServiceTest.java`（假 EmbeddingClient + 假 KbStore 固定向量/得分）：加权 RRF 合并序（k=60, w=0.7/0.3）、top_k 截取、绑定过滤（未绑定名拒绝）、多库省略 kb 提示、单库省略自动、零结果标志、嵌入失败降级标志、审计 JSON 固定键（results_count/zero_result/degraded/top_scores/duration_ms）
- [X] T018 [P] [US2] `oryxos-core` frontmatter 扩展：`core:Profile.java` 增 `List<String> knowledgeBases`（默认空）+ `core:agent/AgentLoader.java` parse 增 `knowledge_bases` 解析（strList 模式，同 tools 字段）

### Implementation for User Story 2

- [X] T019 [US2] `kb:KbSearchService.java`：查询嵌入（失败→degraded）→ 语义路余弦 top-candidate-pool（每 KB 向量懒加载缓存，摄取后失效）→ 关键词路 FTS BM25 top-candidate-pool → 加权 RRF → NoopReranker 槽位 → top_k → 引用文本组装（`[i] score=… kb=… doc_path # heading_path (chunk n)` + 分段正文，正文截断同 HttpGetTool 8000 字符惯例）→ 各分支输出与审计 JSON（使 T017 通过；分支契约 = contracts/agent-tools.md）
- [X] T020 [US2] `kb:KbTools.java`（kb_search：schema/input 解析/分支文本，模式同 MemoryTools）+ `kb:KbToolRegistrar.java`（@Component 注册进 ToolRegistry；**工具不经 Profile 默认可见——Agent 须在 tools 列表显式列出**）+ `oryxos-cli:ProfileCommand.java` create 模板 frontmatter 增 knowledge_bases 注释示例
- [X] T021 [US2] E2E 验证 quickstart.md 场景 2：绑定 Agent chat 问答（事实+引用）、审计 `tool_invocations` 新行含 result_json 字段、未绑定负例（审计无 kb_search 行）、库外提问 zero_result=true 且回答不编造

**Checkpoint**: US1+US2 = MVP 完整价值链（资料进得去、Agent 检索得出、引用可核对）

---

## Phase 5: User Story 3 — 文档变更后增量摄取 (Priority: P2)

**Goal**: 只处理变化文档；删除文档退出检索范围；失败文档可重试

**Independent Test**: quickstart.md 场景 3（5 篇改 1 篇 → 处理 1 跳过 4；删除 → 移除）

- [X] T022 [US3] 补全增量语义并扩展测试：`kb:KbIngestService.java` 摘要 removed 计数与失败文档重试入口（failed→pending）核对补全 + `kb:src/test/.../KbIngestServiceTest.java` 增 removal 与 retry 路径用例（假客户端，断言旧行清理含 FTS）
- [X] T023 [US3] E2E 验证 quickstart.md 场景 3（追加 1 行→ingest 摘要 处理 1/跳过 N；删除文档→移除且检索不再返回其内容）

**Checkpoint**: 增量摄取可验证，运维成本语义成立

---

## Phase 6: User Story 4 — 检索可观测与降级 (Priority: P2)

**Goal**: 审计数据支撑零结果率/降级率统计（SC-005）；嵌入服务故障时关键词路降级可用

**Independent Test**: quickstart.md 场景 4（不可达 base-url → 降级回答 + 审计 degraded≥1 + 统计 SQL 出数）

- [X] T024 [P] [US4] `kb:src/test/.../KbSearchServiceTest.java` 增降级专项用例：假 EmbeddingClient 抛不可用 → 仅关键词路结果 + 正文前缀 `[降级：仅关键词检索]` + degraded=true；未配置 → 不可用输出分支；断言 zero_result 与 degraded 互斥语义
- [X] T025 [US4] E2E 验证 quickstart.md 场景 4：篡改 base-url 重启 → chat 降级回答；恢复后按场景 SQL 统计零结果率/降级率（SC-005 出数）

**Checkpoint**: 弱依赖可用性 + 观测数据闭环（评估集与后续调优的地基）

---

## Phase 7: User Story 5 — 知识库结构总览入口 (Priority: P3)

**Goal**: `kb_overview` 工具与 REST 总览返回文档清单 + 标题大纲；空库/多库分支明确

**Independent Test**: quickstart.md 场景 5（双库绑定提问"库里有什么"→ overview 调用 + 清单）

- [X] T026 [US5] `kb:KbStore.java`/`LocalKbStore.java` 增 overview 聚合（按文档聚合 heading_path 去重保序）+ `kb:KbTools.java` 增 kb_overview 工具（schema/分支：空库 `暂无文档`、kb 解析规则同 kb_search）+ `oryxos-web:KbApiController.java` 增 GET /{name}/overview（contracts/rest-api.md）
- [X] T027 [US5] E2E 验证 quickstart.md 场景 5（kbagent 双库绑定 → overview 清单 + 引用区分来源库；空库分支）

**Checkpoint**: 浏览类/盘点类问题有专属入口（Knowledge Compilation 对齐）

---

## Phase 8: User Story 6 — 检索质量评估集 (Priority: P3)

**Goal**: evalset.yaml 定义"问题→期望文档"对；`kb eval` 输出 hit@k 报告；无效样例不计分

**Independent Test**: quickstart.md 场景 6（10 条评估集 → hit@5 报告）

- [X] T028 [US6] `kb:KbEvalService.java`（读 `<root>/kb/<name>/evalset.yaml` SnakeYAML `[ {query, expected_path} ]`；逐条真实检索（含嵌入）；hit@k / zero_result 数 / 平均耗时 / expected_path 不在库标记 [无效] 不计分）+ `oryxos-cli:KbCommand.java` 增 eval 子命令（contracts/cli.md 输出与退出码）+ `kb:src/test/.../KbEvalServiceTest.java`（假客户端固定向量命中判定 + 无效样例）
- [X] T029 [US6] E2E 验证 quickstart.md 场景 6（编写 10 条评估集跑报告，核对命中率数字与 [无效] 标记）

**Checkpoint**: 检索组件更换有了验收门禁（research D10）

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 跨 story 收尾、文档同步、全量回归

- [X] T030 [P] 文档同步：`docs/CliGuide.md` 增 `oryxos kb` 命令节；`CLAUDE.md` 模块表增 oryxos-kb 行 + 工作区结构增 `kb/`；`docs/oryxos.md` 能力清单增知识库（宪法 Governance：下位文档随能力同步）
- [X] T031 E2E 验证 quickstart.md 场景 7：变更嵌入 model 配置 → 检索返回模型变更明确报错、ingest 返回 EMBEDDING_MISMATCH（FR-015 / SC-007）
- [X] T032 E2E 验证 quickstart.md 场景 8 + 9：serve 下 10 个绑定不同库的 Agent 并发 invoke 引用不串库（SC-006）+ 同时 kb ingest 无锁错误（SqliteWriteGate 排队）；REST 冒烟（创建/总览/删除）
- [X] T033 全量收尾：`mvn clean test` 全绿；三验收 Demo 回归（docs/TechnicalSolution.md §12：光杆/科技日报/GitHub 日报）；quickstart"退出前回归"三项核对；对照 SC-001~007 逐条勾稽并在本文件标记完成

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → **US1 (Phase 3)** → **US2 (Phase 4)** → **US3–US6 (Phase 5–8，可并行)** → **Polish (Phase 9)**
- US3/US4 依赖 US2 的检索服务；US5 依赖 US2 的工具注册模式；US6 依赖 US2 的检索与 US1 的库数据——**US2 是 US3–US6 的硬前置**
- US1 完成即可独立演示（MVP 验收点）

### User Story Dependencies

- **US1**: 仅依赖 Foundational
- **US2**: 依赖 Foundational + US1（摄取产生的索引是检索的数据来源）；Profile/AgentLoader 扩展（T018）可与 US2 测试并行
- **US3/US4**: 依赖 US2（KbSearchService 在手后才能测降级与增量后的检索表现）
- **US5/US6**: 依赖 US2；US5/US6 之间无依赖可并行

### Parallel Opportunities

- Phase 1: T002/T003 与 T001 后段并行（不同文件）
- Phase 2: T004→T005→T006 串行（同库依赖链），T007 与 T004–T006 并行（纯 kb 模块文件），T008 在 T005/T006 后
- US1: T009/T010 测试先行（与实现分文件可并行编写）；T014（CLI）与 T015（Web）并行
- US2: T017/T018 并行（kb 模块 vs core 模块）
- Phase 5–8: US3/US4/US5/US6 在 US2 后可四路并行（互不重叠文件，除 KbSearchServiceTest 由 US3/US4 共享扩展——合并执行时串行）

---

## Parallel Example: User Story 2

```bash
# 测试与 core 扩展先行（互不依赖）：
Task T017: "KbSearchServiceTest（假实现驱动画法）in kb:src/test/.../KbSearchServiceTest.java"
Task T018: "Profile/AgentLoader 增 knowledge_bases in core:Profile.java + agent/AgentLoader.java"
# 实现随后串行：T019 (KbSearchService) → T020 (KbTools/Registrar/模板)
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Phase 1 Setup → Phase 2 Foundational（地基）
2. Phase 3 US1：管理员侧闭环，**STOP 验证** quickstart 场景 1
3. Phase 4 US2：Agent 侧闭环，**STOP 验证** quickstart 场景 2
4. 此时 SC-001/002/003 可首次度量——核心价值已交付

### Incremental Delivery

- +US3（增量摄取）→ +US4（观测与降级）→ +US5（总览）→ +US6（评估）
- 每故事独立验证（对应 quickstart 场景），不打断已交付能力

### Notes

- 测试任务先写先跑（TDD 顺序），实现任务以"使其通过"收口
- 每任务或逻辑组完成后提交；检查点处按 quickstart 场景独立验证
- 宪法红线自查随任务走：写路径 SqliteWriteGate（T005/T006/T012）、工具走 ToolExecutor 审计（T020）、凭证 env-only（T003/T007）、无虚拟线程/响应式（全程）
