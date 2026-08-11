# 第20节：Tool 体系扩展与 MCP Connector

> **课时目标**：扩展 OryxOS 工具库至 24+ 个工具，覆盖 90-95% 的 Agent 场景，并引入 MCP（Model Context Protocol）Connector 对接外部工具生态。

---

## 一、本节目的

第18节建立了 Tool 体系的基础框架，本节在此基础上：

1. **丰富默认工具库**：从 9 个扩展到 24+ 个，分 Tier0（必备）和 Tier1（增强）
2. **引入 MCP Connector**：让 Agent 可以调用任何 MCP 兼容的外部工具
3. **管理台 MCP 管理**：CRUD MCP Server 连接，Agent 创建时可选择

---

## 二、工具库分层设计

### Tier 0：基础必备（每个 Agent 都应该有）

| 工具 | 分类 | 说明 |
|------|------|------|
| `read_file` | 文件 | 读取文件内容 |
| `write_file` | 文件 | 写入文件 |
| `list_dir` | 文件 | 列出目录结构 |
| `shell` | Shell | 执行 Shell 命令 |
| `http_get` | HTTP | GET 请求 |
| `http_post` | HTTP | POST 请求 |
| `current_time` | 工具 | 获取当前日期时间 |
| `save_memory` | 记忆 | 保存长期记忆 |
| `recall_memory` | 记忆 | 查询长期记忆 |
| `notify` | 通知 | 推送通知到渠道 |

### Tier 1：增强能力（按需启用）

| 工具 | 分类 | 说明 |
|------|------|------|
| `fetch_webpage` | HTTP | 获取网页全文（HTML→Markdown） |
| `download_file` | 文件 | 下载文件到本地 |
| `make_dir` | 文件 | 创建目录 |
| `append_file` | 文件 | 追加内容到文件 |
| `delete_file` | 文件 | 删除文件 |
| `move_file` | 文件 | 移动/重命名文件 |
| `copy_file` | 文件 | 复制文件 |
| `json_extract` | 数据处理 | 从 JSON 中提取字段 |
| `csv_read` | 数据处理 | 读取 CSV 为结构化数据 |
| `markdown_to_html` | 格式 | Markdown 转 HTML |
| `count_lines` | 文本 | 统计文件行数 |
| `search_files` | 搜索 | 按名称/内容搜索文件 |
| `web_search` | 搜索 | 网络搜索（需 API Key） |
| `execute_sql` | 数据库 | 执行 SQL 查询（只读） |

---

## 三、MCP（Model Context Protocol）Connector

### 3.1 什么是 MCP？

MCP 是 Anthropic 提出的开放协议，定义了大模型与外部工具/数据源之间的标准接口。核心概念：

```
┌──────────────────────────────┐
│        OryxOS (Host)          │
│  ┌────────────────────────┐  │
│  │   McpClientService      │  │  ← 管理多个 MCP 连接
│  │   ┌──────┐ ┌──────┐    │  │
│  │   │MCP 1 │ │MCP 2 │    │  │
│  │   └──┬───┘ └──┬───┘    │  │
│  └──────┼────────┼────────┘  │
│         │        │            │
└─────────┼────────┼────────────┘
          │        │
    ┌─────▼──┐ ┌───▼──────┐
    │ MCP    │ │ MCP      │
    │ Server │ │ Server   │
    │ (本地) │ │ (远程    │
    │        │ │  HTTP)   │
    └────────┘ └──────────┘
```

### 3.2 OryxOS 中的 MCP 三层关系

| 层次 | 说明 | 示例 |
|------|------|------|
| **协议层** | MCP 协议本身（JSON-RPC over stdio/HTTP/SSE） | 标准规范 |
| **官方 Connector** | 社区/官方维护的 MCP Server | GitHub MCP, Slack MCP, PostgreSQL MCP |
| **自定义 Connector** | 用户自己写的 MCP Server | 企业内部系统对接 |

### 3.3 MCP Server 配置

```yaml
# AGENT.md 中引用
mcp_servers:
  - github    # 使用全局注册的 GitHub MCP
  - postgres  # 使用全局注册的 PostgreSQL MCP
tools:
  - read_file
  - http_get
  # MCP 工具自动从 MCP Server 获取并合并
```

### 3.4 远程 MCP 鉴权

第一版支持：
- **Bearer Token**：`Authorization: Bearer <token>`
- **API Key**：`X-API-Key: <key>`

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 实现 Tier0 工具（`current_time`、`json_extract` 等） | 新增 5+ 工具 |
| D2 | 实现 Tier1 工具（文件管理、数据处理） | 新增 10+ 工具 |
| D3 | 实现 `McpClientService` | MCP 客户端管理 |
| D4 | 实现 MCP Server CRUD（JPA + REST API） | 全局 MCP 注册表 |
| D5 | 管理台 MCP 管理页面 | 列表 + CRUD |
| D6 | 创建 Agent 时注入 MCP 目录 | 工具清单合并 |
| D7 | 编写测试 | MCP 集成测试 |

---

## 五、代码讲解

### 5.1 扩展工具示例：current_time

```java
@Component
public class CurrentTimeTool implements OryxTool {
    @Override
    public String getName() { return "current_time"; }

    @Override
    public String getDescription() {
        return "获取当前日期和时间。参数: format (可选，如 'yyyy-MM-dd HH:mm:ss')";
    }

    @Override
    public ToolResult execute(JsonNode input) {
        String format = input.has("format")
            ? input.get("format").asText()
            : "yyyy-MM-dd HH:mm:ss";
        String now = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern(format));
        return ToolResult.success(now);
    }
}
```

### 5.2 McpClientService

```java
public interface McpClientService {
    /** 连接到 MCP Server 并获取可用工具列表 */
    List<OryxTool> connect(String serverId);

    /** 断开 MCP Server 连接 */
    void disconnect(String serverId);

    /** 获取所有已连接 MCP Server 的工具 */
    Map<String, List<OryxTool>> getAllTools();
}
```

### 5.3 Tool 清单合并

```java
// 创建 Agent 时，合并内置工具 + MCP 工具
List<OryxTool> allTools = new ArrayList<>();
allTools.addAll(toolRegistry.listAll());        // 内置工具
for (String mcpName : profile.getMcpServers()) { // MCP 工具
    allTools.addAll(mcpClientService.connect(mcpName));
}
// 把完整工具清单注入 Prompt
String toolsJson = buildToolsJson(allTools);
```

---

## 六、Harness：如何验收

### 编译
```bash
mvn clean compile -pl oryxos-tool
```

### 测试
```java
@Test void currentTime_shouldReturnFormattedTime() { ... }
@Test void jsonExtract_shouldExtractNestedField() { ... }
@Test void mcpClient_shouldConnectAndListTools() { ... }
@Test void toolRegistry_shouldMergeBuiltinAndMcp() { ... }
```

### 集成测试
```bash
# 通过 API 注册 MCP Server
curl -X POST http://localhost:8080/api/v1/mcp-servers \
  -H "Content-Type: application/json" \
  -d '{"name":"github","type":"http","url":"https://mcp.github.com","authType":"bearer","authToken":"..."}'

# 验证 Agent 可调用 MCP 工具
```

### 宪法检查
- [x] 所有 Tool 实现 `OryxTool` 接口（统一抽象）
- [x] MCP 工具经 ToolRegistry 注册，不绕过 Sandbox
