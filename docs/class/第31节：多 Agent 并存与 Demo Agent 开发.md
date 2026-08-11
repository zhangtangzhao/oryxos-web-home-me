# 第31节：多 Agent 并存与 Demo Agent 开发

> **课时目标**：完成三个 Demo Agent 的开发与验证——天气助手、科技日报、GitHub 日报——验证多 Agent 并存、定时调度、通知推送全流程。

---

## 一、本节目的

底座和 Skill 体系已经就绪，本节回归实践：**开发三个真实的业务 Agent**，验证 OryxOS 作为一个 Agent OS 的核心价值——多个 Agent 在同一个底座上独立运行。

---

## 二、三个 Demo Agent

### 2.1 每日天气助手（daily-weather）

**功能**：每天早上 8 点查询指定城市天气，生成简洁报告推送到飞书。

**AGENT.md 关键配置**：
```yaml
name: daily-weather
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get, notify, save_memory, current_time]
skills: [report-format, notify-message]
notify: team-lark
schedule:
  - id: morning-weather
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: "查询北京、上海、深圳今天的天气，生成报告，通知到 team-lark"
```

### 2.2 科技日报（tech-daily）

**功能**：每天早上 8 点汇总 AI 和科技领域最新动态，生成日报推送。

**AGENT.md 关键配置**：
```yaml
name: tech-daily
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get, fetch_webpage, write_file, notify, save_memory]
skills: [web-research, summarize, notify-message]
notify: team-lark
schedule:
  - id: tech-news
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: >
      查询 Hacker News 和 GitHub Trending 上 AI 相关的热门项目，
      每个项目一句话介绍，整理成日报格式，通知到 team-lark
```

### 2.3 GitHub 日报（github-rust）

**功能**：查询 GitHub 上 Rust 语言最火的项目，推送到飞书。

**AGENT.md 关键配置**：
```yaml
name: github-rust
provider: { name: deepseek, model: deepseek-chat }
tools: [http_get, json_extract, notify, save_memory, current_time]
skills: [summarize, notify-message]
notify: team-lark
schedule:
  - id: rust-trending
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: >
      查询 GitHub API：搜索 Rust 语言今日最热项目（q=language:rust，
      按 stars 排序，取前 10），整理成简洁的日报格式并通知到 team-lark
```

---

## 三、多 Agent 架构验证

### 3.1 目录结构

```
.oryxos/agents/
├── daily-weather/
│   ├── AGENT.md
│   ├── skills/
│   └── output/
├── tech-daily/
│   ├── AGENT.md
│   ├── skills/
│   └── output/
└── github-rust/
    ├── AGENT.md
    ├── skills/
    └── output/
```

### 3.2 运行时行为

```
AgentScheduler 启动
  ├── 扫描 .oryxos/agents/
  ├── 发现 3 个 Agent，每个都有 schedule
  ├── 注册 3 个定时任务
  └── 管理台可查看全部

每天早上 8:00:
  ├── daily-weather 触发 → 查询天气 → 通知飞书
  ├── tech-daily 触发     → 抓取新闻 → 通知飞书
  └── github-rust 触发    → 查询 GitHub → 通知飞书

管理台:
  ├── Agent 列表：3 个 Agent，各自有描述 + 最后执行时间
  ├── 每个 Agent 可点「立即触发」
  ├── 每个 Agent 详情可查看执行历史
  └── 定时任务列表：3 个任务，可查看下次执行时间
```

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 创建 `daily-weather` Agent | AGENT.md + skills |
| D2 | 创建 `tech-daily` Agent | AGENT.md + skills |
| D3 | 创建 `github-rust` Agent | AGENT.md + skills |
| D4 | 启动服务验证三个 Agent | 全流程联调 |
| D5 | 验证飞书通知推送 | 消息发送成功 |
| D6 | 管理台验证 | 查看 Agent 列表 + 执行历史 |
| D7 | 编写文档 | Agent 开发指南 |

---

## 五、Harness：如何验收

### 创建 Agent
```bash
# 1. 手动创建 Agent 目录和 AGENT.md
mkdir -p .oryxos/agents/daily-weather/skills
# 写入 AGENT.md

# 2. 或通过 API 创建
curl -X POST http://localhost:8080/api/v1/agents \
  -d '{"name":"daily-weather","description":"每日天气助手"}'
```

### 运行验证
```bash
# 1. 启动服务
java -jar oryxos-boot.jar serve --port 8080

# 2. 手动触发 Agent
curl -X POST http://localhost:8080/api/v1/agents/daily-weather/invoke \
  -H "Content-Type: application/json" \
  -d '{"message":"查询北京的天气"}'

# 3. 检查飞书是否收到消息
# 4. 管理台 → Agent 列表 → 查看执行历史
# 5. 管理台 → 长期记忆 → 查看保存的记忆
```

### 检查点
- [x] 三个 Agent 并存，互不干扰
- [x] 定时任务按 Cron 正常触发
- [x] 通知成功推送到飞书
- [x] 执行历史和记忆正常记录
- [x] 管理台可管理所有 Agent
