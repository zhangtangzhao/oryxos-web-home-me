# 第19节：Notify 模块：原理解析、实现与代码讲解

> **课时目标**：理解 Agent 通知推送机制，完成 `oryxos-tool` 中 Notify 模块的实现，使 Agent 执行结果能推送到飞书、企业微信、钉钉等渠道。

---

## 一、本节目的

Agent 执行完任务后需要"汇报"——把结果推送到人所在的沟通渠道。本节实现 OryxOS 的通知模块：

- **全局通知渠道注册表**（CRUD + SQLite 持久化）
- **多渠道适配**：Webhook、飞书、企业微信、钉钉
- **Agent 通过自然语言引用渠道**（"发到 Lark"而非"发到 channel-id-xxx"）
- **管理台可管**：管理员可增删查改通知渠道

---

## 二、原理：Notify 在 Agent OS 中的角色

### 2.1 为什么需要 Notify 模块？

```
Agent 执行任务 → 产出结果 → 需要通知人
                              ├── 飞书群消息
                              ├── 企业微信群机器人
                              ├── 钉钉机器人
                              └── 通用 Webhook（Slack、Discord 等）
```

Agent 不是自言自语——它需要把结果投递到人的协作工具里。

### 2.2 设计原则

1. **全局注册表**：通知渠道不属于某个 Agent，而是全局可用
2. **按名引用**：Agent 在 AGENT.md 中用自然语言指定（"通知到 Lark"）
3. **适配器模式**：每个渠道一个 Adapter，统一接口
4. **持久化**：渠道配置存 SQLite，重启不丢

---

## 三、设计与架构

### 3.1 NotifyChannel 抽象

```java
public interface NotifyChannelAdapter {
    /** 渠道类型标识 */
    String getType();  // "webhook", "feishu", "wecom", "dingtalk"

    /** 发送消息 */
    boolean send(String target, String title, String content);
}
```

### 3.2 渠道注册表（SQLite）

```sql
CREATE TABLE notify_channels (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,       -- "team-lark", "ops-wecom"
    type        TEXT NOT NULL,       -- "feishu", "webhook", "wecom", "dingtalk"
    webhook_url TEXT NOT NULL,       -- 渠道的 webhook 地址
    description TEXT,
    enabled     INTEGER DEFAULT 1,
    created_at  TEXT,
    updated_at  TEXT
);
```

### 3.3 架构图

```
┌─────────────────────────────────────────────┐
│              Agent (AGENT.md)                 │
│  "执行后通知到 team-lark"                     │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│           NotifyTool (notify)                 │
│  解析 Agent 意图 → 查找渠道 → 调用适配器     │
└──────────────────┬──────────────────────────┘
                   │
┌──────────────────▼──────────────────────────┐
│        NotifyChannelAdapter 适配器层          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ Feishu   │ │ WeCom    │ │ DingTalk │     │
│  │ Adapter  │ │ Adapter  │ │ Adapter  │     │
│  └─────┬────┘ └────┬─────┘ └────┬─────┘     │
│        │           │            │            │
│  ┌─────▼───────────▼────────────▼─────┐      │
│  │     NotifyChannel 注册表 (SQLite)    │      │
│  └────────────────────────────────────┘      │
└─────────────────────────────────────────────┘
```

### 3.4 REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/notify-channels` | GET | 列出所有渠道 |
| `/api/v1/notify-channels` | POST | 创建渠道 |
| `/api/v1/notify-channels/{id}` | PUT | 更新渠道 |
| `/api/v1/notify-channels/{id}` | DELETE | 删除渠道 |

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 定义 `NotifyChannelAdapter` 接口 | core 模块接口 |
| D2 | 实现 `FeishuAdapter` + `WebhookAdapter` | 两个渠道适配器 |
| D3 | 实现 `NotifyChannel` JPA Entity + Repository | SQLite 持久化 |
| D4 | 实现 `NotifyChannelApiController` | REST CRUD |
| D5 | 实现 `NotifyTool`（notify） | Agent 可调用 |
| D6 | 管理台集成 | 侧边栏 + 列表 + 编辑 |
| D7 | 编写测试 | 各渠道连通性测试 |

---

## 五、代码讲解

### 5.1 FeishuAdapter

```java
@Component
public class FeishuAdapter implements NotifyChannelAdapter {
    @Override
    public String getType() { return "feishu"; }

    @Override
    public boolean send(String webhookUrl, String title, String content) {
        Map<String, Object> body = Map.of(
            "msg_type", "interactive",
            "card", Map.of(
                "header", Map.of("title", Map.of("content", title)),
                "elements", List.of(Map.of(
                    "tag", "markdown",
                    "content", content
                ))
            )
        );
        // POST to webhook URL
        return httpPost(webhookUrl, body);
    }
}
```

### 5.2 NotifyTool

```java
@Component
public class NotifyTool implements OryxTool {
    private final NotifyChannelRepository channelRepo;
    private final Map<String, NotifyChannelAdapter> adapters;

    @Override
    public String getName() { return "notify"; }

    @Override
    public ToolResult execute(JsonNode input) {
        String channelName = input.get("channel").asText(); // "team-lark"
        String message = input.get("message").asText();

        NotifyChannel channel = channelRepo.findByName(channelName)
            .orElseThrow(() -> new RuntimeException("Channel not found: " + channelName));

        NotifyChannelAdapter adapter = adapters.get(channel.getType());
        boolean ok = adapter.send(channel.getWebhookUrl(), "Agent 通知", message);
        return ok ? ToolResult.success("通知已发送")
                  : ToolResult.failure("发送失败", true);
    }
}
```

### 5.3 AGENT.md 中的通知引用

```yaml
# 旧方式（复杂、易出错）
notify_channels:
  - id: "lark-xxx"
    type: feishu

# 新方式（自然语言）
# AGENT.md 中：
# "每天早上 8 点查询天气，结果通知到 team-lark"
```

---

## 六、Harness：如何验收

### 编译
```bash
mvn clean compile
```

### 单元测试
```java
@Test void feishuAdapter_shouldFormatInteractiveCard() { ... }
@Test void notifyTool_shouldFindChannelByName() { ... }
@Test void notifyTool_shouldThrowWhenChannelNotFound() { ... }
```

### 集成测试
```bash
# 1. 创建通知渠道
curl -X POST http://localhost:8080/api/v1/notify-channels \
  -H "Content-Type: application/json" \
  -d '{"name":"test-lark","type":"feishu","webhookUrl":"https://open.feishu.cn/xxx"}'

# 2. 让 Agent 发通知
curl -X POST http://localhost:8080/api/v1/agents/daily-weather/invoke \
  -H "Content-Type: application/json" \
  -d '{"message":"查询北京天气并通知到 test-lark"}'
```

### 宪法检查
- [x] NotifyChannel 接口先行（宪法 #9）
- [x] Webhook URL 不从代码硬编码
