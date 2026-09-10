# Contract: Agent 工具 — `kb_search` / `kb_overview`

**注册**: `KbTools` 工厂构造（模式同 `MemoryTools`）→ `KbToolRegistrar`
注册进 `ToolRegistry`；**Agent 须在 frontmatter `tools` 列出工具名才可见**
（同 memory 工具行为，Profile 最小权限）
**执行路径**: ReActLoop → ToolExecutor（含重试 + `tool_invocations` 审计）
**绑定过滤**: `kb` 参数解析规则 + 服务层强制校验（research D5），传入未绑定
库名 → 工具失败结果（对模型可见的报错字符串）

## kb_search

`description`: "在已绑定的知识库中检索相关内容片段。返回带来源引用
（知识库名、文档路径、标题路径、序号）的候选列表；零结果时明确返回未找到。

输入 schema（JSON Schema）:

```json
{
  "type": "object",
  "properties": {
    "query":  {"type": "string", "description": "检索问题或关键词"},
    "kb":     {"type": "string", "description": "知识库名；省略时若仅绑定一个库则使用该库"},
    "top_k":  {"type": "integer", "description": "返回条数，默认 5，上限 20"}
  },
  "required": ["query"]
}
```

**成功输出**（`ToolResult.success`，纯文本，模型可读）:

```text
[1] score=0.83 kb=product-docs docs/deploy.md # 部署指南 > Windows (chunk 3)
    <分段正文…>
[2] score=0.79 kb=product-docs docs/faq.md # 常见问题 (chunk 7)
    <分段正文…>
```

**分支输出**:
- 零结果: `未找到与 "<query>" 相关的内容（kb=product-docs）` →
  审计 `zero_result=true`（US2 场景 4：模型据此如实告知用户，不得编造引用）
- 降级（嵌入服务故障，已配置但不可用）: 结果仅来自关键词路，正文前缀
  `[降级：仅关键词检索]`；审计 `degraded=true`
- 未配置嵌入服务: 输出 `知识库检索不可用：未配置嵌入服务（oryxos.kb.embedding）`
- `kb` 省略且绑定多库: `请指定 kb 参数，可选值: product-docs, faq`
- `kb` 传入未绑定名或不存在名: `知识库不可用: <name>`
- `top_k` 越界: 钳制到 [1, 20]

## kb_overview

`description`: "列出知识库的文档清单与标题大纲，用于了解库里有什么、
选择检索方向。"

输入 schema:

```json
{
  "type": "object",
  "properties": {
    "kb": {"type": "string", "description": "知识库名；省略时若仅绑定一个库则使用该库"}
  },
  "required": []
}
```

**成功输出**:

```text
kb=product-docs 文档 2 篇:
- docs/deploy.md
  # 部署指南 > # 环境要求 > # Windows > # Linux
- docs/faq.md
  # 常见问题 > # 许可与授权
```

空库输出 `kb=<name> 暂无文档`（US5 场景 2）。`kb` 解析规则与错误分支同上。

## 审计契约

两工具每次调用经 ToolExecutor 落 `tool_invocations`；`kb_search` 的
`result_json` 结构见 data-model.md（`zero_result`/`degraded`/`top_scores`/
`duration_ms` 固定字段，SC-005 与评估回填依赖之）。
