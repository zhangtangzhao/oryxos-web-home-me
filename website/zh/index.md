---
layout: home

hero:
  name: "OryxOS"
  text: "Agent Harness OS"
  tagline: 一句自然语言发布任务，底座把它拆解，组织一支 Agent 团队分工协作，交付一个结果。Java 原生 · 私有部署 · 全链路可审计。
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
---

<div class="ox-formula">
  <div class="ox-formula__bar">
    <span>自然语言 (md)</span><i>+</i><span>Memory</span><i>+</i><span>Tool</span><i>+</i><span>MCP</span><i>+</i><span>Skill</span><i>+</i><span>知识库</span><i>+</i><span>Notify</span><em>= 一个 Agent</em>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Reason → Act → Observe</div>
  <h2 class="ox-title">看得见的思考，管得住的执行</h2>
  <p class="ox-sub">自实现的 ReAct 引擎：LLM 决定调哪个工具，OryxOS 经沙箱白名单校验后执行、回填、继续推理。每一步都有记录，循环行为完全可控。</p>
  <OryxTerminal title="oryxos chat --profile ops" :lines="[
    { t: '$ oryxos chat --profile ops', c: 'cmd' },
    { t: '', c: 'dim' },
    { t: '> 线上订单服务最近一小时为什么频繁超时？', c: 'user' },
    { t: '', c: 'dim' },
    { t: '● Reason   先查订单服务日志，再核对数据库连接池状态', c: 'reason' },
    { t: '▸ Act      shell → kubectl logs orders-7f9c --tail 200', c: 'act' },
    { t: '◦ Observe  命中 37 次 timeout，伴随连接池等待告警', c: 'observe' },
    { t: '● Reason   拉取数据库监控，确认连接池水位', c: 'reason' },
    { t: '▸ Act      http_get → monitor.internal/api/db/pool', c: 'act' },
    { t: '◦ Observe  active 98 / max 100，连接池接近打满', c: 'observe' },
    { t: '', c: 'dim' },
    { t: '✔ 根因：DB 连接池耗尽，导致订单服务大面积超时。', c: 'ok' },
    { t: '  建议：连接池上限 100 → 200，并治理未释放连接的慢查询。', c: 'ok2' }
  ]" />
</div>

<div class="ox-section">
  <div class="ox-kicker">oryxos ps</div>
  <h2 class="ox-title">像操作系统管理进程一样，管理一群 Agent</h2>
  <p class="ox-sub">一个目录 = 一个 Agent，一个底座运行一群 Agent。运维助手、客服助手、HR 助手并存于同一实例，共享模型路由、工具、记忆与审计。</p>
  <OryxProcess
    title="oryxos ps"
    note="一个目录 = 一个 Agent · 多 Agent 同实例并存"
    :header="['PID', 'AGENT', 'STATUS', 'MEMORY', 'TOOLS', 'LAST ACTIVE']"
    :rows="[
      ['01', 'ops-assistant 运维助手', 'running', '2.4 MB', 'shell · http · notify', '3 秒前'],
      ['02', 'cs-helper 客服助手', 'running', '1.8 MB', 'kb · recall_memory', '12 秒前'],
      ['03', 'hr-assistant HR 助手', 'running', '1.6 MB', 'kb · notify', '1 分钟前'],
      ['04', 'kb-curator 知识管家', 'idle', '0.9 MB', 'read_file · write_file', '26 分钟前'],
      ['05', 'report-writer 日报写手', 'scheduled', '—', 'write_file · notify', '每日 09:00']
    ]"
  />
</div>

<div class="ox-section">
  <div class="ox-kicker">Four Gates → Four Keys</div>
  <h2 class="ox-title">四道门槛，一次拆掉</h2>
  <p class="ox-sub">Agent 大多停在 demo，不是模型不行，是缺一个能让 Agent 上生产的运行环境。OryxOS 补的就是这一层。</p>
  <div class="ox-gates">
    <div class="ox-gate">
      <div class="ox-gate__pain">定义一个 Agent 要写代码</div>
      <div class="ox-gate__key">KEY 01</div>
      <div class="ox-gate__answer">一个目录 = 一个 Agent</div>
      <p>一份 AGENT.md 即定义，不写代码。最懂业务的人，自己就能上线一个新 Agent。</p>
    </div>
    <div class="ox-gate">
      <div class="ox-gate__pain">云平台要把数据拿走</div>
      <div class="ox-gate__key">KEY 02</div>
      <div class="ox-gate__answer">私有部署，数据不出域</div>
      <p>装在企业自己的 K8s、虚拟机或物理机上。能过 SaaS 方案过不了的合规审查。</p>
    </div>
    <div class="ox-gate">
      <div class="ox-gate__pain">执行是黑盒，不敢上生产</div>
      <div class="ox-gate__key">KEY 03</div>
      <div class="ox-gate__answer">全链路审计 + 强制沙箱</div>
      <p>文件 / 命令 / 域名白名单校验，每次 LLM 与工具调用落库。从第一天就可追溯。</p>
    </div>
    <div class="ox-gate">
      <div class="ox-gate__pain">跑一个容易，跑一群难</div>
      <div class="ox-gate__key">KEY 04</div>
      <div class="ox-gate__answer">一群 Agent 的生命周期与治理</div>
      <p>多 Agent 并存、定时调度、REST 治理接口。底座优先于 Agent，环境先于个体。</p>
    </div>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Five Core Capabilities</div>
  <h2 class="ox-title">五大核心能力</h2>
  <p class="ox-sub">覆盖一个 Agent 从模型接入到对外服务的完整链路，全部构建在同一个可审计的底座上。</p>
  <div class="ox-caps">
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-01</div>
      <h3>对接 LLM</h3>
      <p>Provider 抽象统一对接 DeepSeek、通义、Kimi、智谱、Anthropic、OpenAI 与本地推理。Agent 不感知厂商，运行时切换无锁定。</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-02</div>
      <h3>ReAct 循环</h3>
      <p>自实现推理引擎：Reason → Act → Observe。LLM 思考是否调工具、调哪个，OryxOS 执行后回填结果，直到交付。</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-03</div>
      <h3>记忆系统</h3>
      <p>会话记忆（SQLite）+ 长期记忆（MEMORY.md）两层。跨对话记住用户偏好、项目背景与关键决策，越用越懂你。</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-04</div>
      <h3>工具体系</h3>
      <p>内置 9 个 Tool + MCP Connector + @Tool 注解三档扩展。从零代码复用生态，到原生方法，按门槛自由选择。</p>
    </div>
    <div class="ox-cap">
      <div class="ox-cap__no">CAP-05</div>
      <h3>对外服务</h3>
      <p>完整 REST API 暴露所有能力：会话管理、Agent 调用、Profile / Memory / Tool 查询、系统状态。任何语言可集成。</p>
    </div>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Architecture</div>
  <h2 class="ox-title">四层架构，安全是地基</h2>
  <p class="ox-sub">接入、引擎、能力、基础四层解耦。状态外置、实例无状态，从架构起为分布式留好路。</p>
  <div class="ox-arch">
    <img src="/architecture.svg" alt="OryxOS 架构图">
  </div>
  <div class="ox-layers">
    <div class="ox-layer ox-layer--1"><b>接入层</b><span>CLI Channel · REST API · 定时调度</span></div>
    <div class="ox-layer ox-layer--2"><b>引擎层</b><span>ReActLoop · PromptBuilder · ToolExecutor</span></div>
    <div class="ox-layer ox-layer--3"><b>能力层</b><span>Provider · Memory · Tool/MCP · Sandbox</span></div>
    <div class="ox-layer ox-layer--4"><b>基础层</b><span>Profile/Bootstrap · SQLite · Session · 审计</span></div>
  </div>
</div>

<div class="ox-section">
  <div class="ox-kicker">Roadmap</div>
  <h2 class="ox-title">慢就是快，克制且聚焦</h2>
  <p class="ox-sub">先把单机运行时内核做扎实，再在它之上生长出分布式能力。每一步都走扎实，不一开始就堆一个无法落地的大型架构。</p>
  <div class="ox-roadmap">
    <div class="ox-stage">
      <div class="ox-stage__phase">PHASE 1 · 当前</div>
      <b>单机运行时内核</b>
      <span>五大核心能力跑通：配置即 Agent、多 Agent 并存、REST API、对接 MCP。把单节点运行一群 Agent 做到可用。</span>
    </div>
    <div class="ox-stage">
      <div class="ox-stage__phase">PHASE 2 · 规划</div>
      <b>底座分布式</b>
      <span>节点无状态化、状态外置、多副本部署。支撑更大规模与高可用。</span>
    </div>
    <div class="ox-stage">
      <div class="ox-stage__phase">PHASE 3 · 愿景</div>
      <b>跨节点 Agent 协作</b>
      <span>引入 Agent 通信底座，对接 A2A。多节点上的 Agent 跨节点发现、委托、可靠异步协同。</span>
    </div>
  </div>
</div>

<div class="ox-quote">
  <p>让每一家公司，都能用自然语言<br>跑起来自己的 Agent。</p>
  <span>ORYXOS · APACHE 2.0 · ORYX-LABS</span>
</div>
