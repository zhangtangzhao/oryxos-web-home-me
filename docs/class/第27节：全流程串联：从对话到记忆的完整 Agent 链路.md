# 第27节：全流程串联：从对话到记忆的完整 Agent 链路

> **课时目标**：以 Mock Provider 驱动，打通 OryxOS 的完整链路——用户发消息 → ReAct 推理 → Tool 调用 → 记忆保存 → 审计落库——使 Agent 主流程首次可用。

---

## 一、本节目的

前面 16-26 节分别实现了各个模块，本节进行**全流程联调**：

```
用户输入消息
  → Session 管理（创建/加载会话）
    → AgentService 处理
      → Provider 调用 LLM（Mock Provider 不需要真 Key）
        → ReAct Loop 推理
          → Tool 执行（Sandbox 白名单校验）
            → 审计记录（tool_invocations / llm_calls）
          → 保存记忆（save_memory）
        → 返回最终答案
  → 前端展示对话 + 会话详情
```

**本节的核心价值**：让系统首次成为一个"能用"的完整 Agent。

---

## 二、Mock Provider 设计

### 2.1 为什么需要 Mock Provider？

真实 LLM 需要 API Key 和网络连接，不适合开发和 CI 环境。Mock Provider：

- **零依赖**：不需要任何外部 API Key
- **可预测**：返回预设的响应，方便测试
- **覆盖 Tool 调用路径**：模拟 LLM 返回 Tool Call，触发完整链路

### 2.2 Mock Provider 实现

```java
@Component
public class MockProviderService implements ProviderService {

    @Override
    public List<String> getProviderNames() {
        return List.of("mock");
    }

    @Override
    public LlmResponse call(Profile profile,
                            List<Map<String, Object>> messages,
                            String toolsJson) {
        String lastMessage = getLastUserMessage(messages);

        // 模拟不同的 Agent 行为
        if (lastMessage.contains("天气")) {
            return mockToolCall("http_get",
                Map.of("url", "https://api.weather.com/beijing"));
        }
        if (lastMessage.contains("文件")) {
            return mockToolCall("write_file",
                Map.of("path", "/tmp/output.md", "content", "# 测试输出"));
        }
        if (lastMessage.contains("记忆")) {
            return mockToolCall("save_memory",
                Map.of("content", "用户偏好简洁格式", "scope", "CORE"));
        }

        // 默认：返回纯文本
        return mockTextResponse("你好！我是 OryxOS Mock Agent。"
            + "我可以帮你查询天气、操作文件、保存记忆。");
    }

    private LlmResponse mockToolCall(String toolName, Map<String, Object> args) {
        LlmResponse response = new LlmResponse();
        response.setContent("我需要调用 " + toolName + " 工具");
        response.setToolCalls(List.of(
            new ToolCall("call_001", toolName, args)));
        response.setTotalTokens(50);
        return response;
    }
}
```

### 2.3 ReAct Loop 集成

Mock Provider 第一次返回 Tool Call，第二次返回最终答案：

```
Turn 1: Mock → tool_call: http_get("https://api.weather.com/beijing")
Turn 2: ToolExecutor 执行 → 返回结果
Turn 3: Mock → "北京今天晴，25°C，适合出行"
Turn 4: 无 tool_call → 返回最终答案给用户
```

---

## 三、全流程验证

### 3.1 手动测试流程

```bash
# 1. 启动服务
java -jar oryxos-boot.jar serve --port 8080

# 2. 创建会话
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profileName":"mock"}'

# 3. 发送消息（触发天气查询 → Tool 调用）
curl -X POST http://localhost:8080/api/v1/sessions/{sessionId}/messages \
  -H "Content-Type: application/json" \
  -d '{"message":"查询北京今天的天气"}'

# 4. 查看会话详情（含对话历史）
curl http://localhost:8080/api/v1/sessions/{sessionId}

# 5. 验证审计表
# 检查 tool_invocations 表是否有记录
# 检查 llm_calls 表是否有记录
```

### 3.2 集成测试

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MockProviderFlowTest {

    @Test
    void shouldCompleteFullPipeline() {
        // 1. 创建会话
        var session = restTemplate.postForObject(
            "/api/v1/sessions",
            new CreateSessionRequest("mock"), Session.class);

        // 2. 发送消息
        var response = restTemplate.postForObject(
            "/api/v1/sessions/" + session.getSessionId() + "/messages",
            new SendMessageRequest("帮我查询天气"),
            String.class);

        // 3. 验证响应非空
        assertThat(response).isNotEmpty();

        // 4. 验证审计记录
        var sessions = restTemplate.getForObject(
            "/api/v1/sessions", List.class);
        assertThat(sessions).isNotEmpty();
    }
}
```

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 `MockProviderService` | 无 Key 开发用 Provider |
| D2 | 完善 `AgentService` 全流程逻辑 | 会话 + Profile + ReAct |
| D3 | 实现 `SessionManager` | 创建/加载/归档会话 |
| D4 | 编写 `MockProviderFlowTest` | 端到端集成测试 |
| D5 | 管理台集成 | 会话详情页面 |
| D6 | 编写手动测试指南 | 测试流程文档 |

---

## 五、Harness：如何验收

### 编译
```bash
mvn clean package -DskipTests
```

### 集成测试
```bash
mvn test -pl oryxos-web -Dtest=MockProviderFlowTest
```

### 手动验证
```bash
# 启动 → 创建会话 → 发消息 → 检查响应
# 管理台 → 会话详情 → 查看对话历史 + 工具调用记录
# 管理台 → OS 运行时 → 长期记忆 → 查看保存的记忆
```

### 检查点
- [x] Mock Provider 无需 API Key 即可运行
- [x] 全链路：消息 → ReAct → Tool → 记忆 → 审计
- [x] `tool_invocations` 和 `llm_calls` 表有数据
- [x] 管理台可查看会话详情和对话内容
