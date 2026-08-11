# OryxOS 介绍

## 什么是 OryxOS？

**OryxOS** 是基于 Java 21 实现的面向企业场景的 **Agent OS**。它装在企业自己的 K8s 或服务器上，作为统一底座运行各种业务 Agent（运维助手、客服助手、HR 助手、知识管理助手等），共享一套渠道接入、模型路由、工具调用、记忆系统、沙箱执行能力。数据完全留在企业自己的基础设施，不锁任何云生态。

> **你用一句自然语言发布一个任务 → 底座把它拆解 → 组织一支 Agent 团队 → 多个 Agent 分工协作 → 交付一个结果。**

## 为什么需要 OryxOS？

每家公司都有该交给 Agent 的活，但 Agent 大多还停在 demo，卡在四道门槛上：

1. **定义一个 Agent 要写代码** — 最懂业务的人反而做不了
2. **云平台要把数据拿走** — 合规过不去
3. **执行是黑盒** — 没审计、没白名单、没审批，企业不敢上生产
4. **跑一个容易、跑一群难** — 没有人把「一群 Agent 的操作系统」这一层交给你

OryxOS 一次拆掉这四道门槛：**自然语言定义、私有部署、全链路审计加沙箱、以及为一整队 Agent 准备的生命周期与治理。**

## 五大核心能力

| 能力 | 说明 |
|------|------|
| 🤖 **对接 LLM** | Provider 抽象统一对接主流大模型，Agent 不感知具体厂商，运行时切换无锁定 |
| 🧠 **ReAct 循环** | 自实现推理引擎：Reason → Act → Observe，循环行为完全可控 |
| 💾 **记忆系统** | 会话记忆 + 长期记忆两层，跨对话保留用户偏好、项目背景、关键决策 |
| 🔧 **工具体系** | 内置 9 个 Tool + Plugin Tool 三档接入（零代码 / 轻代码 / 重代码） |
| 🌐 **Web Service** | 完整 REST API 对外暴露所有能力，任何语言都能接入 |

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.9+

### 构建

```bash
git clone https://github.com/oryx-labs/oryxos.git
cd oryxos
mvn clean package -DskipTests
```

### 5 分钟快速体验

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

## 设计原则

- **底座优先于 Agent** — 最重要的交付不是某个强大的 Agent，而是让任意 Agent 都能可靠运行的环境
- **自实现核心，可控优先** — 核心推理循环自己实现，底层模型协议适配复用成熟库
- **配置即 Agent** — 一个 Agent 由一份 `AGENT.md` 定义，而不是由代码写出
- **对接开放标准** — 工具用 MCP、协作用 A2A、技能用开放格式
- **安全是地基不是补丁** — 工具来源受控、最小权限、强制沙箱、凭证不落地、全链路可审计

## 许可证

[Apache License 2.0](https://github.com/oryx-labs/oryxos/blob/main/LICENSE)
