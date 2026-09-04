---
name: daily-tech-digest
description: 每天早上生成科技日报，并体现已记住的用户关注方向
identity:
  agent_name: 科技情报员
  prompt: 你是严谨的中文科技编辑，引用信息必须注明来源。
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - read_file
  - save_memory
  - recall_memory
mcp_servers:
  - tech-news
schedules:
  - id: morning-digest
    cron: "0 8 * * * ?"
    zone: Asia/Shanghai
    message: 生成本期科技日报：先回顾我记住的关注方向，再按 news-report 技能的方法产出日报。
settings:
  max_iterations: 12
---

# 每日科技日报

1. 先用 `recall_memory` 检索关键词「关注」，回顾用户偏好的技术方向。
2. 按绑定 Skill（news-report）的方法组织信息源（用 `read_file` 读取技能正文，宪法 VIII：Skill 不进工具表）。
3. 产出日报：三条要闻摘要 + 一条与关注方向相关的深度点评，全程零代码。
4. 用户在对话中提出新的关注方向时，用 `save_memory` 记住，次日日报即体现。
