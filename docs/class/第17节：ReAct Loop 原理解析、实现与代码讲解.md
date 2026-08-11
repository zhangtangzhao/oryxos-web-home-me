# 第17节：ReAct Loop 原理解析、实现与代码讲解

> **课时目标**：理解 ReAct 推理循环的原理，完成 OryxOS 自实现 ReAct Loop，使 Agent 具备"思考→行动→观察→再思考"的自主推理能力。

---

## 一、本节目的

ReAct（Reasoning + Acting）是 Agent 的核心引擎。本节实现 OryxOS 自己的 ReAct 循环：

```
用户消息 → 组装 Prompt → 调用 LLM → 解析响应
  ├─ 无 Tool 调用 → 返回最终答案
  └─ 有 Tool 调用 → 执行 Tool → 结果回填 → 继续循环
```

**核心原则：自实现，不依赖 Spring AI 的自动 Tool 执行。** Spring AI 只用于 LLM 协议转换和 `@Tool` 的 schema 生成。

---

## 二、原理：ReAct 范式

### 2.1 为什么需要 ReAct？

LLM 单独使用时有两个根本限制：
1. **知识截止日期** — 不知道训练后的信息
2. **无行动能力** — 不能查数据库、调 API、读文件

ReAct 解决这个问题：让 LLM 在"思考"和"行动"之间交替，用工具弥补 LLM 的短板。

### 2.2 ReAct 循环流程

```
          ┌──────────────────────┐
          │   用户消息输入        │
          └──────────┬───────────┘
                     ▼
          ┌──────────────────────┐
          │  组装 System Prompt   │
          │  + 对话历史           │
          │  + 可用 Tool 列表     │
          └──────────┬───────────┘
                     ▼
          ┌──────────────────────┐
          │   调用 LLM Provider   │
          └──────────┬───────────┘
                     ▼
          ┌──────────────────────┐
          │  解析 LLM 响应        │
          └──────┬───────┬───────┘
                 │       │
          无 Tool │       │ 有 Tool 调用
                 ▼       ▼
          ┌─────────┐  ┌─────────────────┐
          │返回最终 │  │ 执行 Tool        │
          │答案     │  │ → 校验 Sandbox   │
          └─────────┘  │ → 写入审计表     │
                       │ → 结果追加到历史 │
                       └────────┬────────┘
                                │
                                ▼
                       回到"调用 LLM"步骤
                       （最多 N 次迭代）
```

### 2.3 停止条件

循环在以下任一条件满足时终止：
1. LLM 返回的响应中不包含 Tool 调用
2. 达到最大迭代次数（默认 10，可在 Profile 中配置）
3. Sandbox 拒绝 Tool 调用（安全拦截）

---

## 三、设计与架构

### 3.1 核心类

```
oryxos-core/
├── ReActLoop.java          # 核心循环控制器
├── PromptBuilder.java      # Prompt 组装器
├── ToolExecutor.java       # Tool 执行器
└── AgentService.java       # Agent 服务门面（已存在接口）
```

### 3.2 ReActLoop 接口设计

```java
public interface ReActLoop {
    /**
     * 执行一次完整的 ReAct 循环
     * @param session    当前会话（含对话历史）
     * @param profile    运行时配置（provider、最大迭代数等）
     * @return 最终的 LLM 响应文本
     */
    String execute(Session session, Profile profile);
}
```

### 3.3 PromptBuilder 职责

```
PromptBuilder.assemble(session, profile, tools)
  ├── System Prompt    ← 来自 AGENT.md + Bootstrap 文件
  ├── Memory Context   ← 长期记忆（相关片段）
  ├── Tool 列表        ← 可用工具清单（名称 + 描述 + 参数 schema）
  ├── 对话历史         ← Session 中已存储的消息
  └── 用户最新消息     ← 追加到末尾
```

### 3.4 ToolExecutor 职责

```
ToolExecutor.execute(toolCall, sandbox)
  ├── 1. 查找 Tool（从 ToolRegistry 按名获取）
  ├── 2. 校验参数（参数类型 + 必填）
  ├── 3. Sandbox 检查（路径/命令/域名白名单）
  ├── 4. 执行 Tool
  ├── 5. 写入审计表（tool_invocations 表）
  └── 6. 返回 ToolResult
```

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 完善 `PromptBuilder` 接口与实现 | Prompt 组装逻辑 |
| D2 | 完善 `ToolExecutor` 接口与实现 | Tool 执行+审计 |
| D3 | 实现 `ReActLoop` 核心循环 | 循环控制逻辑 |
| D4 | 实现 `AgentService` | 对外统一门面 |
| D5 | 编写单元测试 | ReActLoopTest、ToolExecutorTest |
| D6 | CLI 集成 | `oryxos chat` 端到端可用 |
| D7 | 宪法合规检查 | 确认未引入 Spring AI 自动执行 |

---

## 五、代码讲解

### 5.1 ReActLoop 实现

```java
public class DefaultReActLoop implements ReActLoop {

    private final ProviderService providerService;
    private final PromptBuilder promptBuilder;
    private final ToolExecutor toolExecutor;
    private final ToolRegistry toolRegistry;
    private final Sandbox sandbox;
    private final LlmCallRepository llmCallRepo;

    @Override
    public String execute(Session session, Profile profile) {
        int maxIterations = profile.getMaxIterations() != null 
            ? profile.getMaxIterations() : 10;
        ChatModel chatModel = providerService.getChatModel(
            profile.getProvider());

        for (int i = 0; i < maxIterations; i++) {
            // 1. 组装 Prompt
            Prompt prompt = promptBuilder.assemble(
                session, profile, toolRegistry.listTools());

            // 2. 调用 LLM
            long start = System.currentTimeMillis();
            ChatResponse response = chatModel.call(prompt);
            long duration = System.currentTimeMillis() - start;

            // 3. 审计记录
            llmCallRepo.save(new LlmCallEntity(
                session.getSessionId(), profile.getProvider(),
                response, duration));

            // 4. 解析 Tool 调用
            List<ToolCall> toolCalls = extractToolCalls(response);
            if (toolCalls.isEmpty()) {
                // 无 Tool 调用 → 返回最终答案
                return response.getResult().getOutput().getContent();
            }

            // 5. 执行 Tool
            for (ToolCall tc : toolCalls) {
                ToolResult result = toolExecutor.execute(tc, sandbox);
                // 结果追加到对话历史
                session.addMessage(new Message("tool", 
                    tc.name() + " → " + result.getOutput()));
            }
        }

        throw new MaxIterationsExceededException(
            "ReAct loop reached max iterations: " + maxIterations);
    }
}
```

### 5.2 PromptBuilder 实现

```java
public class DefaultPromptBuilder implements PromptBuilder {

    private final ContextLoader contextLoader;
    private final MemoryService memoryService;

    @Override
    public Prompt assemble(Session session, Profile profile, 
                           List<ToolInfo> tools) {
        StringBuilder sb = new StringBuilder();

        // 1. System Prompt
        sb.append(contextLoader.loadSystemPrompt(profile));
        sb.append("\n\n");

        // 2. 长期记忆（相关片段）
        List<Memory> memories = memoryService.recall(
            session.getLastUserMessage(), 5);
        if (!memories.isEmpty()) {
            sb.append("## 相关记忆\n");
            memories.forEach(m -> sb.append("- ").append(m.getContent()).append("\n"));
            sb.append("\n");
        }

        // 3. 可用工具
        sb.append("## 可用工具\n");
        tools.forEach(t -> sb.append(formatTool(t)));
        sb.append("\n");

        // 4. 对话历史 + 用户消息
        return new Prompt(new org.springframework.ai.chat.messages.UserMessage(
            sb.toString() + session.formatHistory()));
    }
}
```

### 5.3 与 Spring AI 的边界

```java
// ✅ OryxOS 这样做：
//    只用 Spring AI 的 ChatModel.call(Prompt) 做协议转换
//    Tool 调用由 OryxOS 自己的 ReActLoop 控制

// ❌ 绝不用 Spring AI 的自动 Tool 执行：
//    chatModel.call(prompt, tools)  ← 这会导致 Tool 被调两次！
```

---

## 六、Harness：如何验收

### Layer 1：编译门禁
```bash
mvn clean compile -pl oryxos-core
```

### Layer 2：单元测试
```java
@Test
void shouldReturnAnswerWhenNoToolCall() {
    // Mock LLM 返回无 tool call 的响应
    String answer = reActLoop.execute(session, profile);
    assertThat(answer).isEqualTo("Hello, world!");
}

@Test
void shouldExecuteToolAndContinue() {
    // Mock LLM 第一次返回 tool call，第二次返回最终答案
    String answer = reActLoop.execute(session, profile);
    verify(toolExecutor).execute(any(), any());
}

@Test
void shouldStopAtMaxIterations() {
    // Mock LLM 始终返回 tool call
    assertThrows(MaxIterationsExceededException.class, 
        () -> reActLoop.execute(session, profile));
}
```

### Layer 3：集成测试
```bash
# 用 Mock Provider 测试完整链路
java -jar oryxos-boot.jar chat --profile mock
# 输入任意消息，验证 Agent 返回响应
```

### Layer 4：宪法检查
- [x] ReAct Loop 自实现，未依赖 Spring AI 自动执行（宪法 #2）
- [x] 每次 LLM 调用写入 `llm_calls` 表（宪法 #5）
- [x] Tool 调用经 Sandbox 校验（宪法 #4）

---

## 七、常见问题

**Q：为什么不用 LangChain/LlamaIndex？**
A：OryxOS 是 Java 技术栈，LangChain 主要是 Python。更重要的是，自实现意味着完全掌控循环行为，可以定制任何环节。

**Q：最大迭代次数设多少合适？**
A：默认 10 次。大多数任务在 3-5 次内完成。如果 Agent 频繁触达上限，说明 Tool 返回的信息不够精准。

**Q：多个 Tool 调用怎么处理？**
A：LLM 可能一次返回多个 Tool 调用（并行或串行）。OryxOS 按顺序执行，每个 Tool 的结果都追加到对话历史后再继续。
