<p align="center">
  <img src="docs/images/logo.svg" alt="OryxOS Logo" width="140">
</p>

<h1 align="center">OryxOS</h1>

<p align="center">
  <strong>Agent Harness OS —— 让一群 Agent 像进程一样跑在操作系统上</strong>
</p>

<p align="center">Java 原生 · 私有部署 · 全链路可审计</p>

<p align="center">
  <a href="https://github.com/oryx-labs/oryxos/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://www.java.com"><img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.x-green.svg" alt="Spring Boot 3.x"></a>
  <a href="https://maven.apache.org"><img src="https://img.shields.io/badge/Maven-3.9+-blue.svg" alt="Maven"></a>
</p>

---

> **你用一句自然语言发布一个任务 → 底座把它拆解 → 组织一支 Agent 团队 → 多个 Agent 分工协作 → 交付一个结果。**

## OryxOS 是什么

OryxOS 是开源的 **Agent Harness OS**（Agent 运行骨架操作系统），装在企业自己的 K8s 或服务器上，作为统一底座运行各种业务 Agent —— 运维助手、客服助手、HR 助手、知识管理助手 —— 共享一套渠道接入、模型路由、工具调用、记忆系统、沙箱执行与审计。

**agent harness** 是套在模型外面、把模型变成能干活的 Agent 的那层脚手架：驱动 Reason → Act → Observe 的循环、它能调用的工具与执行机制、每次调用前组装好的上下文、它积累的记忆、约束它的沙箱、记录它做过什么的审计。裸模型只会生成文本，harness 才让它可靠、安全地「做事」。

**北极星公式：**

```
自然语言(md) + Memory + Tool + MCP(Connector) + Skill + 知识库 + Notify = 一个 Agent
```

一个目录定义一个 Agent，一个底座运行一群 Agent，私有部署，数据不出域。

## 为什么是 Harness OS，而不是又一个 Agent

Agent runtime 让**一个** Agent 跑起来；Agent Harness OS 在其上管理**一群** Agent：多个 Agent 的生命周期、统一的对外渠道与对内接入、统一的记忆、治理与审计。借操作系统类比 —— runtime 像单个进程的执行环境，Harness OS 像管理一群进程、调度资源、提供共享服务的那一层。

更深一层的判断：**让 Agent 在生产环境可靠工作，瓶颈通常不在模型本身，而在 Agent 的运行环境。** 能不能拿到对的上下文、有没有受控的工具、调用能不能被隔离和审计 —— OryxOS 做的就是这个让一群 Agent 可靠运行的底座本身。

## 四道门槛，一次拆掉

Agent 大多停在 demo，卡在四道门槛上：

| 门槛 | OryxOS 的答案 |
|------|--------------|
| 定义一个 Agent 要写代码，最懂业务的人反而做不了 | **一个目录 = 一个 Agent** —— 一份 `AGENT.md` 即定义，不写代码 |
| 云平台要把数据拿走，合规过不去 | **私有部署，数据不出域** —— 装在自己的 K8s / VM / 物理机 |
| 执行是黑盒，没审计没白名单，不敢上生产 | **全链路审计 + 强制沙箱** —— 白名单校验，调用落库，从第一天起 |
| 跑一个容易、跑一群难 | **一群 Agent 的生命周期与治理** —— 并存、调度、REST 治理接口 |

## 一分钟看懂：一次 ReAct 排障

```text
$ java -jar oryxos.jar chat --profile ops
> 线上订单服务最近一小时为什么频繁超时？

● Reason   先查订单服务日志，再核对数据库连接池状态
▸ Act      shell → kubectl logs orders-7f9c --tail 200
◦ Observe  命中 37 次 timeout，伴随连接池等待告警

● Reason   拉取数据库监控，确认连接池水位
▸ Act      http_get → monitor.internal/api/db/pool
◦ Observe  active 98 / max 100，连接池接近打满

✔ 根因：DB 连接池耗尽，导致订单服务大面积超时。
  建议：连接池上限 100 → 200，并治理未释放连接的慢查询。
```

LLM 决定调哪个工具，OryxOS 经沙箱白名单校验后执行、回填结果、继续推理 —— 每一步都有记录，循环行为完全可控。

## 五大核心能力

| 能力 | 说明 |
|------|------|
| 🤖 **对接 LLM** | Provider 抽象统一对接 DeepSeek、通义、Kimi、智谱、Anthropic、OpenAI 与本地推理。Agent 不感知厂商，运行时切换无锁定 |
| 🧠 **ReAct 循环** | 自实现推理引擎：Reason → Act → Observe，直到交付或达到最大迭代。不套外部 Agent 框架，机制完全可控 |
| 💾 **记忆系统** | 会话记忆（SQLite）+ 长期记忆（MEMORY.md）两层，跨对话记住偏好、背景与决策，越用越懂你 |
| 🔧 **工具体系** | 内置 9 个 Tool（文件 / Shell / HTTP / 记忆 / 通知）+ 三档插件接入：零代码（MCP 复用）、轻代码（自写 MCP server）、重代码（`@Tool` 注解） |
| 🌐 **对外服务** | 完整 REST API 暴露所有能力，覆盖会话管理、Agent 调用、Profile / Memory / Tool 查询、系统状态，任何语言可集成 |

## 核心特性

- 🤖 **一个目录 = 一个 Agent** —— 包含 `AGENT.md` 的目录即定义，多 Agent 同实例并存
- ☕ **Java 原生** —— JDK 21 + Spring Boot 3.x，单 JAR 部署，复用现有 Java 运维工具链
- 🔒 **私有可控** —— 数据不出域，不锁任何云
- 🛡️ **安全是地基不是补丁** —— 文件 / 命令 / 域名白名单，强制沙箱，凭证走环境变量不落地，全链路可审计
- 🧠 **自实现核心，可控优先** —— 推理循环自己实现，协议适配复用成熟库
- 🔌 **对接开放标准** —— 工具用 MCP、协作用 A2A、Agent 目录借 Anthropic Agent Skills 形态，不另立协议
- 🌐 **无状态可扩展** —— 实例无状态、状态外置，从架构起为分布式留好路

## 快速开始

**环境要求**：JDK 21+，Maven 3.9+

```bash
git clone https://github.com/oryx-labs/oryxos.git
cd oryxos
mvn clean package -DskipTests
```

**5 分钟体验：**

```bash
# 1. 初始化工作区
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar init

# 2. 创建一个 Agent
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar profile create weather

# 3. 配置模型凭证（环境变量，绝不明文写文件）
export DEEPSEEK_API_KEY=sk-xxx

# 4. 开始对话
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar chat --profile weather
```

**启动 API 服务：**

```bash
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve --port 8080
```

访问 `http://localhost:8080/api/v1/health` 确认服务正常。

## 架构

四层解耦：接入层（CLI / REST / 调度）→ 引擎层（ReActLoop / PromptBuilder / ToolExecutor）→ 能力层（Provider / Memory / Tool+MCP / Sandbox）→ 基础层（Profile / SQLite / Session / 审计）。安全横贯每一层。

![OryxOS Architecture](docs/images/architecture.svg)

```
oryxos/
├── oryxos-core/           # 核心抽象：ReActLoop、Profile、AgentLoader、ContextLoader
├── oryxos-provider/       # LLM Provider 抽象与显式映射
├── oryxos-memory/         # 记忆系统：MemoryService 门面、LongTermMemory
├── oryxos-tool/           # 工具体系：内置 Tool、MCP Client、Sandbox、ToolRegistry
├── oryxos-channel-cli/    # CLI Channel 实现
├── oryxos-web/            # REST API：10 个核心端点
├── oryxos-storage/        # SQLite 持久化层
├── oryxos-cli/            # Picocli 命令行入口（12 个子命令）
├── oryxos-boot/           # Spring Boot 启动模块
├── docs/                  # 设计文档
├── website/               # VitePress 官网
└── scripts/               # 构建与部署脚本
```

**核心 API（10 个端点）：**

| 端点 | 说明 |
|------|------|
| `POST /api/v1/sessions` · `POST /api/v1/sessions/{id}/messages` · `GET /api/v1/sessions/{id}` · `DELETE /api/v1/sessions/{id}` | 会话管理 |
| `POST /api/v1/agents/{name}/invoke` | 无状态调用 Agent |
| `GET /api/v1/profiles` · `GET /api/v1/memory` · `GET /api/v1/tools` | Profile / 记忆 / 工具查询 |
| `GET /api/v1/health` · `GET /api/v1/info` | 系统状态 |

## 路线图

慢就是快，克制且聚焦：先把单机运行时内核做扎实，再在它之上生长出分布式能力。

- **阶段一（当前）单机运行时内核** —— 五大核心能力跑通：配置即 Agent、多 Agent 并存、REST API、对接 MCP
- **阶段二（规划）底座分布式** —— 节点无状态化、状态外置、多副本部署、高可用
- **阶段三（愿景）跨节点 Agent 协作** —— Agent 通信底座、对接 A2A、跨节点发现 / 委托 / 可靠异步协同
- **横向能力（伴随各阶段逐步补齐）** —— 多租户、SSO、完整审计、工具策略、可观测、Web 管理

## 设计原则

- **底座优先于 Agent** —— 最重要的交付不是某个强大的 Agent，而是让任意 Agent 都能可靠运行的环境
- **自实现核心，可控优先** —— 核心推理循环自己实现，底层协议适配复用成熟库，不重复造轮子
- **配置即 Agent** —— Agent 由一份配置定义，而不是由代码写出
- **对接开放标准** —— 工具用 MCP、协作用 A2A、技能用开放格式
- **无状态实例，状态外置** —— 从单机平滑走向分布式的前提
- **安全是地基不是补丁** —— 工具来源受控、最小权限、强制沙箱、凭证不落地、全链路可审计
- **分阶段克制** —— 当前只做运行时内核的最小完备集，每次架构升级都用真实使用数据证明其必要性

## 许可证与社区

[Apache License 2.0](LICENSE) · OryxOS 是 [oryx-labs](https://github.com/oryx-labs) 旗下的开源项目，长期目标是走进 Apache 基金会。欢迎通过 Issue、PR 和 Discussions 参与贡献。

---

<p align="center">
  <strong>让每一家公司，都能用自然语言跑起来自己的 Agent。</strong>
</p>
