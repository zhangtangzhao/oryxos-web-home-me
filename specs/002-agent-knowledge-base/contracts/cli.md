# Contract: CLI — `oryxos kb` 子命令组

**归属**: `oryxos-cli` 新增 `KbCommand`（`@Command(name="kb")` + 嵌套子命令，
模式同 `ProfileCommand`）
**命令档位**: **重命令**——`kb` 不进 `OryxOsLauncher.LIGHT_COMMANDS`，经
`PicocliSpringFactory` 注入 `KbService`（触及 JPA 与嵌入客户端）
**前置**: 需要 `oryxos.kb.embedding` 已配置（ingest/eval）；create/list/show/
delete/add 不触嵌入

## 子命令

```text
oryxos kb create <name> [--description <desc>]
    创建知识库。重名 → 非零退出 + "知识库已存在: <name>"
    输出: 已创建 <name>（路径 <root>/kb/<name>/）

oryxos kb list
    列出全部知识库：名称、文档数(ready/failed)、嵌入模型、更新时间
    输出: 表格；无库时输出"（无知识库）"

oryxos kb show <name>
    状态详情：逐文档 doc_path/status/chunk_count/error_message
    不存在 → 非零退出 + "知识库不存在: <name>"

oryxos kb add <name> <file...>
    把文件复制入 <root>/kb/<name>/docs/ 并记 pending
    仅 .md/.markdown/.txt；不存在的库 → 报错指引先 create

oryxos kb ingest <name>
    执行增量摄取（指纹比对，只处理变化文档）。输出处理摘要：
    处理 2 / 跳过 8 / 移除 0 / 失败 0，耗时 Xs
    嵌入模型身份不符 → 报错 + "请删除重建或恢复嵌入配置 (EMBEDDING_MISMATCH)"
    服务不可达 → 报错 (EMBEDDING_UNAVAILABLE)，文档保留 pending 可重试

oryxos kb delete <name>
    删除库及其全部索引数据与目录。需确认（--yes 跳过交互确认）

oryxos kb eval <name> [--top-k 5]
    运行 <root>/kb/<name>/evalset.yaml 评估集（纯检索，不调 LLM）
    报告: 样例数/命中数 → hit@k 百分比、zero_result 数、平均耗时
    evalset.yaml 格式:
      - query: 如何配置白名单？
        expected_path: docs/sandbox.md
    期望路径不在库中的样例标记 [无效] 不计分
```

## 退出码约定

`0` 成功；`1` 业务失败（库不存在/重名/嵌入不可用/评估未过 100% 命中时仅
警告不失败）；`2` 用法错误（Picocli 默认）。

## AGENT.md 模板增量

`profile create` 生成的模板 frontmatter 增注释示例：

```yaml
# knowledge_bases:          # 绑定知识库（配合 kb_search / kb_overview 工具）
#   - product-docs
```
