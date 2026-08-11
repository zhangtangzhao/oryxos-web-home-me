---
layout: home

hero:
  name: "OryxOS"
  text: "Enterprise Agent OS"
  tagline: Java Native · Private · Auditable
  image:
    src: /logo.svg
    alt: OryxOS
  actions:
    - theme: brand
      text: Get Started
      link: /en/introduction
    - theme: alt
      text: View on GitHub
      link: https://github.com/oryx-labs/oryxos

features:
  - icon: 🤖
    title: One Directory = One Agent
    details: Define an agent with a single AGENT.md file — no code required. Multiple agents coexist on one instance, sharing the same infrastructure.
  - icon: ☕
    title: Java Native
    details: Built on Java 21 + Spring Boot 3.x. Single fat JAR deployment. Reuse your existing Java ops toolchain — no new stack to learn.
  - icon: 🔒
    title: Private & Controlled
    details: Deploy on your own K8s, VMs, or bare metal. Data never leaves your infrastructure. No cloud lock-in.
  - icon: 🛡️
    title: Secure by Design
    details: Tool execution gated by file/command/domain whitelists. Mandatory sandbox isolation. Credentials via env vars. Full audit trail from day one.
  - icon: 🧠
    title: Self-Implemented ReAct
    details: Core reasoning loop built from scratch — no external agent frameworks. Fully controllable mechanism. Spring AI used only for protocol adaptation.
  - icon: 🔌
    title: Open Standards
    details: Tools via MCP, agent collaboration via A2A, agent directories borrowing Anthropic Agent Skills format. Works with the ecosystem.
---

<div class="oryxos-section-title">Five Core Capabilities</div>

<div class="oryxos-features">
  <div class="oryxos-feature-card">
    <h3>🤖 LLM Integration</h3>
    <p>Provider abstraction unifies access to all major LLMs — DeepSeek, Qwen, Kimi, Anthropic, OpenAI. Agents never know which provider they're talking to. Switch at runtime with zero lock-in.</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>🧠 ReAct Loop</h3>
    <p>Self-implemented reasoning engine: Reason → Act → Observe. The LLM decides whether to call a tool, OryxOS executes it and feeds results back, then the LLM decides the next step. Fully controllable.</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>💾 Memory System</h3>
    <p>Two-layer memory: session memory (SQLite) + long-term memory (MEMORY.md). Agents remember user preferences, project context, and key decisions across conversations.</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>🔧 Tool System</h3>
    <p>9 built-in tools (file, shell, HTTP, memory, notify) + 3 tiers of plugin tools: zero-code (Agent directory + MCP), light-code (custom MCP server), and deep-code (@Tool Java Bean).</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>🌐 Web Service</h3>
    <p>Complete REST API exposes all capabilities. Covering session management, agent invocation, profile/memory/tool queries, and system health — integrate from any language that can send HTTP.</p>
  </div>
</div>

<div class="oryxos-section-title">Why OryxOS?</div>

<div class="oryxos-features">
  <div class="oryxos-feature-card">
    <h3>Define without Code</h3>
    <p>Business users who know the domain best can define agents without writing code — just an AGENT.md file in a directory.</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>Private Deployment</h3>
    <p>Data stays 100% on your infrastructure. No cloud dependency. Pass compliance reviews that SaaS-based solutions can't.</p>
  </div>
  <div class="oryxos-feature-card">
    <h3>Full Audit Trail</h3>
    <p>Every tool invocation and LLM call is recorded from day one. Trace who did what, when, through which agent — auditable by design, not bolted on later.</p>
  </div>
</div>
