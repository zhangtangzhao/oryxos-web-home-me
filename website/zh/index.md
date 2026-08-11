---
layout: home

hero:
  name: "OryxOS"
  text: "企业级 Agent OS"
  tagline: Java 原生 · 私有可控 · 全链路可审计
  image:
    src: /logo.svg
    alt: OryxOS
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/introduction
    - theme: alt
      text: GitHub
      link: https://github.com/oryx-labs/oryxos

features:
  - icon: 🤖
    title: 一个目录 = 一个 Agent
    details: 一个包含 AGENT.md 的目录定义一个 Agent，不用写代码。多个 Agent 同实例并存，共享同一套基础设施。
  - icon: ☕
    title: Java 原生
    details: 基于 Java 21 + Spring Boot 3.x，单可执行 JAR 部署。复用现有 Java 运维工具链，不需要学新栈。
  - icon: 🔒
    title: 私有可控
    details: 装在企业自己的 K8s、虚拟机或物理机上。数据不出域，不锁任何云。
  - icon: 🛡️
    title: 安全隔离
    details: 工具调用经文件、命令、网络白名单校验，强制沙箱隔离。凭证走环境变量不落地。全链路可审计。
  - icon: 🧠
    title: 自实现 ReAct
    details: 核心推理循环自己实现，不套外部 Agent 框架。机制完全可控。Spring AI 只用于协议转换。
  - icon: 🔌
    title: 对接开放标准
    details: 工具用 MCP、Agent 协作用 A2A、Agent 目录借 Anthropic Agent Skills 形态。与生态协同不另立协议。
---

<div class="oryxos-section-title">五大核心能力</div>

<div class="oryxos-features">
  <div class="oryxos-feature-card">
    <h3>🤖 对接 LLM</h3>
    <p>Provider 抽象统一对接主流大模型——DeepSeek、通义、Kimi、智谱、Anthropic、OpenAI。Agent 不感知具体调的是哪家，运行时切换无锁定。</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>🧠 ReAct 循环</h3>
    <p>自实现推理引擎：Reason → Act → Observe。LLM 思考是否调工具、调哪个，OryxOS 执行后回填结果，LLM 再决定下一步。循环行为完全可控。</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>💾 记忆系统</h3>
    <p>会话记忆（SQLite）+ 长期记忆（MEMORY.md）两层，跨对话保留用户偏好、项目背景、关键决策。让 Agent 越用越懂你。</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>🔧 工具体系</h3>
    <p>内置 9 个 Tool（文件、Shell、HTTP、记忆、通知）+ Plugin Tool 三档接入：零代码（Agent 目录 + MCP）、轻代码（自写 MCP server）、重代码（@Tool Java Bean）。</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>🌐 Web Service</h3>
    <p>完整 REST API 对外暴露所有能力，覆盖会话管理、Agent 调用、Profile/Memory/Tool 查询、系统状态。任何能发 HTTP 的语言都能接入。</p>
  </div>
</div>

<div class="oryxos-section-title">为什么选择 OryxOS？</div>

<div class="oryxos-features">
  <div class="oryxos-feature-card">
    <h3>不用写代码就能定义 Agent</h3>
    <p>最懂业务的人写一份 AGENT.md 就能上线一个新 Agent，不需要写后端代码。</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>私有部署，数据不出域</h3>
    <p>数据 100% 留在企业自己的基础设施上。不依赖任何云。能过 SaaS 方案过不了的合规审查。</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>全链路可审计</h3>
    <p>每次工具调用和 LLM 调用从第一天就记录在案。谁、什么时候、通过哪个 Agent、做了什么、结果如何——可追溯、可审计。</p>
  </div>
</div>
