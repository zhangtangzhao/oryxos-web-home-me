# Introduction

## What is OryxOS?

**OryxOS** is an enterprise-grade **Agent OS** built on Java 21. It runs on your own K8s or servers as a unified platform, hosting multiple business agents (ops assistant, customer support, HR assistant, knowledge management, etc.) that share a common set of capabilities: channel access, model routing, tool invocation, memory system, and sandbox execution. All data stays within your own infrastructure — no cloud lock-in.

> **You issue a task in natural language → the platform decomposes it → organizes an agent team → multiple agents collaborate → delivers a result.**

## Why OryxOS?

Every company has work that should be handed to agents, but most agent projects stall at four barriers:

1. **Defining an agent requires coding** — domain experts who know the business best are excluded
2. **Cloud platforms demand your data** — compliance won't allow it
3. **Execution is a black box** — no audit trail, no whitelist, no approval workflow; enterprises can't trust it in production
4. **Running one is easy; running many is hard** — nobody offers the "operating system for a team of agents" layer

OryxOS removes all four barriers: **natural language definition, private deployment, full audit trail with sandboxing, and lifecycle management for a team of agents.**

## Five Core Capabilities

| Capability | Description |
|------------|-------------|
| 🤖 **LLM Integration** | Provider abstraction for all major LLMs. Agents don't perceive which vendor they're calling. |
| 🧠 **ReAct Loop** | Self-implemented reasoning engine: Reason → Act → Observe. Fully controllable. |
| 💾 **Memory System** | Session memory + long-term memory. Agents remember across conversations. |
| 🔧 **Tool System** | 9 built-in tools + 3 plugin tiers (zero-code MCP, custom MCP server, @Tool Java Bean). |
| 🌐 **Web Service** | Complete REST API. Integrate from any language that can send HTTP. |

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

## Design Principles

- **Platform first, agent second** — The most important deliverable is not a powerful agent, but an environment where any agent can run reliably.
- **Self-implemented core** — Core reasoning loop built from scratch. Mature libraries reused for protocol adaptation.
- **Configuration = Agent** — An agent is defined by a config file, not by code.
- **Open standards** — MCP for tools, A2A for collaboration, open formats for skills.
- **Security is foundation, not patch** — Controlled tool sources, least privilege, mandatory sandbox, credential isolation, full audit trail.

## License

[Apache License 2.0](https://github.com/oryx-labs/oryxos/blob/main/LICENSE)
