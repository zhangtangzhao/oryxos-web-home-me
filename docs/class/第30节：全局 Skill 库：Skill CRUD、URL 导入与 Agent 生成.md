# 第30节：全局 Skill 库：Skill CRUD、URL 导入与 Agent 生成

> **课时目标**：实现 OryxOS 的全局 Skill 库——Skill CRUD、从 GitHub URL 导入 Skill、创建 Agent 时自动引入 Skill 约束。

---

## 一、本节目的

Skill 是 OryxOS 的核心竞争力之一。本节建立完整的 Skill 管理体系：

1. **全局 Skill 库**：`.oryxos/skills/{name}/SKILL.md`，所有 Agent 共享
2. **Skill CRUD**：REST API + 管理台管理
3. **URL 导入**：支持从 GitHub 目录一键导入 Skill
4. **Agent 生成时注入 Skill**：创建 Agent 时告诉模型可用的 Skill 目录

---

## 二、Skill 的定义与定位

### 2.1 Skill ≠ Tool

| | Tool | Skill |
|---|------|-------|
| **本质** | 可执行的操作 | 指导性知识 |
| **LLM 视角** | 可调用的函数 | 要遵循的规则/模板 |
| **注册方式** | ToolRegistry | 文件系统 |
| **注入方式** | Function Calling Schema | System Prompt |
| **例子** | `http_get`, `shell` | `report-format`, `json-output` |

### 2.2 Skill 目录结构

```
.oryxos/skills/
├── report-format/
│   └── SKILL.md              ← Skill 定义
├── web-research/
│   └── SKILL.md
├── summarize/
│   └── SKILL.md
├── json-output/
│   └── SKILL.md
├── code-review/
│   └── SKILL.md
└── notify-message/
    └── SKILL.md
```

### 2.3 SKILL.md 格式

```markdown
# report-format

## 描述
生成结构化报告的 Skill。Agent 输出报告时应遵循此格式。

## 输出模板

### 标题
{报告主题} — {日期}

### 摘要
一段话概括核心发现。

### 详细分析
| 指标 | 当前值 | 变化 | 趋势 |
|------|--------|------|------|

### 结论与建议
- 关键发现 1
- 关键发现 2
- 建议行动
```

---

## 三、实现

### 3.1 REST API

```java
@RestController
@RequestMapping("/api/v1/skills")
public class SkillApiController {

    @GetMapping
    public ApiResponse<List<SkillInfo>> listSkills() {
        // 列出所有全局 Skill
    }

    @PostMapping
    public ApiResponse<SkillInfo> createSkill(@RequestBody CreateSkillRequest req) {
        // 创建 Skill：创建目录 + 写入 SKILL.md
    }

    @GetMapping("/{name}")
    public ApiResponse<SkillDetail> getSkill(@PathVariable String name) {
        // 查看 Skill 详情（含文件列表）
    }

    @PutMapping("/{name}")
    public ApiResponse<SkillInfo> updateSkill(@PathVariable String name,
                                               @RequestBody UpdateSkillRequest req) {
        // 更新 Skill 内容
    }

    @DeleteMapping("/{name}")
    public ApiResponse<Void> deleteSkill(@PathVariable String name) {
        // 删除 Skill 目录
    }

    @PostMapping("/import")
    public ApiResponse<SkillInfo> importFromUrl(@RequestBody ImportRequest req) {
        // 从 GitHub URL 导入 Skill
        // 例如：https://github.com/obra/superpowers/tree/main/skills/brainstorming
    }
}
```

### 3.2 GitHub URL 导入

```java
@Service
public class SkillImportService {

    public SkillInfo importFromGitHub(String githubUrl) {
        // 1. 解析 GitHub URL
        // https://github.com/{owner}/{repo}/tree/{branch}/{path}
        var parts = parseGitHubUrl(githubUrl);

        // 2. 调用 GitHub API 获取目录内容
        String apiUrl = String.format(
            "https://api.github.com/repos/%s/%s/contents/%s?ref=%s",
            parts.owner(), parts.repo(), parts.path(), parts.branch());

        // 3. 下载每个文件
        List<GitHubContent> files = fetchDirectoryContents(apiUrl);

        // 4. 创建本地 Skill 目录
        String skillName = extractSkillName(parts.path());
        Path skillDir = skillsRoot.resolve(skillName);
        for (var file : files) {
            Path target = skillDir.resolve(file.name());
            Files.writeString(target, decodeBase64(file.content()));
        }

        return new SkillInfo(skillName, files.size());
    }
}
```

### 3.3 内置 Skill 清单

| Skill | 用途 | 适用场景 |
|-------|------|---------|
| `report-format` | 结构化报告模板 | 所有需要产生报告的 Agent |
| `web-research` | 网络调研方法 | 调研类 Agent |
| `summarize` | 摘要生成规则 | 任何需要总结输出的 Agent |
| `json-output` | JSON 格式输出约束 | API 对接场景 |
| `code-review` | 代码审查检查清单 | 代码审查 Agent |
| `notify-message` | 通知消息模板 | 需要推送通知的 Agent |

### 3.4 Agent 生成时注入 Skill

```java
// 创建 Agent 时，把可用的 Skill 列表告诉大模型
public String buildGenerationPrompt(CreateAgentRequest req) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("你是一个 Agent 设计专家。请根据以下需求生成 AGENT.md：\n");
    prompt.append("需求：").append(req.getDescription()).append("\n\n");

    // 注入可用的 Skill 目录
    prompt.append("## 可用的全局 Skill\n");
    for (SkillInfo skill : skillService.listAll()) {
        prompt.append("- **").append(skill.getName()).append("**: ")
              .append(skill.getDescription()).append("\n");
    }

    prompt.append("\n## 可用的通知渠道\n");
    for (NotifyChannel ch : notifyChannelService.listAll()) {
        prompt.append("- ").append(ch.getName())
              .append(" (").append(ch.getType()).append(")\n");
    }

    // 注入可用的 Tool 清单
    prompt.append("\n## 可用的工具\n");
    for (OryxTool tool : toolRegistry.listAll()) {
        prompt.append("- ").append(tool.getName()).append(": ")
              .append(tool.getDescription()).append("\n");
    }

    return prompt.toString();
}
```

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 `SkillApiController` | Skill CRUD |
| D2 | 实现 `SkillImportService` | GitHub URL 导入 |
| D3 | 创建 6 个内置 Skill | 预置 Skill 库 |
| D4 | 管理台 Skill 列表 + 详情 | 查看文件 + Markdown 渲染 |
| D5 | 生成 Agent 时注入 Skill 目录 | AI 生成流增强 |
| D6 | 安全性加固（SSRF 防御） | URL 导入安全 |
| D7 | 编写测试 | Skill CRUD + 导入测试 |

---

## 五、Harness：如何验收

### 编译
```bash
mvn clean compile
```

### 手动验证
```bash
# 1. 查看内置 Skill
curl http://localhost:8080/api/v1/skills

# 2. 创建自定义 Skill
curl -X POST http://localhost:8080/api/v1/skills \
  -d '{"name":"my-skill","content":"# My Skill\n\n这是我的自定义 Skill。"}'

# 3. 从 GitHub 导入 Skill
curl -X POST http://localhost:8080/api/v1/skills/import \
  -d '{"url":"https://github.com/obra/superpowers/tree/main/skills/brainstorming"}'

# 4. 管理台 → Skill 列表 → 查看详情 → 文件内容（Markdown 渲染）
```

### 检查点
- [x] Skill 全局共享，不跟随单个 Agent
- [x] Skill 不进 ToolRegistry（宪法 #8）
- [x] URL 导入支持 GitHub 目录
- [x] SSRF 防御（禁止内网地址 + 逐跳校验）
