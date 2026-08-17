---
layout: home

hero:
  name: "OryxOS"
  text: "Agent Harness OS"
  tagline: Publish a task in plain language — the base decomposes it, assembles a team of agents, and delivers the result. Java native · Private · Fully auditable.
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
---

<div class="ox-formula">
  <div class="ox-formula__bar">
    <span>Natural language (md)</span><i>+</i><span>Memory</span><i>+</i><span>Tool</span><i>+</i><span>MCP</span><i>+</i><span>Skill</span><i>+</i><span>Knowledge</span><i>+</i><span>Notify</span><em>= one Agent</em>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Reason → Act → Observe</div>
  <h2 class="ox-title">Visible reasoning, governed execution</h2>
  <p class="ox-sub">A self-implemented ReAct engine: the LLM decides which tool to call, OryxOS executes it behind sandbox whitelists, feeds the result back, and keeps reasoning. Every step is recorded. The loop is fully controllable.</p>
  <OryxTerminal title="oryxos chat --profile ops" :lines="[
    { t: '$ oryxos chat --profile ops', c: 'cmd' },
    { t: '', c: 'dim' },
    { t: '> Why has the orders service been timing out for the past hour?', c: 'user' },
    { t: '', c: 'dim' },
    { t: '● Reason   Check service logs first, then the DB connection pool', c: 'reason' },
    { t: '▸ Act      shell → kubectl logs orders-7f9c --tail 200', c: 'act' },
    { t: '◦ Observe  37 timeouts, plus connection-pool wait alerts', c: 'observe' },
    { t: '● Reason   Pull database metrics to confirm pool saturation', c: 'reason' },
    { t: '▸ Act      http_get → monitor.internal/api/db/pool', c: 'act' },
    { t: '◦ Observe  active 98 / max 100 — pool nearly exhausted', c: 'observe' },
    { t: '', c: 'dim' },
    { t: '✔ Root cause: DB connection pool exhaustion.', c: 'ok' },
    { t: '  Fix: raise pool limit 100 → 200 and fix leaking slow queries.', c: 'ok2' }
  ]" />
</div>

<div class="ox-section">
  <div class="ox-kicker">oryxos ps</div>
  <h2 class="ox-title">Manage a herd of agents like processes on an OS</h2>
  <p class="ox-sub">One directory = one agent. One base runs a herd of them. Ops, support and HR assistants coexist on a single instance — sharing model routing, tools, memory and audit.</p>
  <OryxProcess
    title="oryxos ps"
    note="one directory = one agent · multi-agent on one instance"
    :header="['PID', 'AGENT', 'STATUS', 'MEMORY', 'TOOLS', 'LAST ACTIVE']"
    :rows="[
      ['01', 'ops-assistant', 'running', '2.4 MB', 'shell · http · notify', '3s ago'],
      ['02', 'cs-helper', 'running', '1.8 MB', 'kb · recall_memory', '12s ago'],
      ['03', 'hr-assistant', 'running', '1.6 MB', 'kb · notify', '1m ago'],
      ['04', 'kb-curator', 'idle', '0.9 MB', 'read_file · write_file', '26m ago'],
      ['05', 'report-writer', 'scheduled', '—', 'write_file · notify', 'daily 09:00']
    ]"
  />
</div>

<div class="ox-section">
  <div class="ox-kicker">Four Gates → Four Keys</div>
  <h2 class="ox-title">Four gates, torn down at once</h2>
  <p class="ox-sub">Agents stall at demo stage not because models are weak — they lack a runtime built for production. OryxOS is that layer.</p>
  <div class="ox-gates">
    <div class="ox-gate">
      <div class="ox-gate__pain">Defining an agent requires code</div>
      <div class="ox-gate__key">KEY 01</div>
      <div class="ox-gate__answer">One directory = one agent</div>
      <p>A single AGENT.md defines an agent — no code. The people who know the business best can ship agents themselves.</p>
    </div>
    <div class="ox-gate">
      <div class="ox-gate__pain">Cloud platforms take your data</div>
      <div class="ox-gate__key">KEY 02</div>
      <div class="ox-gate__answer">Private deployment</div>
      <p>Runs on your own K8s, VMs or bare metal. Data never leaves your infrastructure. Passes compliance reviews SaaS can't.</p>
    </div>
    <div class="ox-gate">
      <div class="ox-gate__pain">Execution is a black box</div>
      <div class="ox-gate__key">KEY 03</div>
      <div class="ox-gate__answer">Full audit + sandboxing</div>
      <p>File / command / domain whitelists. Every LLM call and tool invocation is persisted from day one.</p>
    </div>
    <div class="ox-gate">
      <div class="ox-gate__pain">One agent is easy, a herd is hard</div>
      <div class="ox-gate__key">KEY 04</div>
      <div class="ox-gate__answer">Lifecycle & governance for herds</div>
      <p>Multi-agent coexistence, scheduled tasks, governance APIs. The base comes before the agent — environment before individual.</p>
    </div>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Five Core Capabilities</div>
  <h2 class="ox-title">Five core capabilities</h2>
  <p class="ox-sub">The complete chain from model access to external services — all built on one auditable base.</p>
  <div class="ox-caps">
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-01</div>
      <h3>LLM Integration</h3>
      <p>Provider abstraction unifies DeepSeek, Qwen, Kimi, GLM, Anthropic, OpenAI and local inference. Agents never know the vendor — switch at runtime, zero lock-in.</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-02</div>
      <h3>ReAct Loop</h3>
      <p>Self-implemented reasoning engine: Reason → Act → Observe. The LLM decides whether and which tool to call; OryxOS executes and feeds results back until delivery.</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-03</div>
      <h3>Memory System</h3>
      <p>Two layers: session memory (SQLite) + long-term memory (MEMORY.md). Preferences, context and decisions persist across conversations.</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-04</div>
      <h3>Tool System</h3>
      <p>9 built-in tools + MCP connector + @Tool annotations — three tiers of extension. From zero-code ecosystem reuse to native methods.</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-05</div>
      <h3>Web Service</h3>
      <p>A complete REST API exposes everything: sessions, agent invocation, profile / memory / tool queries, system status. Integrate from any language.</p>
    </div>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Architecture</div>
  <h2 class="ox-title">Four layers, security as the foundation</h2>
  <p class="ox-sub">Access, engine, capability and infrastructure layers, cleanly decoupled. Stateless instances with externalized state — distributed-ready by design.</p>
  <div class="ox-arch">
    <img src="/architecture.svg" alt="OryxOS architecture">
  </div>
  <div class="ox-layers">
    <div class="ox-layer ox-layer--1"><b>Access</b><span>CLI Channel · REST API · Scheduler</span></div>
    <div class="ox-layer ox-layer--2"><b>Engine</b><span>ReActLoop · PromptBuilder · ToolExecutor</span></div>
    <div class="ox-layer ox-layer--3"><b>Capability</b><span>Provider · Memory · Tool/MCP · Sandbox</span></div>
    <div class="ox-layer ox-layer--4"><b>Infrastructure</b><span>Profile/Bootstrap · SQLite · Session · Audit</span></div>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Roadmap</div>
  <h2 class="ox-title">Slow is smooth, focused and deliberate</h2>
  <p class="ox-sub">Make the single-node runtime kernel solid first, then grow distribution on top of it. No grand architecture before it can land.</p>
  <div class="ox-roadmap">
    <div class="ox-stage">
      <div class="ox-stage__phase">PHASE 1 · NOW</div>
      <b>Single-node runtime kernel</b>
      <span>Five core capabilities working: config-as-agent, multi-agent coexistence, REST API, MCP integration. Making one node run a herd — usable.</span>
    </div>
    <div class="ox-stage">
      <div class="ox-stage__phase">PHASE 2 · PLANNED</div>
      <b>Distributed base</b>
      <span>Stateless nodes, externalized state, multi-replica deployment. Larger scale and high availability.</span>
    </div>
    <div class="ox-stage">
      <div class="ox-stage__phase">PHASE 3 · VISION</div>
      <b>Cross-node collaboration</b>
      <span>Agent communication base with A2A. Discovery, delegation and reliable async coordination across nodes.</span>
    </div>
  </div>
</div>

<div class="ox-quote">
  <p>Let every company run its own agents,<br>in plain natural language.</p>
  <span>ORYXOS · APACHE 2.0 · ORYX-LABS</span>
</div>
