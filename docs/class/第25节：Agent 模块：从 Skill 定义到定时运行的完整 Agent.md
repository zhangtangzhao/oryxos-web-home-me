# 第25节：Agent 模块：从 Skill 定义到定时运行的完整 Agent

> **课时目标**：理解"一个目录 = 一个 Agent"的完整生命周期，实现 Agent 加载、定时调度、执行历史等核心能力。

---

## 一、本节目的

前面 16-24 节搭建了底座（Provider、ReAct、Tool、Memory、Sandbox），本节将底座能力组合为**一个完整的 Agent**：

1. **AgentLoader**：扫描 `.oryxos/agents/` 目录，解析 `AGENT.md`
2. **AgentService**：统一的 Agent 执行入口
3. **AgentScheduler**：定时任务调度（cron → Agent 自动运行）
4. **Agent 执行历史**：每次运行的审计记录

---

## 二、一个 Agent 的完整定义

### 2.1 目录结构

```
.oryxos/agents/daily-weather/
├── AGENT.md          # Agent 定义（身份 + 工具 + 调度 + Skill）
├── REFERENCE.md      # 参考知识（可选）
├── skills/           # Agent 专属 Skill（可选）
│   └── weather-formatter/
│       └── SKILL.md
└── scripts/          # Agent 专属脚本（可选）
    └── parse_weather.py
```

### 2.2 AGENT.md 格式

```markdown
---
name: daily-weather
description: 每日天气助手，每天早上查询天气并推送到团队群
version: "1.0"

provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.3

tools:
  - http_get
  - notify
  - save_memory
  - current_time

skills:
  - weather-formatter
  - notify-message

notify: team-lark

schedule:
  - id: morning-report
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: "查询北京今天和明天的天气，整理成简洁的报告，通知到 team-lark"

bootstrap:
  - AGENTS.md
  - SOUL.md
---

# 身份
你是一个专业的天气助手。你的职责是查询天气信息并整理成易读的报告。

# 工作流程
1. 使用 http_get 工具查询天气 API
2. 使用 weather-formatter skill 格式化输出
3. 使用 notify 工具推送到指定渠道
4. 使用 save_memory 保存查询记录

# 输出格式
- 简洁明了，一个城市一段
- 包含：天气状况、温度范围、降水概率、风力
- 如有极端天气，加粗提醒
```

### 2.3 Agent 的生命周期

```
┌─────────────────────────────────────────────────┐
│                 1. 定义                            │
│  创建 AGENT.md → 放入 .oryxos/agents/{name}/     │
├─────────────────────────────────────────────────┤
│                 2. 加载                            │
│  AgentLoader 扫描 → 解析 frontmatter → Profile    │
├─────────────────────────────────────────────────┤
│                 3. 触发                            │
│  手动：CLI / REST API / 管理台「立即触发」按钮     │
│  定时：AgentScheduler 按 cron 自动触发             │
├─────────────────────────────────────────────────┤
│                 4. 执行                            │
│  AgentService → ReAct Loop → Tool → 结果         │
├─────────────────────────────────────────────────┤
│                 5. 记录                            │
│  agent_executions 表 ← 开始/结束时间、状态、来源   │
└─────────────────────────────────────────────────┘
```

---

## 三、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 `AgentLoader` | 扫描目录 → 解析 AGENT.md → Profile |
| D2 | 实现 `AgentService` 完整逻辑 | Profile + 会话 + ReAct |
| D3 | 实现 `AgentScheduler` | Cron → Agent 自动运行 |
| D4 | 实现 `AgentExecution` 实体 + 审计 | 执行历史落库 |
| D5 | 管理台 Agent 管理 | 列表 + 创建 + 详情 + 立即触发 |
| D6 | 创建示例 Agent（daily-weather） | 端到端验证 |
| D7 | 编写测试 | Agent 全流程测试 |

---

## 四、代码讲解

### 4.1 AgentLoader

```java
public interface AgentLoader {
    /** 扫描所有 Agent 目录，返回 Profile 列表 */
    List<Profile> scanAll();

    /** 加载单个 Agent */
    Optional<Profile> load(String agentName);

    /** 从 AGENT.md 解析 Profile */
    Profile deriveProfile(Path agentDir);
}
```

解析逻辑：
```java
public Profile deriveProfile(Path agentDir) {
    Path agentMd = agentDir.resolve("AGENT.md");
    String content = Files.readString(agentMd);

    // 分离 YAML frontmatter 和 Markdown body
    var parts = parseFrontmatter(content);
    Profile profile = yamlMapper.readValue(parts.frontmatter(), Profile.class);
    profile.getIdentity().setPrompt(parts.body());
    return profile;
}
```

### 4.2 AgentService 实现

```java
@Service
public class DefaultAgentService implements AgentService {

    private final AgentLoader agentLoader;
    private final SessionManager sessionManager;
    private final ReActLoop reActLoop;
    private final AgentExecutionRepository executionRepo;

    @Override
    public String process(Session session, String userMessage) {
        // 1. 加载 Profile
        Profile profile = agentLoader.load(session.getProfileName())
            .orElseThrow(() -> new AgentNotFoundException(session.getProfileName()));

        // 2. 追加用户消息到会话
        session.addMessage(Message.user(userMessage));

        // 3. 执行 ReAct Loop
        long start = System.currentTimeMillis();
        String result = reActLoop.execute(session, profile);
        long duration = System.currentTimeMillis() - start;

        // 4. 追加 Agent 回复
        session.addMessage(Message.assistant(result));

        // 5. 记录执行历史
        executionRepo.save(new AgentExecution(
            profile.getName(), "manual", start, duration, "COMPLETED"));

        return result;
    }
}
```

### 4.3 AgentScheduler

```java
@Component
public class DefaultAgentScheduler implements AgentScheduler {

    private final AgentLoader agentLoader;
    private final AgentService agentService;
    private final ScheduledExecutorService executor =
        Executors.newVirtualThreadPerTaskExecutor();

    @PostConstruct
    void start() {
        List<Profile> agents = agentLoader.scanAll();
        for (Profile agent : agents) {
            for (ScheduleDef schedule : agent.getSchedules()) {
                scheduleJob(agent, schedule);
            }
        }
    }

    private void scheduleJob(Profile agent, ScheduleDef schedule) {
        CronExpression cron = CronExpression.parse(schedule.getCron());
        executor.submit(() -> {
            while (true) {
                Instant next = cron.next(Instant.now());
                Thread.sleep(Duration.between(Instant.now(), next));
                // 创建新会话，执行 Agent
                Session session = sessionManager.create(agent.getName());
                agentService.process(session, schedule.getMessage());
            }
        });
    }
}
```

### 4.4 AgentExecution 实体

```java
@Entity
@Table(name = "agent_executions")
public class AgentExecutionEntity {
    @Id private String id;
    private String agentName;     // Agent 名称
    private String triggerType;   // MANUAL | SCHEDULED | API
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private String status;        // RUNNING | COMPLETED | FAILED
    private String errorMessage;
    private String sessionId;     // 关联的会话
}
```

---

## 五、Harness：如何验收

### 编译
```bash
mvn clean compile
```

### 单元测试
```java
@Test void agentLoader_shouldParseAgentsMd() { ... }
@Test void agentLoader_shouldExtractFrontmatter() { ... }
@Test void scheduler_shouldTriggerOnSchedule() { ... }
@Test void execution_shouldRecordStartAndEnd() { ... }
```

### 集成测试
```bash
# 1. 创建测试 Agent
mkdir -p .oryxos/agents/test-agent
# 写入 AGENT.md（含 schedule: cron "*/1 * * * *"）

# 2. 启动服务，观察 Agent 每分钟自动运行
# 3. 管理台 → Agent 列表 → 查看执行历史

# 4. 手动触发
curl -X POST http://localhost:8080/api/v1/agents/test-agent/invoke \
  -d '{"message":"你好，请做自我介绍"}'
```

### 宪法检查
- [x] AGENT.md 一个目录 = 一个 Agent（宪法 #7）
- [x] AgentLoader 归 core 模块，不归 Tool（宪法 #7）
- [x] Skill 不进 ToolRegistry（宪法 #8）
