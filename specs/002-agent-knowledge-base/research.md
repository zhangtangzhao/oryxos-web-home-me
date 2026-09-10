# Research: 给 Agent 增加知识库

**Feature**: `002-agent-knowledge-base` | **Date**: 2026-09-09
**输入**: spec.md（含 5 条澄清）、代码库探查报告（2026-09-09）、2026-09 开源格局调研（本会话）

> 每条决策按 Decision / Rationale / Alternatives considered 结构记录。
> 涉及代码库事实的均经实际探查验证（文件与行号见引用）。

---

## D1. Spring AI 基线：维持 1.0.0-M5，不随本特性升级

**Decision**: 本特性**不升级** Spring AI。嵌入调用不新增 Spring AI 接触面，
而是在 `oryxos-kb` 内用 Spring `RestClient` 直连 OpenAI 兼容 `/embeddings`
端点（`OpenAiCompatEmbeddingClient`）。2.0.x 升级列为独立基建任务，不阻塞 KB。

**Rationale**: 探查证实 Spring AI 在全代码库只被一个文件触碰——
`oryxos-provider/.../DefaultProviderService.java:12`（裸 `OpenAiApi` 协议类型，
构造参数已含 `/v1/embeddings` 路径，见 61-66 行）。升级到 2.0.x 的收益
（GA 化 EmbeddingModel 抽象）对KB 无用——我们要的只是"POST /embeddings 拿
float[]"，几十行 RestClient 代码即可；而升级的破坏面（`OpenAiApi` 构造签名
在 M5 → GA → 2.0 间多次变动，如 26 参 `ChatCompletionRequest` 构造器
DefaultProviderService.java:119-123 需重写）落在**已上线的 chat 主链路**上。
为一个可选抽象去动稳定主链路，风险收益倒置。spring-ai-alibaba（pom 中 M5）
无任何代码 import，升级时可直接从依赖树移除。

**Alternatives considered**:
- *升级 Spring AI 2.0.1 并用其 EmbeddingModel*：GA 抽象 + 未来 rerank 生态；
  拒因：破坏面在 chat 主链路，且 spring-ai-alibaba 版本配套需同步验证，
  把基建风险捆绑进功能交付。
- *复用 DefaultProviderService 已构造的 OpenAiApi 实例发嵌入请求*：零新增
  客户端代码；拒因：该实例绑定 chat Provider 的 base-url/api-key（如
  DeepSeek——其**无嵌入 API**），而嵌入服务需要独立凭据与端点
  （spec 澄清 1 允许独立选型）。

## D2. 嵌入服务接入：独立 `EmbeddingClient` 端口 + OpenAI 兼容 HTTP 实现

**Decision**: 端口接口 `EmbeddingClient { List<float[]> embed(List<String> texts); int dimensions(); }`
定义在 `oryxos-kb`；唯一 v1 实现 `OpenAiCompatEmbeddingClient` 用 Spring
`RestClient` 同步调用 OpenAI 兼容 `/embeddings`（批量数组输入，批大小 16）。
配置 `oryxos.kb.embedding{base-url, api-key-env, model, dimensions}`，凭证
仅环境变量（宪法 VI），缺失/未配置时**点名报错**（spec 澄清 2）。默认配置
示例给 dashscope `text-embedding-v3`（1024 维）；文档明示 DeepSeek 不提供
嵌入端点，不可配为嵌入服务。

**Rationale**: OpenAI 兼容协议是行业事实标准（dashscope/qwen、openai、
本地 vLLM/ollama 网关全兼容），一个实现覆盖全部部署形态，符合宪法 IX
（一接口一档实现）。独立配置使嵌入供应商与 chat 供应商解耦（chat 用
DeepSeek + 嵌入用 dashscope 是常见组合）。同步 RestClient 符合宪法 I。
批量嵌入把 100 篇文档的 API 往返从千次级压到几十次（SC-001 预算的关键）。

**Alternatives considered**:
- *本地嵌入模型（ONNX/推理服务）*：数据绝对不出域；拒因（本期）：部署负担
  重（模型文件 + 运行时），spec 澄清 1 已裁决"与 chat 同姿态、本地留后续
  档次"，接口已为其留位（换一档 `EmbeddingClient` 实现即可）。
- *Spring AI EmbeddingModel（M5）*：见 D1。

## D3. 索引存储形态：SQLite JPA 三表 + FTS5 派生表 + 每 KB 向量缓存，暴力余弦

**Decision**:
- **元数据与分段**：`oryxos-storage` 新增 `kb_knowledge_bases` /
  `kb_documents` / `kb_chunks` 三张 JPA 表（ddl-auto=update 建新表有效，
  符合既有机制边界）。向量以 float[] → TEXT(JSON) 存 `kb_chunks.embedding`。
- **关键词路**：FTS5 虚拟表 `kb_chunks_fts(chunk_id, tokens)`——存**中文
  二元切分（bigram）+ ASCII 词元**后的空格连接文本，unicode61 分词器即可
  正确索引，BM25 排序内建。JPA 映射不了虚拟表，由 `KbFtsIndex` 用
  JdbcTemplate 在启动时 `CREATE VIRTUAL TABLE IF NOT EXISTS` 建立；
  FTS5 不可用时自动降级为 LIKE 匹配（v1 规模下可用性兜底）。
- **向量检索**：每 KB 懒加载向量缓存（chunk_id → float[]，摄取完成即失效
  重建），查询时暴力余弦取 top-N。所有写路径（分段落库 + FTS 同步）包在
  `SqliteWriteGate.write(...)` 内。
- **规模锚点**：万级分段 × 1024 维暴力余弦 ≈ 十毫秒级单线程计算，内存
  缓存 ≈ 数十 MB——与 spec"不设硬上限、按预算验收"（澄清 3）自洽；
  sqlite-vec 扩展留作后续档次（接口 `KbStore` 不暴露此细节）。

**Rationale**: 单节点私有部署 + 既有 SQLite WAL 拓扑（SqliteWriteGate/
wal_autocheckpoint=64 调优成果）下，外置向量库违背轻量部署承诺且无收益；
SQLite 是唯一与其自洽的选择。**中文分词选 bigram 而非 jieba**：零运行时
依赖（jieba-analysis 需引第三方词库组件），2 字滑窗对中文召回覆盖好、
对 ASCII 自动退化为整词，是 CJK 检索的经典无依赖做法；检索精度若不达
评估集门禁（Story 6），升 jieba 只是换 token 函数，索引可重建。

**Alternatives considered**:
- *sqlite-vec 虚拟表*：真向量索引；拒因：原生扩展在 Windows/Linux 的加载
  与 xerial JDBC 版本耦合是部署风险，v1 规模暴力计算已进预算。
- *jieba 分词 + FTS5*：词级精度更高；拒因（本期）：新增运行时依赖，
  bigram 先行、按评估数据决定是否升级。
- *纯 LIKE 无 FTS5*：最简；拒因：BM25 排序缺失使关键词路退化为过滤，
  FR-009"融合并合并排序"不成立（保留为 FTS5 不可用时的降级路径）。
- *外置向量库（Qdrant/Milvus）*：拒因：违背单节点私有部署宪法约束
  （数据不出域 + 轻量）。

## D4. 检索流水线：双路召回 → 加权 RRF → 重排槽（本期直通）→ 引用组装

**Decision**: `KbSearchService` 编排：① 查询嵌入（失败→降级标志，仅关键词
路，spec 澄清 2 区分"未配置=报错 / 已配置但故障=降级"）；② 语义路余弦
top-50 + 关键词路 FTS5 BM25 top-50（候选池可配）；③ 加权 RRF 合并：
`score = Σ w·1/(60+rank)`，`w_semantic=0.7 / w_keyword=0.3`（可配）；
④ `Reranker` 槽位——v1 仅 `NoopReranker` 直通（FR-010 的"未配置降级"），
未来接 rerank 服务只是换实现；⑤ 取 top_k（默认 5）组装引用：
`kb / doc_path / heading_path / chunk_ordinal / score`。双路皆空 →
`zero_result` 标志 + 明确"未找到"内容（US2 场景 4）。

**Rationale**: RRF 无需分数归一（余弦与 BM25 量纲不可通约），是混合检索
的稳健标准做法；权重默认偏向语义路，因 bigram 关键词路对精确词（型号、
错误码）更准但噪声也高，比例交由评估集调优。引用粒度到"标题路径 + 分段
序号"（FR-006 的"文档内位置"），模型可据此引导用户核对原文。

**Alternatives considered**:
- *分数归一线性加权*：需要按分布校准两路分数，脆弱；拒。
- *重排服务本期接入（BGE-reranker 系）*：调研确认其为当前开源优选，但
  FR-010 裁决本期留槽不配置——接入点已固定（④），属任务级增量。

## D5. 检索工具契约：`kb_search` / `kb_overview`，绑定过滤在服务层强制

**Decision**: 两个工具（schema 见 contracts/agent-tools.md）：
- `kb_search{query*, kb?, top_k?}`——`kb` 省略且 Agent 恰绑定一个库时自动
  使用之；绑定多库而省略 `kb` 时返回"请指定 kb"并列出绑定名；传入未绑定
  的 `kb` 名被拒绝（越权防护在 `KbSearchService` 强制，不依赖模型自觉）。
- `kb_overview{kb?}`——同 `kb` 解析规则，返回文档清单 + 标题大纲。
工具实例由 `KbTools` 工厂构造（模式同 `MemoryTools`）、`KbToolRegistrar`
注册进 `ToolRegistry`（同 `MemoryToolRegistrar`）；Profile 的
`knowledge_bases` 绑定经 `listForAgent` 天然生效——工具声明纳入 Agent
`tools` 列表才可见（`kb_search`、`kb_overview` 二名）。

**Rationale**: 工具无会话上下文（`execute(JsonNode)` 无 profile 参数——
探查事实），把"绑定"做成输入解析规则 + 服务层校验，避免引入 ThreadLocal
隐式状态；模型显式传 `kb` 也让多库 Agent 的检索路径在审计中自解释。
要求 Agent 在 `tools` 中显式列出 KB 工具，与 memory 工具行为一致
（非默认注入），保持 Profile 最小权限。

**Alternatives considered**:
- *ThreadLocal 携带当前绑定集*：隐式状态、与同步模型下的线程复用纠缠；拒。
- *PromptBuilder 注入绑定库名进 system prompt*：作为引导可做（任务阶段
  可选增强），但不作为解析机制依赖模型自觉。

## D6. 检索审计：复用 `tool_invocations`，不新增第六表

**Decision**: FR-007 的审计由既有链路自动满足——`ToolExecutor.java:65`
对每次工具调用落 `tool_invocations`；`kb_search` 的 `result_json` 以固定
结构携带 `kb`、`results_count`、`zero_result`、`degraded`、
`top_scores[]`、`duration_ms`。SC-005 的统计用 `json_extract` 查询；
评估集回填（Story 6）读同源数据。

**Rationale**: 宪法 V 的两张审计表 + ToolExecutor 自动化是既定地基；
检索本质是工具调用，另立 `kb_retrievals` 表会制造双写与口径分叉。
结构化 JSON 在 SQLite 上可查询（`json_extract`），零迁移成本。

**Alternatives considered**:
- *专用 `kb_retrievals` 表*：查询更整洁；拒因：破坏"审计一张表"的既有
  口径，双写一致性无收益。

## D7. 模块 placement：新增 `oryxos-kb`（镜像 `oryxos-memory`），实体归 `oryxos-storage`

**Decision**: `oryxos-kb` 承载端口接口、服务编排、工具工厂/注册器、配置；
JPA 实体与仓库按惯例落 `oryxos-storage`（`@EntityScan`/`@EnableJpaRepositories`
的 basePackages 已指向 `com.oryxos.storage`，探查事实——实体放新模块反而
要动扫描配置）。`OryxOsApplication.scanBasePackages += "com.oryxos.kb"`；
boot/cli/web 三 pom 加依赖；`OryxOsLauncher.LIGHT_COMMANDS` **不**收录
`kb`（管理命令走 Spring 重路径，因其触及 JPA 与嵌入客户端）。

**Rationale**: KB 与 Memory 同构（数据资产 + 门面服务 + 能力工具），
`oryxos-memory` 的 `MemoryTools`/`MemoryToolRegistrar`/`MemoryModuleConfiguration`
是该形态的既成先例；塞进 `oryxos-tool` 会把摄取/分段/嵌入/索引机器并入工具
模块，违背内聚。宪法"Tool 模块拆太细"反模式指 Tool 家族内再拆分，此处是
能力模块分域，非违例（plan.md 宪法检查表已记录）。

**Alternatives considered**:
- *全部进 `oryxos-tool`*：模块内聚崩坏；拒。
- *实体放 `oryxos-kb` 内*：需扩 `@EntityScan` basePackages，偏离既有
  "持久化层集中"惯例；拒。

## D8. 摄取流程：指纹增量 + 标题感知分段 + 批量嵌入，仅手动触发

**Decision**: `kb add` 把文件**复制**进 `<root>/kb/<name>/docs/`（原文
落入工作区，FR-012）并记 `pending`；`kb ingest` 逐文档：sha256 指纹与
现存一致 → 跳过（FR-008）；不一致/新建 → `HeadingAwareChunker` 分段
（ATX 标题切分并保留标题路径，段落打包目标 ~500 字符、重叠 ~50；纯文本
退化为段落打包）→ 批量嵌入（16/批）→ 事务内替换该文档分段 + FTS 行
（`SqliteWriteGate`）→ `ready`；文件已从 docs/ 消失 → 删除其分段与索引。
KB 元数据记录嵌入 `model + dimensions`，摄取时校验与配置一致，不一致
拒绝并提示重建（FR-015，`KbConflictException` → 409）。触发仅 CLI/REST
手动（澄清 4）；无自动文件监视。

**Rationale**: 复制进工作区使原文/索引/审计同域自洽（备份即拷
`.oryxos/`），外部路径引用会引入工作区外的生命周期耦合。指纹用 JDK 内建
`MessageDigest`，零依赖。标题路径作为引用位置与分段上下文（对齐 2026
"Knowledge Compilation"趋势中最廉价的一份编译产物：结构大纲即
`kb_overview` 的数据源，不额外生成摘要文本）。

**Alternatives considered**:
- *引用外部路径不复制*：拒因见上（FR-012 语义弱化）。
- *LLM 生成上下文前缀（Contextual Retrieval）/摘要树*：质量更优但每段
  一次 LLM 调用，摄取成本×50；拒（本期），`KbChunker` 接口留升级位。
- *WatchService 自动摄取*：spec 澄清 4 明确出范围。

## D9. 配置与默认值

**Decision**: `@ConfigurationProperties(prefix="oryxos.kb")`（模式同
`ProviderProperties`）：`embedding{base-url, api-key-env, model, dimensions}`
、`search{top-k=5, candidate-pool=50, rrf-k=60, semantic-weight=0.7,
keyword-weight=0.3}`、`chunk{target-chars=500, overlap-chars=50}`。
`application.yml` 增注释段（嵌入段默认留空 base-url，缺失即点名报错）；
sandbox 白名单核对：`<root>` 已在 `allowed-paths` 默认值内（探查证实），
无需扩权。AGENT.md 模板（`profile create`）补 `knowledge_bases: []` 示例
注释。

**Rationale**: 全部沿用既有配置模式（无新机制）；默认值即 spec 预算的
落地点，评估集调优只动数字不动代码。

## D10. 评估机制：检索级评估先行，无 LLM 参与

**Decision**: 评估集 = `<root>/kb/<name>/evalset.yaml`（`[{query,
expected_path}]` 列表）；`oryxos kb eval <name> [--top-k]` 对每条执行真实
检索（含嵌入），报告 hit@k、zero-result 数、平均耗时；`expected_path`
不存在于库的样例标记无效不计分（US6 场景 2）。SC-002 的"回答引用正确率"
属端到端验收（LLM 参与），由 quickstart 场景人工/半自动执行，不进
`kb eval`（后者纯检索层，可在无 LLM key 的环境跑）。

**Rationale**: 检索级评估是 Story 6 的可交付核心——它是后续任何检索组件
更换（rerank、jieba、嵌入模型）的验收门禁；端到端引用正确率依赖模型行为，
做成可重复的自动化门禁成本高且脆弱，quickstart 场景 + 审计数据核对足够。

**Alternatives considered**:
- *LLM-as-judge 自动评引用正确率*：纳入门禁成本高；拒（本期），审计数据
  已为将来接入留了原料（score/zero_result/degraded 全在 `tool_invocations`）。
