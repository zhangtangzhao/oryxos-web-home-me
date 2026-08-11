# 第28节：定时任务管理子系统：Skill 定义的智能定时 Agent

> **课时目标**：实现 OryxOS 的定时任务管理子系统——Agent 可按 Cron 表达式自动运行，任务定义在 SQLite 持久化，管理台可查看和管理，每次执行记录历史。

---

## 一、本节目的

Agent 不能只等人来调——它需要**自己按时运行**：

```
定时触发（Cron）
  → 创建会话
    → AgentService.process(session, message)
      → ReAct Loop → Tool → Notify → Memory
        → 执行历史记录
          → 管理台可查看
```

本节实现后，可以定义"每天早上 8 点查询天气并通知到飞书"这样的 Agent。

---

## 二、设计

### 2.1 定时任务生命周期

```
┌──────────────────────────────────────────┐
│              AGENT.md 中定义               │
│  schedule:                                │
│    - id: morning-weather                  │
│      cron: "0 0 8 * * *"                  │
│      message: "查询北京天气并通知到 Lark"  │
├──────────────────────────────────────────┤
│              AgentScheduler 启动时加载     │
│  扫描所有 Agent → 读取 schedules → 注册    │
├──────────────────────────────────────────┤
│              Cron 触发时执行               │
│  1. 创建新会话                             │
│  2. AgentService.process(session, message) │
│  3. 异步执行（Virtual Thread）             │
│  4. 记录到 agent_executions 表             │
├──────────────────────────────────────────┤
│              管理台查看                    │
│  - 定时任务列表（名称 / Cron / 上次执行）  │
│  - 执行历史（开始/结束时间 / 状态 / 来源） │
│  - 手动触发按钮                            │
└──────────────────────────────────────────┘
```

### 2.2 数据库表

```sql
-- 定时任务定义（从 AGENT.md 解析后写入）
CREATE TABLE scheduled_tasks (
    id          TEXT PRIMARY KEY,
    agent_name  TEXT NOT NULL,
    task_id     TEXT NOT NULL,     -- AGENT.md 中的 schedule.id
    cron_expr   TEXT NOT NULL,
    message     TEXT NOT NULL,     -- 触发时发送的 Prompt
    zone        TEXT DEFAULT 'Asia/Shanghai',
    enabled     INTEGER DEFAULT 1,
    last_run_at TEXT,
    next_run_at TEXT,
    created_at  TEXT
);

-- 执行历史
CREATE TABLE agent_executions (
    id            TEXT PRIMARY KEY,
    agent_name    TEXT NOT NULL,
    trigger_type  TEXT NOT NULL,   -- SCHEDULED | MANUAL | API
    task_id       TEXT,            -- 关联的定时任务 ID
    session_id    TEXT,
    start_time    TEXT NOT NULL,
    end_time      TEXT,
    duration_ms   INTEGER,
    status        TEXT,            -- RUNNING | COMPLETED | FAILED
    error_message TEXT,
    created_at    TEXT
);
```

---

## 三、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 `ScheduledTaskEntity` + Repository | SQLite 持久化 |
| D2 | 实现 `AgentScheduler` 完整逻辑 | Cron 解析 + 触发 |
| D3 | 实现异步执行 | Virtual Thread 后台跑 |
| D4 | 实现 `AgentExecution` 记录 | 执行历史落库 |
| D5 | 实现 REST API | 定时任务 CRUD |
| D6 | 管理台定时任务页面 | 列表 + 执行历史 |
| D7 | 编写端到端测试 | `ScheduledTaskE2ETest` |

---

## 四、代码讲解

### 4.1 AgentScheduler 核心逻辑

```java
@Component
public class DefaultAgentScheduler {

    private final Map<String, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private final ThreadPoolTaskScheduler taskScheduler;

    @PostConstruct
    void init() {
        // 启动时加载所有 Agent 的定时任务
        List<Profile> agents = agentLoader.scanAll();
        for (Profile agent : agents) {
            for (ScheduleDef schedule : agent.getSchedules()) {
                registerTask(agent, schedule);
            }
        }
    }

    private void registerTask(Profile agent, ScheduleDef schedule) {
        CronTrigger trigger = new CronTrigger(schedule.getCron(),
            TimeZone.getTimeZone(schedule.getZone()));

        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            AgentExecution exec = startExecution(agent, schedule);
            try {
                Session session = sessionManager.create(agent.getName());
                String result = agentService.process(session, schedule.getMessage());
                completeExecution(exec, result);
            } catch (Exception e) {
                failExecution(exec, e);
            }
        }, trigger);

        runningTasks.put(agent.getName() + "/" + schedule.getId(), future);
    }
}
```

### 4.2 异步执行（避免超时）

```java
@Async  // Spring @Async + Virtual Thread
public CompletableFuture<String> invokeAsync(String agentName, String message) {
    Session session = sessionManager.create(agentName);
    String result = agentService.process(session, message);
    return CompletableFuture.completedFuture(result);
}
```

### 4.3 执行历史查询

```java
@RestController
@RequestMapping("/api/v1/scheduled-tasks")
public class ScheduledTaskApiController {

    @GetMapping
    public ApiResponse<List<ScheduledTask>> listTasks() { ... }

    @GetMapping("/{agentName}/executions")
    public ApiResponse<List<AgentExecution>> listExecutions(
            @PathVariable String agentName,
            @RequestParam(defaultValue = "20") int limit) { ... }

    @PostMapping("/{agentName}/trigger")
    public ApiResponse<String> triggerNow(@PathVariable String agentName) {
        // 异步触发，立即返回
        agentScheduler.triggerNow(agentName);
        return ApiResponse.success("任务已触发，正在后台执行");
    }
}
```

---

## 五、Harness：如何验收

### 编译
```bash
mvn clean compile
```

### 集成测试
```java
@Test void scheduledTask_shouldTriggerOnSchedule() { ... }
@Test void execution_shouldRecordStartEndAndStatus() { ... }
@Test void triggerNow_shouldReturnImmediately() { ... }
@Test void failedExecution_shouldRecordError() { ... }
```

### 手动验证
```bash
# 1. 创建含 schedule 的 Agent
mkdir -p .oryxos/agents/test-scheduled
# 写入 AGENT.md（含每分钟执行的 schedule）

# 2. 启动服务，观察日志
# 3. 管理台 → 定时任务 → 查看列表和执行历史
# 4. 点击「立即触发」，验证 Agent 执行
```

### 检查点
- [x] 定时任务按 Cron 自动触发
- [x] 执行历史落库（开始/结束时间、状态、来源）
- [x] 异步触发立即返回，不阻塞 HTTP 响应
- [x] 重启后任务从 SQLite 恢复
