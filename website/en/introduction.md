---
title: Introduction
description: OryxOS is an open-source Agent Harness OS — one directory defines an agent, one base runs a herd of them. Private deployment, data never leaves your domain.
---

# Introduction

## What is OryxOS

OryxOS is an open-source **Agent Harness OS** built on Java 21 for enterprise private deployment. It runs on your own K8s or servers as a unified base hosting business agents — ops assistant, customer support, HR assistant, knowledge management — all sharing channel access, model routing, tool invocation, memory, sandboxing and audit. Data stays entirely within your infrastructure. No cloud lock-in.

> **You issue a task in natural language → the base decomposes it → organizes a team of agents → multiple agents collaborate → a result is delivered.**

A herd of agents runs on one operating system — reliably, collaboratively. Let every company run its own agents in plain natural language.

## Agent Harness OS: the skeleton around the model

The **agent harness** is the scaffolding wrapped around a model that turns it into an agent that can actually work:

- the loop driving **Reason → Act → Observe**
- the **tools** it can call, and the machinery that executes them
- the **context** assembled before every call
- the **memory** it accumulates
- the **sandbox** that constrains it
- the **audit trail** of what it did

A bare model generates text. The harness is what makes it do work — reliably and safely.

**North star formula:**

```
Natural language (md) + Memory + Tool + MCP (Connector) + Skill + Knowledge + Notify = one Agent
```

One directory defines an agent. One base runs a herd. Private deployment, data never leaves your domain. Native to MCP and A2A open protocols.

## Why OryxOS

Every company has work that should be handed to agents, but most agent projects stall at the demo stage, blocked by four barriers:

1. **Defining an agent requires code** — the people who know the business best are excluded
2. **Cloud platforms demand your data** — compliance won't allow it
3. **Execution is a black box** — no audit, no whitelists, no approvals; enterprises can't trust it in production
4. **Running one is easy, running many is hard** — nobody offers the "operating system for a herd of agents" layer

OryxOS removes all four at once: **natural-language definition, private deployment, full audit with sandboxing, and lifecycle management for an entire team of agents.**

The deeper conviction: **the bottleneck for reliable production agents is usually not the model — it's the runtime environment.** Whether an agent can actually deliver depends on having a dependable base: the right context, governed tools, isolated and auditable invocations, messages that arrive exactly once across nodes. OryxOS is not another agent — it is the base that makes a herd of agents reliable.

## Agent runtime vs. Agent Harness OS

| | Agent runtime | Agent Harness OS |
|---|---|---|
| Manages | A single agent | A herd of agents |
| Responsibilities | Model calls, tool execution, context management, reasoning-loop control | Lifecycle, unified channels & access, shared memory, multi-tenancy & governance, cross-node collaboration |
| OS analogy | The execution environment of one process | The layer that manages many processes, schedules resources, provides shared services |

In one sentence: the runtime makes one agent run; the Harness OS runs and manages a herd. OryxOS is the latter.

## Five Core Capabilities

| Capability | Description |
|------------|-------------|
| 🤖 **LLM Integration** | Provider abstraction unifies all major LLMs — agents never perceive the vendor, switch at runtime with zero lock-in, local inference supported. Multiple providers coexist via explicit mapping |
| 🧠 **ReAct Loop** | The agent's reasoning engine, self-implemented with no external framework. The LLM decides whether and which tool to call; OryxOS executes and feeds results back until the final response or max iterations. Fully controllable |
| 💾 **Memory** | State across conversations. Two layers — session memory plus file-based long-term memory with keyword retrieval, interface reserved for vector upgrade |
| 🔧 **Tool System** | Agents act on the world through tools. Built-in file / shell / HTTP tools; three extension tiers from low to high: zero-code (agent directory + existing MCP server), light-code (custom MCP server), deep-code (native methods) |
| 🌐 **Web Service** | Everything exposed via REST API. Business systems integrate over HTTP from any language |

## A ReAct troubleshooting session in one minute

```text
$ java -jar oryxos.jar chat --profile ops
> Why has the orders service been timing out for the past hour?

● Reason   Check service logs first, then the DB connection pool
▸ Act      shell → kubectl logs orders-7f9c --tail 200
◦ Observe  37 timeouts, plus connection-pool wait alerts

● Reason   Pull database metrics to confirm pool saturation
▸ Act      http_get → monitor.internal/api/db/pool
◦ Observe  active 98 / max 100 — pool nearly exhausted

✔ Root cause: DB connection pool exhaustion.
  Fix: raise pool limit 100 → 200 and fix leaking slow queries.
```

## Key Characteristics

- 🤖 **One directory = one agent** — a directory containing `AGENT.md` defines an agent, no code; multiple agents coexist on one instance
- ☕ **Java native** — Java with JDK 21, single executable JAR deployment, reuses your existing Java ops toolchain
- 🔒 **Private & controlled** — runs on your own K8s, VMs or bare metal; data never leaves your domain, no cloud lock-in
- 🛡️ **Security by design** — file / command / network whitelists, mandatory sandbox isolation, credentials via enterprise secret management (never on disk), full audit trail from day one
- 🧠 **Self-implemented ReAct** — the core reasoning loop is our own, no external agent frameworks, fully controllable
- 🔌 **Open standards** — MCP for tools, A2A for collaboration, agent directories borrowing the Anthropic Agent Skills format
- 🧩 **Three extension tiers** — from zero-code agent directories to custom MCP servers to native methods
- 💾 **Memory across conversations** — session plus long-term memory keeps context alive
- 🌐 **Stateless & scalable** — stateless instances with externalized state, distributed-ready by design

## Quick Start

### Prerequisites

- JDK 21+
- Maven 3.9+

### Build

```bash
git clone https://github.com/oryx-labs/oryxos.git
cd oryxos
mvn clean package -DskipTests
```

### 5-Minute Quick Start

```bash
# 1. Initialize workspace
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init

# 2. Create an agent
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar profile create weather

# 3. Set model credentials (env var only — never in files!)
export DEEPSEEK_API_KEY=sk-xxx

# 4. Start chatting
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat --profile weather
```

### Start the API service

```bash
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve --port 8080
```

Visit `http://localhost:8080/api/v1/health` to confirm the service is up.

## Architecture

Four decoupled layers: Access (CLI / REST / scheduler) → Engine (ReActLoop / PromptBuilder / ToolExecutor) → Capability (Provider / Memory / Tool+MCP / Sandbox) → Infrastructure (Profile / SQLite / Session / audit). Security cuts across every layer.

![OryxOS architecture](/architecture.svg)

## Roadmap

Our philosophy: **slow is smooth, restrained and focused.** Make the single-node runtime kernel solid and genuinely usable first, then grow distribution on top of it. Distribution is the end-state vision, but engineering-wise we go single-node first — solid steps, no grand architecture before it can land.

- **Phase 1 (current) — single-node runtime kernel**: five core capabilities working — config-as-agent, multi-agent coexistence, REST API, MCP integration; make one node running a herd of agents truly usable
- **Phase 2 (planned) — distributed base**: stateless nodes, externalized state, multi-replica deployment; larger scale and high availability
- **Phase 3 (vision) — cross-node collaboration**: agent communication base with A2A; discovery, delegation and reliable async coordination across nodes
- **Cross-cutting (filled in alongside each phase)**: multi-tenancy, SSO, complete audit, tool policies, observability, web admin

## Design Principles

- **Base before agents** — the most important deliverable is not one powerful agent, but an environment where any agent runs reliably
- **Self-implemented core, control first** — the reasoning loop is ours; protocol adaptation reuses mature libraries
- **Configuration is the agent** — an agent is defined by a config document, not written in code
- **Open standards** — MCP for tools, A2A for collaboration, open formats for skills
- **Stateless instances, externalized state** — the prerequisite for a smooth path from single-node to distributed
- **Security is foundation, not patch** — controlled tool sources, least privilege, mandatory sandbox, credentials never on disk, full audit from day one
- **Restraint by phases** — only the minimal-complete runtime kernel now; governance and heavy distributed infrastructure later, each upgrade justified by real usage data

## Project Info

| | |
|---|---|
| Language | Java (JDK 21) |
| License | Apache 2.0 |
| Ecosystem | oryx-labs |
| Long-term goal | Join the Apache Software Foundation, aiming to become an Apache top-level project |

To become the runtime base of the agent era: every business agent, every cross-team agent, every cross-node agent — running, managed and coordinated on one base, ultimately heeding a single sentence of natural language, delivering complex tasks as a team.
