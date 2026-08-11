# 第18节：Tool 体系 原理解析、实现与代码讲解

> **课时目标**：理解 Agent Tool 体系的设计原理，完成 `oryxos-tool` 模块核心实现，使 Agent 具备文件操作、Shell 执行、HTTP 请求等基础行动能力。

---

## 一、本节目的

Tool 是 Agent 的"手"——让 LLM 不仅会"说"，还会"做"。本节建立 OryxOS 的工具体系：

- **9 个内置 Tool**：覆盖文件、Shell、HTTP、记忆、通知五大类
- **Tool 注册机制**：`ToolRegistry` 统一管理，按 Agent Profile 筛选
- **Sandbox 白名单**：所有 Tool 调用经 `WhitelistSandbox` 安全校验
- **审计追踪**：每次 Tool 调用写入 `tool_invocations` 表

---

## 二、原理：Agent Tool 的工作机制

### 2.1 Function Calling 协议

LLM 的 Function Calling 是 Tool 调用的基础：

```
1. OryxOS 把可用 Tool 列表（名称 + 描述 + JSON Schema）嵌入 System Prompt
2. LLM 分析用户意图，决定要不要调 Tool、调哪个
3. LLM 返回 Tool 名称和参数（JSON）
4. OryxOS 执行 Tool，把结果追加回对话历史
5. LLM 基于 Tool 结果生成最终回复
```

### 2.2 OryxTool 统一抽象

所有 Tool——无论是内置、MCP 导入、还是 Java `@Tool` 注解——都实现同一个接口：

```java
public interface OryxTool {
    String getName();              // Tool 唯一名称，供 LLM 调用
    String getDescription();       // 人类可读描述
    JsonNode getInputSchema();     // 参数 JSON Schema
    ToolResult execute(JsonNode input);  // 执行 Tool
}
```

---

## 三、设计与架构

### 3.1 Tool 分类

```
OryxOS 内置 Tool（9 个）
├── 文件操作（路径白名单）
│   ├── read_file     # 读取文件内容
│   ├── write_file    # 写入文件
│   └── list_dir      # 列出目录
├── Shell 操作（命令白名单 + 超时）
│   └── shell         # 执行 shell 命令
├── HTTP 操作（域名白名单）
│   ├── http_get      # GET 请求
│   └── http_post     # POST 请求
├── 记忆操作
│   ├── save_memory   # 保存长期记忆
│   └── recall_memory # 查询长期记忆
└── 通知操作
    └── notify        # 推送通知（Webhook / 飞书 / 钉钉）
```

### 3.2 ToolRegistry 注册机制

```java
public interface ToolRegistry {
    void register(OryxTool tool);                    // 注册单个 Tool
    OryxTool get(String name);                       // 按名获取
    List<OryxTool> listAll();                        // 列出全部
    List<OryxTool> listForAgent(List<String> names); // 按 Agent Profile 筛选
}
```

当前实现：`InMemoryToolRegistry`（基于 `ConcurrentHashMap`，支持 Virtual Thread 并发）。

### 3.3 ToolExecutor 执行流程

```
ToolExecutor.execute(toolCall)
  ├── 1. toolRegistry.get(toolCall.name)      // 查找 Tool
  ├── 2. 参数校验（JSON Schema 验证）         // 参数类型 + 必填
  ├── 3. sandbox.enforce(type, target)        // 白名单校验
  ├── 4. tool.execute(input)                  // 执行 Tool
  ├── 5. 写入 tool_invocations 审计表         // 持久化审计
  └── 6. 返回 ToolResult                      // 回调到 ReAct Loop
```

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 `ReadFileTool`、`WriteFileTool`、`ListDirTool` | 文件操作 Tool |
| D2 | 实现 `ShellTool`（超时 + 命令白名单） | Shell 执行 Tool |
| D3 | 实现 `HttpGetTool`、`HttpPostTool` | HTTP 请求 Tool |
| D4 | 实现 `ToolExecutor` | 统一执行器 |
| D5 | 完善 `WhitelistSandbox` | 三类白名单校验 |
| D6 | 实现 Tier0/Tier1 扩展工具 | `current_time`、`json_extract`、文件管理等 |
| D7 | 编写测试 + 宪法检查 | ToolExecutorTest、SandboxTest |

---

## 五、代码讲解

### 5.1 ReadFileTool

```java
@Component
public class ReadFileTool implements OryxTool {
    private final Sandbox sandbox;

    @Override
    public String getName() { return "read_file"; }

    @Override
    public String getDescription() {
        return "读取指定路径的文件内容。参数: path (文件路径)";
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String path = input.get("path").asText();
        sandbox.enforce(ActionType.FILE_READ, path);
        String content = Files.readString(Path.of(path));
        return ToolResult.success(content);
    }
}
```

### 5.2 ShellTool（安全关键）

```java
@Component
public class ShellTool implements OryxTool {
    private final Sandbox sandbox;

    @Override
    public String getName() { return "shell"; }

    @Override
    public ToolResult execute(JsonNode input) {
        String command = input.get("command").asText();
        sandbox.enforce(ActionType.SHELL_COMMAND, command);

        // 超时 30 秒
        ProcessBuilder pb = new ProcessBuilder(splitCommand(command));
        Process p = pb.start();
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            return ToolResult.failure("Command timeout (30s)", true);
        }
        String output = new String(p.getInputStream().readAllBytes());
        return ToolResult.success(output);
    }
}
```

### 5.3 WhitelistSandbox 三类检查

```java
public class WhitelistSandbox implements Sandbox {

    private final List<String> allowedPaths;
    private final List<String> allowedCommands;
    private final List<String> allowedDomains;

    @Override
    public void enforce(ActionType type, String target) {
        switch (type) {
            case FILE_READ:
            case FILE_WRITE:
                checkPath(target);    // 路径前缀匹配白名单
                break;
            case SHELL_COMMAND:
                checkCommand(target); // 命令第一词匹配白名单
                break;
            case HTTP_REQUEST:
                checkDomain(target);  // 域名精确/后缀/通配符匹配
                break;
        }
    }

    private void checkDomain(String url) {
        String host = new URL(url).getHost();
        for (String allowed : allowedDomains) {
            if ("*".equals(allowed)) return;             // 全通配
            if (host.equals(allowed)) return;             // 精确匹配
            if (host.endsWith("." + allowed)) return;     // 子域名匹配
        }
        throw new SandboxViolationException(HTTP_REQUEST, url);
    }
}
```

---

## 六、Harness：如何验收

### Layer 1：编译
```bash
mvn clean compile -pl oryxos-tool
```

### Layer 2：单元测试
```java
@Test void readFile_shouldReturnContent() { ... }
@Test void readFile_shouldThrowWhenPathNotInWhitelist() { ... }
@Test void shellCommand_shouldTimeout() { ... }
@Test void httpGet_shouldAllowWhitelistedDomain() { ... }
@Test void httpGet_shouldBlockUnknownDomain() { ... }
```

### Layer 3：集成测试
```bash
java -jar oryxos-boot.jar chat --profile mock
# 让 Agent 执行：读文件 → 调用 http_get → 写结果文件
```

### Layer 4：宪法检查
- [x] Tool 调用经 Sandbox 白名单校验（宪法 #4）
- [x] 每次 Tool 调用写入 `tool_invocations` 表（宪法 #5）
- [x] Skill ≠ Tool — Skill 不进 ToolRegistry（宪法 #8）

---

## 七、扩展：三档 Tool 接入

| 档次 | 方式 | 适用场景 |
|------|------|---------|
| 零代码 | Agent 目录 + MCP Server 复用 | 非 Java 开发者，快速接入 |
| 轻代码 | 自写 MCP Server（任何语言） | 需要自定义逻辑 |
| 重代码 | Java `@Tool` 注解 Spring Bean | 深度集成，需要 JVM 性能 |
