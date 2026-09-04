# Demo 一：每日天气（SC-008 发布硬条件）

光杆 Agent（无 Skill / 无 MCP）：`schedules` 定时 + `http_get` 天气域名白名单 + `notify` Webhook 推送。

## 装配（对应 quickstart 场景 9）

```bash
oryxos init
# 1. 装配 Agent（frontmatter 唯一配置来源，FR-003）
cp config/demos/daily-weather/AGENT.md .oryxos/agents/daily-weather/AGENT.md

# 2. 凭证只走环境变量（宪法 VI）
export DEEPSEEK_API_KEY=sk-xxx
export ORYXOS_NOTIFY_WEBHOOK_URL=https://open.feishu.cn/open-apis/bot/v2/hook/xxx

# 3. 常驻守护（web + 调度器）
oryxos gateway
#    启动日志应出现：已注册定时任务 1 个（daily-weather:morning-report）
```

可选：把 `config-fragment.yml` 的白名单段合并进 `config/application.yml`（收紧域名）。

## 通过标准（quickstart 场景 9）

- 次日 07:00（Asia/Shanghai）无人工干预自动完成「查天气 → 生成建议 → 群推送」
- `sqlite3 .oryxos/oryxos.db "select tool_name, success from tool_invocations order by id desc limit 10;"` 含两次 HTTP 调用（open-meteo + webhook）
- `GET /api/v1/sessions/scheduler-scheduler-daily-weather` 可查该自动会话
- 手动补跑走同一链路：`oryxos chat --profile daily-weather --message "查询北京今天的天气并生成穿搭建议，然后推送到群 Webhook。"`

## 备考：cron 语法

Spring 6 字段 cron：`秒 分 时 日 月 周`，如 `0 7 * * * ?` = 每天 07:00:00。调试期可临时改为 `*/30 * * * * ?`（每 30 秒）观察自动触发。
