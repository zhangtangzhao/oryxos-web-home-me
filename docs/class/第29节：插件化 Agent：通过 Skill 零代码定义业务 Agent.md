# 第29节：插件化 Agent：通过 Skill 零代码定义业务 Agent

> **课时目标**：实现 OryxOS 的插件化 Agent 机制——通过 Web Service 上传一个 Skill，自动创建一个完整的业务 Agent，自动扫描并按 Skill 定义的规则定时执行。

---

## 一、本节目的

底座（16-28 节）提供了运行 Agent 的全部能力。本节回答：**怎么定义一个真正的业务 Agent？**

```
底座能力                       +     业务定义            =     业务 Agent
(Provider/ReAct/Tool/                 (AGENT.md +
 Memory/Sandbox/Notify)                Skill + Schedule)
```

通过 Web Service 上传一个 Skill，OryxOS 自动：
1. 创建 Agent 目录（脚手架：AGENT.md + skills/ + output/）
2. 扫描并注册到 Agent 列表
3. 按 AGENT.md 中的 schedule 定时执行
4. Agent 出现在管理台，可查看/编辑/触发

---

## 二、Skill-Centric Agent 模型

### 2.1 模型转变

```
旧模型：Tool-Centric
  定义 Agent = 选 Provider + 选 Tool + 写 Prompt

新模型：Skill-Centric
  定义 Agent = 定义一个 Skill（做什么事）+ OryxOS 自动配置
```

### 2.2 一个完整示例：每日对账 Agent

**AGENT.md**：
```markdown
---
name: daily-reconcile
description: 每日对账助手，检查昨日交易记录并生成对账报告
version: "1.0"

provider:
  name: deepseek
  model: deepseek-chat

tools:
  - http_get
  - http_post
  - read_file
  - write_file
  - notify

skills:
  - reconcile-checker
  - report-format

notify: finance-lark

schedule:
  - id: daily-reconcile
    cron: "0 0 9 * * *"
    zone: Asia/Shanghai
    message: >
      查询昨日的交易记录，执行对账检查，
      生成对账报告（使用 report-format skill），
      通知到 finance-lark 渠道。

bootstrap:
  - AGENTS.md
---

# 身份
你是一个专业的财务对账助手。

# 工作流程
1. 使用 http_post 查询昨日交易记录
2. 使用 reconcile-checker skill 检查对账
3. 使用 write_file 生成对账报告
4. 使用 notify 推送到财务群
5. 使用 save_memory 保存对账结果摘要
```

**skills/reconcile-checker/SKILL.md**：
```markdown
# 对账检查 Skill

## 目的
自动检查交易记录的对账状态。

## 检查规则
1. 每笔交易的金额与对方记录一致
2. 交易时间在营业时间内
3. 交易状态为"已完成"
4. 如有差异，标记为"待处理"

## 输出格式
```json
{
  "total": 150,
  "matched": 148,
  "mismatched": 2,
  "pending": 0
}
```
```

---

## 三、实现架构

### 3.1 创建 Agent 流程

```
用户 → 管理台 / API → 创建 Agent
  ├── 输入：名称 + 描述
  ├── 后台：
  │   ├── 创建 .oryxos/agents/{name}/ 目录
  │   ├── 生成 AGENT.md（模板 + 用户输入）
  │   ├── 创建 skills/ output/ 子目录
  │   └── AgentLoader 自动扫描 → 注册
  └── 返回：Agent 已创建
```

### 3.2 一句话生成 Agent（AI 辅助）

```
用户输入：自然语言描述
  "帮我做一个每天早上8点查询GitHub最火Rust项目推送到飞书的Agent"

↓ 大模型（通过 Provider）处理

生成 AGENT.md：
  - 解析意图 → "github-rust-daily"
  - 选择合适的工具 → http_get, notify, current_time
  - 生成 schedule → cron "0 0 8 * * *"
  - 生成 system prompt → 工作流程描述
  - 关联 notify channel → team-lark
```

### 3.3 自动扫描

```java
@Component
public class DefaultAgentLoader implements AgentLoader {

    private final Path agentsDir;

    @PostConstruct
    void init() {
        // 启动时扫描
        scanAll();
        // 监听文件系统变化（可选）
        watchForChanges();
    }

    @Override
    public List<Profile> scanAll() {
        if (!Files.exists(agentsDir)) return List.of();
        try (var dirs = Files.list(agentsDir)) {
            return dirs
                .filter(Files::isDirectory)
                .map(this::deriveProfile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }
    }

    @Override
    public Profile deriveProfile(Path agentDir) {
        Path agentMd = agentDir.resolve("AGENT.md");
        if (!Files.exists(agentMd)) return null;
        // 解析 YAML frontmatter + Markdown body
        return parseAgentMd(agentMd);
    }
}
```

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 `AgentLoader` 扫描逻辑 | 自动发现 Agent |
| D2 | 实现 `POST /api/v1/agents` 创建 Agent | API 创建 |
| D3 | 实现 AI 辅助生成（一句话 → AGENT.md） | 生成流 |
| D4 | 管理台 Agent 创建页面 | 独立页面 + AI 生成 |
| D5 | 管理台 Agent 详情 | 文件浏览器 + 编辑 |
| D6 | 创建示例 Agent（daily-reconcile） | 端到端验证 |
| D7 | 编写测试 | Agent 创建/扫描测试 |

---

## 五、Harness：如何验收

### 编译
```bash
mvn clean compile
```

### 手动验证
```bash
# 1. 通过 API 创建 Agent
curl -X POST http://localhost:8080/api/v1/agents \
  -H "Content-Type: application/json" \
  -d '{"name":"test-agent","description":"测试 Agent"}'

# 2. 验证目录创建
ls .oryxos/agents/test-agent/
# AGENT.md  skills/  output/

# 3. 验证 Agent 出现在列表
curl http://localhost:8080/api/v1/agents

# 4. 管理台 → Agent 列表 → 查看新 Agent
```

### 检查点
- [x] 创建 Agent 后自动出现在列表
- [x] 目录结构完整（AGENT.md + skills/ + output/）
- [x] AI 生成功能可用（可选：需要真实 Provider）
- [x] AgentLoader 自动扫描注册
