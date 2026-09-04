# Demo 二：每日科技日报（SC-008 发布硬条件，全程零 Java 代码）

零代码接入的完整形态：`AGENT.md`（schedules + mcp_servers + save/recall_memory）+ Skill 绑定（软连接）+ 长期记忆，全程不改一行 Java。

## 装配

```bash
oryxos init
export DEEPSEEK_API_KEY=sk-xxx

# 1. Agent
cp config/demos/daily-tech-digest/AGENT.md .oryxos/agents/daily-tech-digest/AGENT.md

# 2. 公共 Skill 实体 + 绑定（软连接表达，frontmatter 没有 skills: 字段）
mkdir -p .oryxos/skills/news-report
cp config/demos/daily-tech-digest/SKILL.md .oryxos/skills/news-report/SKILL.md
# Windows（开发者模式或管理员）:
cmd /c mklink /J .oryxos\agents\daily-tech-digest\skills\news-report .oryxos\skills\news-report
# Linux / macOS:
ln -s ../../skills/news-report .oryxos/agents/daily-tech-digest/skills/news-report

# 3. MCP server 注册（工作区级，data-model §4）
cat > .oryxos/../config/mcp_servers.yaml <<'EOF'
servers:
  - name: tech-news
    transport: stdio
    command: npx
    args: ["-y", "@modelcontextprotocol/server-brave-search"]
    env:
      BRAVE_API_KEY: ${BRAVE_API_KEY}
EOF
#    也可换任一新闻/搜索类社区 MCP server；连接失败只记日志跳过，不阻断启动。

# 4. 提前对话告知关注方向（写入长期记忆，次日日报体现）
oryxos chat --profile daily-tech-digest --message "我更关注 AI 和芯片方向，请记住。"

# 5. 常驻守护
oryxos gateway
```

## 通过标准（quickstart 场景 9）

- 到点自动产出日报：日报内容体现已记住的关注方向（AI / 芯片相关点评）
- `tool_invocations` 出现 `recall_memory` / MCP 工具（`tech-news__…`）记录
- 手动补跑走同一链路：`oryxos chat --profile daily-tech-digest --message "生成本期科技日报。"`
- 全程未编写/修改任何 Java 代码
