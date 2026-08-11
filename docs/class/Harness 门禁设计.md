# OryxOS Harness 门禁设计

> 本文档定义 OryxOS 的 Harness（门禁）体系，确保每节课件开发产出的代码质量符合预期，功能围绕 `docs/TechnicalSolution.md` 的设计交付。

## 1. 什么是 Harness

Harness 是一组**自动化检查与验证规则**，在每节课件开发完成后运行，确保：

- **编译通过** — 代码可编译、可打包
- **测试覆盖** — 核心逻辑有单测/集成测试
- **宪法合规** — 不违反 `.specify/memory/constitution.md`
- **功能正确** — 产出的功能符合课件验收标准
- **回归安全** — 不影响已有功能

## 2. Harness 分层

```
Layer 1: 编译门禁     → mvn compile          (秒级)
Layer 2: 单元测试     → mvn test             (分钟级)
Layer 3: 集成测试     → mvn verify           (分钟级)
Layer 4: 宪法检查     → constitution check    (秒级)
Layer 5: 手工验收     → 按课件 Harness 章节    (手动)
```

## 3. 各层详解

### Layer 1：编译门禁

```bash
mvn clean compile -DskipTests
```

- 所有模块编译通过
- 无 import 错误、类型不匹配
- 无循环依赖

### Layer 2：单元测试

```bash
mvn test
```

- 每个模块的核心类有单元测试
- 测试命名：`{ClassName}Test.java`
- 覆盖：正常路径 + 边界条件 + 异常路径

### Layer 3：集成测试

```bash
mvn verify
```

- 跨模块交互测试（如 Provider → ReAct → Tool）
- 数据库交互测试（SQLite）
- REST API 端点测试

### Layer 4：宪法检查

自动检查以下红线：

| # | 宪法条款 | 检查方式 |
|---|---------|---------|
| 1 | Java 21，不得降版本 | `mvn-enforcer-plugin` 检查 `<java.version>` |
| 2 | ReAct Loop 自实现 | 禁止依赖 Spring AI 的 `ToolExecutionCallback` |
| 3 | 多 Provider 用 `Map<String, ChatModel>` 映射 | 检查 Provider 配置类无 `@Primary` 歧义 |
| 4 | Sandbox 用白名单 | `WhitelistSandbox` 存在且为默认实现 |
| 5 | 审计表即落库 | `LlmCallRepository` / `ToolInvocationRepository` 存在 |
| 6 | 敏感配置只走环境变量 | 扫描 YAML 无硬编码 API key |
| 7 | AGENT.md 一个目录 = 一个 Agent | `AgentLoader` 按目录扫描 |
| 8 | Skill ≠ Tool | Skill 不实现 `OryxTool` 接口 |
| 9 | 接口先行 | Sandbox/Memory/NotifyChannel 有抽象接口 |

### Layer 5：手工验收

每节课件末尾的「如何验收」章节定义本节特有的验收流程。例如：

- 启动服务 → 调用 API → 检查响应
- CLI 命令 → 检查输出
- 管理台页面 → 检查交互

## 4. Spec-Kit 集成

在 Spec-Kit 的 `tasks` 阶段自动注入 Harness 任务：

```yaml
tasks:
  - id: harness-compile
    description: "编译门禁：mvn clean compile"
  - id: harness-test
    description: "单测门禁：mvn test"
  - id: harness-verify
    description: "集成测试：mvn verify"
  - id: harness-constitution
    description: "宪法检查：逐条核对 9 条宪法"
```

## 5. CI/CD 集成（规划）

```yaml
# .github/workflows/harness.yml
name: Harness
on: [push, pull_request]
jobs:
  compile:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: 21 }
      - run: mvn clean compile
  test:
    needs: compile
    runs-on: ubuntu-latest
    steps:
      - run: mvn test
  verify:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - run: mvn verify
```
