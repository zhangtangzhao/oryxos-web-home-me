# 第26节：Web Service 原理解析、实现与代码讲解

> **课时目标**：实现 OryxOS 的 REST API 层，提供完整的 HTTP 接口覆盖会话管理、Agent 调用、系统状态等核心能力，并实现管理控制台。

---

## 一、本节目的

OryxOS 作为 Agent OS，需要向外部系统暴露完整的 HTTP API。本节实现：

1. **10+ 个 REST 端点**：覆盖会话管理、Agent 调用、Profile/Memory/Tool/Provider 查询
2. **统一响应格式**：`ApiResponse<T>`（code + message + data）
3. **管理控制台**：基于 Vue 3 + Vite 的 Web UI，管理 Agent、会话、Provider、Tool 等

---

## 二、API 设计

### 2.1 端点清单

| 类别 | 方法 | 端点 | 说明 |
|------|------|------|------|
| 会话 | POST | `/api/v1/sessions` | 创建会话 |
| 会话 | POST | `/api/v1/sessions/{id}/messages` | 发送消息 |
| 会话 | GET | `/api/v1/sessions/{id}` | 查看会话详情 |
| 会话 | GET | `/api/v1/sessions` | 列出所有会话 |
| 会话 | DELETE | `/api/v1/sessions/{id}` | 归档会话 |
| Agent | POST | `/api/v1/agents/{name}/invoke` | 无状态调用 Agent |
| Agent | POST | `/api/v1/agents` | 创建 Agent |
| Agent | GET | `/api/v1/agents` | 列出所有 Agent |
| Agent | DELETE | `/api/v1/agents/{name}` | 删除 Agent |
| Provider | GET | `/api/v1/providers` | 列出 Provider |
| Tool | GET | `/api/v1/tools` | 列出可用 Tool |
| Memory | GET | `/api/v1/memory` | 查询长期记忆 |
| Skill | GET/POST/PUT/DELETE | `/api/v1/skills` | Skill CRUD |
| Sandbox | GET/POST/DELETE | `/api/v1/sandbox/rules` | 白名单管理 |
| Notify | GET/POST/PUT/DELETE | `/api/v1/notify-channels` | 通知渠道管理 |
| 系统 | GET | `/api/v1/health` | 健康检查 |
| 系统 | GET | `/api/v1/info` | 运行信息 |

### 2.2 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

错误响应：
```json
{
  "code": 400,
  "message": "参数校验失败：缺少必填字段 'name'",
  "data": null
}
```

### 2.3 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SandboxViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSandbox(SandboxViolationException e) {
        return ResponseEntity.status(403).body(
            ApiResponse.error(403, e.getMessage()));
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(AgentNotFoundException e) {
        return ResponseEntity.status(404).body(
            ApiResponse.error(404, e.getMessage()));
    }
}
```

---

## 三、管理控制台

### 3.1 技术栈

```
前端：Vue 3 + Vite + Vue Router + marked (Markdown 渲染)
构建：Vite → 产出静态文件 → Spring Boot 静态资源服务
开发：npm run dev (端口 5173) → 代理到 8080
```

### 3.2 页面结构

```
/admin/
├── Overview         # 概览：系统信息、Agent 数量统计
├── Agent 列表       # 所有 Agent，含创建/详情/触发按钮
├── Agent 详情       # 基本信息 + 工作区 + 输出 + 会话 + 执行历史
├── 定时任务         # 所有定时任务列表 + 执行记录
├── OS 运行时
│   ├── Provider 列表
│   ├── Tool 列表
│   ├── SandBox 列表
│   ├── MCP 管理
│   ├── Skill 列表
│   ├── 长期记忆
│   └── 会话列表
└── 会话详情         # 对话内容（Markdown 渲染 + 折叠工具调用）
```

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 `ApiResponse<T>` 统一响应类 | 标准信封 |
| D2 | 实现 6 个 ApiController | 会话/Agent/Profile/Memory/Tool/系统 |
| D3 | 实现 `GlobalExceptionHandler` | 统一异常处理 |
| D4 | 初始化管理台前端项目 | Vue 3 + Vite + Router |
| D5 | 实现管理台各页面 | 列表/详情/CRUD |
| D6 | 前后端联调 | Proxy 配置 + API 集成 |
| D7 | 编写集成测试 | REST API 测试 |

---

## 五、代码讲解

### 5.1 ApiResponse

```java
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
```

### 5.2 SessionApiController

```java
@RestController
@RequestMapping("/api/v1/sessions")
public class SessionApiController {

    @PostMapping
    public ApiResponse<Session> createSession(@RequestBody CreateSessionRequest req) {
        Session session = sessionManager.create(req.getProfileName());
        return ApiResponse.success(session);
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<String> sendMessage(
            @PathVariable String id,
            @RequestBody SendMessageRequest req) {
        Session session = sessionManager.get(id);
        String response = agentService.process(session, req.getMessage());
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<Session> getSession(@PathVariable String id) {
        return ApiResponse.success(sessionManager.get(id));
    }

    @GetMapping
    public ApiResponse<List<SessionSummary>> listSessions() {
        return ApiResponse.success(sessionManager.listRecent());
    }
}
```

### 5.3 管理台前端关键结构

```
website/src/
├── App.vue
├── main.js
├── router/index.js
├── views/
│   ├── Overview.vue
│   ├── AgentList.vue
│   ├── AgentDetail.vue
│   ├── AgentCreate.vue
│   ├── ScheduledTasks.vue
│   ├── SessionDetail.vue
│   └── runtime/
│       ├── ProviderList.vue
│       ├── ToolList.vue
│       ├── SandboxList.vue
│       ├── McpManagement.vue
│       ├── SkillList.vue
│       ├── MemoryList.vue
│       └── SessionList.vue
└── components/
    ├── Sidebar.vue
    ├── FileTree.vue
    └── MarkdownViewer.vue
```

---

## 六、Harness：如何验收

### 编译 + 启动
```bash
mvn clean package -DskipTests
java -jar oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar serve --port 8080
```

### API 测试
```bash
# 健康检查
curl http://localhost:8080/api/v1/health

# 创建会话
curl -X POST http://localhost:8080/api/v1/sessions \
  -H "Content-Type: application/json" \
  -d '{"profileName":"default"}'

# 列出 Provider
curl http://localhost:8080/api/v1/providers

# 列出 Tool
curl http://localhost:8080/api/v1/tools
```

### 管理台测试
```bash
cd website && npm install && npm run dev
# 打开 http://localhost:5173/admin/
# 验证侧边栏导航、各页面数据加载
```

### 宪法检查
- [x] REST API 统一 `{code, message, data}` 格式
- [x] 管理台不跳过 Sandbox（白名单检查通过 API 生效）
