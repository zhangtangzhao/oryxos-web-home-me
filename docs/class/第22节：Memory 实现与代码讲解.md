# 第22节：Memory 实现与代码讲解

> **课时目标**：基于第21节的技术评审，实现 OryxOS Memory 模块的三种后端——Markdown、SQLite、Mem0——并实现 MemoryTools 供 Agent 调用。

---

## 一、本节目的

将第21节的设计决策落地为代码：

1. **插件化 Memory 架构**：`MemoryService` 门面 + `MemoryBackend` SPI
2. **三种后端实现**：Markdown（默认）、SQLite、Mem0（扩展）
3. **MemoryTools**：`save_memory` / `recall_memory` 两个 Tool
4. **Per-Agent 记忆隔离**：每个 Agent 独立的记忆命名空间

---

## 二、架构设计

### 2.1 MemoryBackend SPI

```java
public interface MemoryBackend {
    /** 保存记忆 */
    void save(String agentId, MemoryEntry entry);

    /** 关键词检索 */
    List<MemoryEntry> recall(String agentId, String query, int k);

    /** 列出所有记忆 */
    List<MemoryEntry> listAll(String agentId);

    /** 归档旧记忆 */
    List<MemoryEntry> archive(String agentId, Duration olderThan);
}
```

### 2.2 MemoryEntry 数据模型

```java
public class MemoryEntry {
    private String id;           // UUID
    private String agentId;      // 归属 Agent
    private String content;      // 记忆内容
    private MemoryScope scope;   // CORE | ARCHIVAL
    private List<String> tags;   // 标签
    private Instant createdAt;
    private Instant updatedAt;
}
```

### 2.3 三种后端对比

| 维度 | Markdown | SQLite | Mem0 |
|------|----------|--------|------|
| 存储位置 | `.oryxos/memory/{agentId}/` | `oryxos.db` → `memories` 表 | 外部 Mem0 服务 |
| 检索方式 | 文件名 + 内容关键词 | SQL LIKE / FTS | 语义向量检索 |
| 人类可读 | ✅ 是 | ❌ 需工具查看 | ❌ 需 API |
| Git 版本控制 | ✅ 可 | ❌ 不可 | ❌ 不可 |
| 依赖 | 零 | SQLite JDBC | 外部服务 |
| 适用场景 | 个人使用、少量 Agent | 生产环境、多 Agent | 高级语义检索 |

---

## 三、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 定义 `MemoryBackend` SPI | core 模块接口 |
| D2 | 实现 `MarkdownMemoryBackend` | 文件系统存储 |
| D3 | 实现 `SqliteMemoryBackend` | JPA + SQLite 存储 |
| D4 | 实现 `MemoryService` 门面 | 统一入口 |
| D5 | 实现 `MemoryTools`（save/recall） | Agent 可调用 |
| D6 | 实现 REST API + 管理台 | CRUD + 查看 |
| D7 | 编写测试 | 三种后端测试 |

---

## 四、代码讲解

### 4.1 MarkdownMemoryBackend（默认）

```java
@Component
@ConditionalOnProperty(name = "oryxos.memory.backend", havingValue = "markdown")
public class MarkdownMemoryBackend implements MemoryBackend {

    private final Path baseDir;

    public MarkdownMemoryBackend(@Value("${oryxos.root:.oryxos}") String root) {
        this.baseDir = Path.of(root, "memory");
    }

    @Override
    public void save(String agentId, MemoryEntry entry) {
        Path dir = baseDir.resolve(agentId);
        Files.createDirectories(dir);
        Path file = dir.resolve(entry.getScope().name().toLowerCase() + ".md");

        // 追加记忆条目到 Markdown 文件
        String md = formatMemoryEntry(entry);
        Files.writeString(file, md, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private String formatMemoryEntry(MemoryEntry entry) {
        return String.format("""
            ---
            id: %s
            tags: [%s]
            created: %s
            ---
            %s

            """,
            entry.getId(),
            String.join(", ", entry.getTags()),
            entry.getCreatedAt().toString(),
            entry.getContent()
        );
    }

    @Override
    public List<MemoryEntry> recall(String agentId, String query, int k) {
        Path dir = baseDir.resolve(agentId);
        if (!Files.exists(dir)) return List.of();

        // 关键词匹配（核心阶段用简单方案）
        String[] keywords = query.toLowerCase().split("\\s+");
        return Files.list(dir)
            .flatMap(f -> parseEntries(f).stream())
            .filter(e -> matchesKeywords(e, keywords))
            .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).reversed())
            .limit(k)
            .collect(Collectors.toList());
    }
}
```

### 4.2 SqliteMemoryBackend

```java
@Component
@ConditionalOnProperty(name = "oryxos.memory.backend", havingValue = "sqlite")
public class SqliteMemoryBackend implements MemoryBackend {

    private final MemoryJpaRepository jpaRepository;

    @Override
    public void save(String agentId, MemoryEntry entry) {
        MemoryEntity entity = MemoryEntity.from(entry);
        entity.setAgentId(agentId);
        jpaRepository.save(entity);
    }

    @Override
    public List<MemoryEntry> recall(String agentId, String query, int k) {
        // 使用 SQLite FTS（全文搜索）或 LIKE
        return jpaRepository
            .findByAgentIdAndContentContaining(agentId, query,
                PageRequest.of(0, k, Sort.by("createdAt").descending()))
            .stream()
            .map(MemoryEntity::toEntry)
            .collect(Collectors.toList());
    }
}
```

### 4.3 MemoryTools

```java
@Component
public class MemoryTools {

    private final MemoryService memoryService;

    // save_memory Tool
    public ToolResult saveMemory(JsonNode input) {
        String content = input.get("content").asText();
        String scope = input.has("scope") ? input.get("scope").asText() : "CORE";
        String agentId = input.get("agent_id").asText();

        MemoryEntry entry = new MemoryEntry();
        entry.setContent(content);
        entry.setScope(MemoryScope.valueOf(scope.toUpperCase()));
        memoryService.save(agentId, entry);

        return ToolResult.success("记忆已保存 [id=" + entry.getId() + "]");
    }

    // recall_memory Tool
    public ToolResult recallMemory(JsonNode input) {
        String query = input.get("query").asText();
        int k = input.has("k") ? input.get("k").asInt() : 5;
        String agentId = input.get("agent_id").asText();

        List<MemoryEntry> results = memoryService.recall(agentId, query, k);
        String formatted = results.stream()
            .map(e -> "- [" + e.getScope() + "] " + e.getContent())
            .collect(Collectors.joining("\n"));
        return ToolResult.success(formatted);
    }
}
```

### 4.4 核心记忆 vs 归档记忆

```java
public enum MemoryScope {
    CORE,      // 核心记忆：永久保留，始终注入 Prompt
    ARCHIVAL   // 归档记忆：30 天后归档，默认不注入（除非查询命中）
}
```

---

## 五、Harness：如何验收

### 编译
```bash
mvn clean compile -pl oryxos-memory
```

### 单元测试
```java
@Test void markdownBackend_shouldSaveAndRecall() { ... }
@Test void sqliteBackend_shouldPersistAcrossRestarts() { ... }
@Test void recall_shouldReturnMostRecentFirst() { ... }
@Test void saveMemory_shouldAutoTimestamp() { ... }
@Test void coreMemory_shouldBeInjectedToPrompt() { ... }
@Test void archivalMemory_shouldNotBeInjectedByDefault() { ... }
```

### 集成测试
```bash
# 启动服务后用 Mock Provider 测试
# 1. Agent 回答用户问题时调用 save_memory
# 2. 下次对话时 recall_memory 返回之前保存的记忆
# 3. 管理台 → 长期记忆 → 查看记忆列表
```

### 宪法检查
- [x] MemoryService 接口先行（宪法 #9）
- [x] 记忆数据存 SQLite，走 JPA（与审计表一致）
