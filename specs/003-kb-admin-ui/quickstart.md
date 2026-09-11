# Quickstart: Web 管理台知识库页面端到端验证

**Feature**: specs/003-kb-admin-ui | **Date**: 2026-09-10
**契约依据**: [contracts/admin-rest-api.md](contracts/admin-rest-api.md) + [specs/002 rest-api.md](../002-agent-knowledge-base/contracts/rest-api.md)（页面动作 → 端点映射见其"US 覆盖表"）

## 前置

```bash
cd /d/test-project/oryxos
mvn -pl oryxos-boot -am package -DskipTests          # fat jar 含 static/admin/
python D:/tmp/mock_openai.py 18080 8 &               # mock OpenAI（embeddings+chat），日志 D:/tmp/mock-server.log
rm -rf /d/tmp/kbadmin-ws && mkdir -p /d/tmp/kbadmin-ws
```

服务端启动（**env 全部内联，勿依赖上条命令的 export**）：

```bash
cd /d/tmp/kbadmin-ws && ORYXOS_ROOT=/d/tmp/kbadmin-ws/.oryxos DEEPSEEK_API_KEY=dummy \
KB_EMBEDDING_BASE_URL=http://127.0.0.1:18080 KB_EMBEDDING_API_KEY_ENV=DEEPSEEK_API_KEY \
KB_EMBEDDING_MODEL=text-embedding-v3 KB_EMBEDDING_DIMENSIONS=8 \
SPRING_APPLICATION_JSON='{"oryxos":{"providers":[{"name":"deepseek","base-url":"http://127.0.0.1:18080","api-key-env":"DEEPSEEK_API_KEY"}]}}' \
java -jar D:/test-project/oryxos/oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve --port 18081
```

> git-bash 终端为 GBK：curl JSON body 只用 ASCII 内容；中文断言改查 HTTP 状态码或 `python -c` 解码响应文件。

## 静态页可达（先冒烟）

```bash
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" http://127.0.0.1:18081/admin
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" http://127.0.0.1:18081/admin/index.html
curl -s -o /dev/null -w "%{http_code} %{content_type}\n" http://127.0.0.1:18081/admin/app.js
```

预期：`302 …/admin/index.html`；两资源 200 且 content_type 正确。浏览器打开 `http://127.0.0.1:18081/admin/index.html` → 空态页含"创建知识库"引导（US1 验收 3）。

## US2 闭环（零命令行建库）

1. 浏览器：创建库 `manual`（描述任意）→ 列表出现、文档数 0（验收 1）
2. 详情 → 粘贴文本、命名 `faq.md` → 状态"待摄取"（验收 2）
3. 点"开始摄取" → 汇总 processed=1 + 文档"就绪"（验收 3）

curl 侧核对（等价于页面动作的直调一致性，SC-002）：

```bash
curl -s http://127.0.0.1:18081/api/v1/kbs | python -c "import sys,json;d=json.load(sys.stdin);print([k['name'] for k in d['data']])"
```

## US4 试检索

```bash
printf '{"query":"who keeps the port","top_k":5}' > /d/tmp/req.json
curl -s -X POST -H 'Content-Type: application/json' -d @/d/tmp/req.json http://127.0.0.1:18081/api/v1/kbs/manual/search
```

预期 200：`hits[0].doc_path=="faq.md"`、含 `score` 与 `content` 片段（验收 1）。无关查询 → 200 + `zero_result:true`（验收 2）。
浏览器：详情页试检索框输入同样查询 → 命中列表渲染。

```bash
sqlite3 /d/tmp/kbadmin-ws/.oryxos/oryxos.db "select session_id,tool_name,success from tool_invocations order by id desc limit 1"
```

预期：`admin-ui|kb_search|1`（审计落行，宪法 V）。

## US1 / US3 回归

- US1：再建 2 个库（其一摄取一篇会失败的坏文档，如停掉 mock 后 ingest）→ 列表三行、失败库异常标识、详情展示 error_message 与 overview 标题骨架
- US3：删除确认弹窗明示库名/文档数/不可恢复；取消 → 数据不变（SC-005）；确认 → 列表消失、直调详情 404 `KB_NOT_FOUND`

## 边界与失败分支

| 场景 | 操作 | 预期 |
|---|---|---|
| 重名建库 | 再建 `manual` | 页面点名冲突提示，409 `KB_CONFLICT` |
| 身份变更 | 换 `KB_EMBEDDING_MODEL` 重启服务端后试检索 | 409 `EMBEDDING_MISMATCH` + "嵌入模型已变更"提示 |
| 嵌入不可达 | 停 mock 后对 pending 库 ingest | 502 `EMBEDDING_UNAVAILABLE`，页面失败原因 + 可重试 |
| 嵌入未配置 | 去掉 KB_EMBEDDING_* 重启后试检索 | 503 `EMBEDDING_NOT_CONFIGURED` |
| 服务不可达 | 停服务端后刷新页面 | "服务不可达"+重试入口，非白屏 |

## 完成判据

quickstart 全部场景通过 + `mvn clean test` 全绿 + SC-001~006 对应勾稽（SC-003 规模项见 tasks 压测任务）。
