---
name: daily-weather
description: 每天早上推送天气与穿搭建议
identity:
  agent_name: 天气助手
  prompt: 你是一个贴心的生活助手，输出简洁实用的中文建议。
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
  - notify
channels:
  - name: cli
schedules:
  - id: morning-report
    cron: "0 7 * * * ?"
    zone: Asia/Shanghai
    message: 查询北京今天的天气并生成穿搭建议，然后把结果推送到群 Webhook。
settings:
  max_iterations: 10
---

# 每日天气任务

每天早上执行：

1. 用 `http_get` 调用 Open-Meteo 实时天气接口（无需密钥）获取北京天气。
2. 基于天气数据生成 3 条简短穿衣建议。
3. 用 `notify` 把「城市 + 天气摘要 + 建议」推送到配置的群 Webhook。
