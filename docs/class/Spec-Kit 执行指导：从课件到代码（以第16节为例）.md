# Spec-Kit 执行指导：从课件到代码

> 本文档说明如何将 OryxOS 课件（`docs/class/` 下各节 MD）作为 Spec-Kit 的输入，驱动逐节开发。

## 1. 概述

OryxOS 采用 **课件驱动开发（Lesson-Driven Development）**：

```
课件 MD（需求+设计+验收标准）
  → Spec-Kit（specify → clarify → plan → tasks）
    → AI 辅助实现（implement）
      → Harness 验收（编译/测试/检查）
```

每一节课件定义了"要做什么、怎么设计、如何验收"，Spec-Kit 将课件拆解为可执行的任务列表，AI 按任务逐步实现。

## 2. 前置条件

- 已安装 [Spec-Kit CLI](https://github.com/oryx-labs/spec-kit)（`specify` 命令）
- 项目根目录存在 `.specify/` 配置（memory/constitution.md 定义不可违背原则）
- 当前分支为 `class-{N}` 格式（如 `class-16`）

## 3. 执行流程

### 步骤 1：准备课件

确保 `docs/class/第{N}节：{标题}.md` 包含：
- **本节目的**：要交付什么能力
- **原理/设计**：怎么设计、为什么这样设计
- **实现计划**：拆解为哪些模块/类
- **Harness（验收标准）**：编译通过、单测覆盖、集成验证

### 步骤 2：Specify（规格化）

```bash
# 从课件提取规格说明
specify init --from docs/class/第16节：Agent Provider 原理解析、实现与代码讲解.md
```

### 步骤 3：Clarify（澄清歧义）

```bash
# 逐条澄清未决问题（多选/填空）
specify clarify
```

### 步骤 4：Plan（生成实现计划）

```bash
# 生成分步实现计划
specify plan
```

### 步骤 5：Tasks（拆解任务）

```bash
# 拆解为可执行任务列表
specify tasks
```

### 步骤 6：Implement（实现）

```bash
# 按任务逐步实现
specify implement
```

### 步骤 7：Harness 验收

```bash
# 编译 + 单元测试 + 集成测试
mvn clean verify
```

## 4. 以第 16 节为例

### 课件输入

第 16 节课件的核心内容：
- **目的**：实现 Provider 抽象层，支持多 LLM 厂商（DeepSeek、通义千问、Kimi 等）
- **设计**：`ProviderService` 接口 + `Map<String, ChatModel>` 显式映射，不靠类型扫描
- **实现**：`oryxos-provider` 模块，包含 `ProviderService`、`ProviderProperties`、自动配置

### Spec-Kit 拆解结果（示例）

```
Task 1: 创建 oryxos-provider 模块（pom.xml、目录结构）
Task 2: 定义 ProviderProperties 配置类
Task 3: 实现 ProviderService 接口与默认实现
Task 4: 实现 ProviderAutoConfiguration（Map<String, ChatModel> 映射）
Task 5: 编写单元测试（ProviderServiceTest）
Task 6: 编写集成测试（MultiProviderIT）
Task 7: 更新 CLAUDE.md 和 TechnicalSolution.md
```

### Harness 验收

- `mvn clean compile` — 编译通过
- `mvn test` — 单测全绿
- `oryxos provider list` — CLI 可列出 Provider
- 管理台 `/admin/` → OS 运行时 → Provider 列表可查看

## 5. 关键原则

1. **课件是唯一真相源** — 实现以课件为准，代码偏离课件时更新课件
2. **每节独立可验收** — 每节完成后有可演示的东西
3. **Harness 前移** — 在 tasks 阶段就写好验收标准，实现完立即自检
4. **宪法不可违背** — `.specify/memory/constitution.md` 的门禁自动检查
