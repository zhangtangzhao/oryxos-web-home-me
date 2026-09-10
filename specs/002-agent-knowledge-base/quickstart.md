# Quickstart: 给 Agent 增加知识库

**Feature**: `002-agent-knowledge-base` | 端到端验证场景（对照 spec 验收）
**前置阅读**: [contracts/](contracts/)（CLI/REST/工具契约）、[data-model.md](data-model.md)

## 前置条件

```bash
mvn -pl oryxos-boot -am package -DskipTests          # 构建
export DASHSCOPE_API_KEY=sk-xxx                       # 嵌入服务凭证（宪法 VI）
# application.yml 或 config/application.yml 配置:
# oryxos.kb.embedding:
#   base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
#   api-key-env: DASHSCOPE_API_KEY
#   model: text-embedding-v3
#   dimensions: 1024
```

准备样例文档：3 篇含多级标题的 Markdown（其中 1 篇含可提问的明确事实，
如"OryxOS 默认端口是 8080"）。

## 场景 1 — 管理员创建并摄取（US1 / SC-001）

```bash
rm -rf /tmp/kbws && ORYXOS_ROOT=/tmp/kbws/.oryxos java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init
java -jar ... kb create product-docs --description 产品文档
java -jar ... kb add product-docs doc1.md doc2.md doc3.md
java -jar ... kb show product-docs          # 3 篇 pending
java -jar ... kb ingest product-docs        # 处理 3 / 跳过 0 / 失败 0
java -jar ... kb show product-docs          # 3 篇 ready，chunk_count 可见
java -jar ... kb create product-docs        # 预期失败：知识库已存在
```

**通过标准**: 全部就绪且失败 0；重名创建被拒（FR-001/003，SC-001）。

## 场景 2 — Agent 绑定检索 + 引用（US2 / SC-002）

```bash
java -jar ... profile create kbagent
# 编辑 /tmp/kbws/.oryxos/agents/kbagent/AGENT.md frontmatter:
#   tools: [kb_search]  knowledge_bases: [product-docs]
java -jar ... chat --profile kbagent        # 提问样例事实："OryxOS 默认端口是多少？"
```

**通过标准**: 回答包含正确事实 **且** 引用含 `kb=product-docs` + 文档路径 +
标题路径/序号（FR-004/005/006）。对照审计：

```bash
sqlite3 /tmp/kbws/.oryxos/oryxos.db \
  "select json_extract(result_json,'$.results_count'), json_extract(result_json,'$.degraded') \
   from tool_invocations where tool_name='kb_search' order by created_at desc limit 1;"
```

负例：未绑定 KB 的 Agent 提问同样问题 → 不触发 `kb_search`（审计无新行）；
提问库外问题 → 回答如实说明未找到，无编造引用（`zero_result=true`）。

## 场景 3 — 增量摄取（US3 / SC-004）

```bash
echo "补充说明：支持 WAL 模式。" >> doc1.md
java -jar ... kb ingest product-docs        # 预期: 处理 1 / 跳过 2 / 失败 0
rm doc2.md  # （若 add 支持目录同步则删除 docs/ 下副本；否则跳过删除验证）
java -jar ... kb ingest product-docs        # 预期: 移除 1，检索不再返回 doc2 内容
```

**通过标准**: 仅变化文档被处理（FR-008，SC-004 = 处理文档数恰为 1）。

## 场景 4 — 降级与审计（US4 / SC-005）

```bash
# 1) 篡改 base-url 指向不可达地址后重启，chat 中检索:
#    预期回答带 [降级：仅关键词检索]，结果来自关键词路
sqlite3 ... "select count(*) from tool_invocations where tool_name='kb_search'
  and json_extract(result_json,'$.degraded')=1;"      # ≥ 1
# 2) 恢复配置；统计零结果率与降级率（SC-005）:
sqlite3 ... "select avg(json_extract(result_json,'$.zero_result')),
       avg(json_extract(result_json,'$.degraded'))
       from tool_invocations where tool_name='kb_search';"
```

## 场景 5 — 总览与多库（US5）

```bash
java -jar ... kb create faq && java -jar ... kb add faq faq.md && java -jar ... kb ingest faq
# kbagent 的 knowledge_bases 追加 faq 后 chat: "知识库里有哪些资料？"
```

**通过标准**: 模型调用 `kb_overview` 并列出两库文档清单；引用能区分来源库。

## 场景 6 — 评估集（US6）

```bash
# /tmp/kbws/.oryxos/kb/product-docs/evalset.yaml:
#   - {query: "默认端口是多少？", expected_path: docs/doc1.md}
#   ...共 10 条
java -jar ... kb eval product-docs --top-k 5   # 报告 hit@5、zero_result、平均耗时
```

**通过标准**: 报告给出每条命中情况与整体命中率；`expected_path` 指向不存在
文档的样例标记 `[无效]` 不计分。

## 场景 7 — 嵌入模型身份防护（FR-015 / SC-007）

```bash
# 修改配置 model: text-embedding-v4（换模型）后 chat 检索:
#   预期: 知识库检索不可用：嵌入模型已变更（text-embedding-v3 → text-embedding-v4），请恢复配置或重建知识库
java -jar ... kb ingest product-docs        # 预期: EMBEDDING_MISMATCH，拒绝静默重嵌
```

## 场景 8 — 并发与多 Agent（SC-006）

serve 启动后 10 个绑定不同库的 Agent 经 `POST /api/v1/agents/{name}/invoke`
并发提问，**通过标准**: 全部成功且回答引用均来自各自绑定库（审计核对），
同时执行 `kb ingest` 不产生锁错误（SqliteWriteGate 排队）。

## 场景 9 — REST 管理面（FR-014）

```bash
java -jar ... serve --port 8080 &
curl -s localhost:8080/api/v1/kbs | head
curl -s -X POST localhost:8080/api/v1/kbs -d '{"name":"rest-kb"}' -H 'Content-Type: application/json'
curl -s localhost:8080/api/v1/kbs/rest-kb/overview
curl -s -X DELETE localhost:8080/api/v1/kbs/rest-kb
```

## 退出前回归

- `oryxos status` / `profile list` 等轻命令仍亚秒返回（未误起 Spring）
- 既有 53 测试全绿 + 本特性新增测试
- 三个验收 Demo（docs/TechnicalSolution.md §12）跑通——确认无回归
