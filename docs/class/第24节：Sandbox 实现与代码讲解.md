# 第24节：Sandbox 实现与代码讲解

> **课时目标**：基于第23节的技术评审，实现 OryxOS Sandbox 模块——WhitelistSandbox + SQLite 持久化 + 动态管理 API + 管理台。

---

## 一、本节目的

将第23节的设计落地为可运行的代码：

1. **WhitelistSandbox**：三类白名单（FILE / SHELL / HTTP）校验
2. **SQLite 持久化**：白名单规则存数据库，启动从配置播种，运行时增删写穿
3. **动态管理**：REST API + 管理台 CRUD
4. **安全边界**：Agent 的每次工具调用都要经过 Sandbox

---

## 二、核心设计

### 2.1 SandboxRule 数据模型

```sql
CREATE TABLE sandbox_rules (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_type  TEXT NOT NULL,    -- FILE | SHELL | HTTP
    rule_value TEXT NOT NULL,    -- 具体的路径/命令/域名
    description TEXT,            -- 说明（为什么加这个白名单）
    enabled    INTEGER DEFAULT 1,
    created_at TEXT,
    updated_at TEXT
);
```

### 2.2 启动播种

```
应用启动
  → 读取 config/application.yml 中 oryxos.sandbox.* 配置
  → 对每条规则：如果 SQLite 中不存在 → INSERT
  → 加载所有 enabled=1 的规则到内存缓存
  → 运行时可通过 API 增删 → 写穿到 SQLite → 刷新内存缓存
```

### 2.3 三类检查器

```java
public class WhitelistSandbox implements Sandbox {

    private final SandboxRuleRepository ruleRepo;
    private volatile Set<String> filePaths;     // 内存缓存
    private volatile Set<String> commands;
    private volatile Set<String> domains;

    @PostConstruct
    void init() {
        refreshCache();
    }

    public void refreshCache() {
        filePaths = loadRules(RuleType.FILE);
        commands  = loadRules(RuleType.SHELL);
        domains   = loadRules(RuleType.HTTP);
    }

    @Override
    public void enforce(ActionType type, String target) {
        switch (type) {
            case FILE_READ, FILE_WRITE -> checkFile(target);
            case SHELL_COMMAND          -> checkCommand(target);
            case HTTP_REQUEST           -> checkHttp(target);
        }
    }
}
```

---

## 三、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 创建 `SandboxRuleEntity` + `SandboxRuleRepository` | SQLite 持久化 |
| D2 | 实现 `WhitelistSandbox` 完整逻辑 | 三类检查 + 内存缓存 |
| D3 | 实现配置播种 | 启动时从 YAML 初始化 |
| D4 | 实现 `SandboxApiController` | REST CRUD |
| D5 | 管理台 Sandbox 页面 | 列表 + 新增/删除 |
| D6 | 编写测试 | Sandbox 安全测试 |

---

## 四、代码讲解

### 4.1 文件路径检查

```java
private void checkFile(String path) {
    String normalized = Path.of(path).normalize().toString();
    for (String allowed : filePaths) {
        if (normalized.startsWith(allowed)) {
            return;  // 通过
        }
    }
    throw new SandboxViolationException(
        ActionType.FILE_READ,
        path + " (允许的路径: " + filePaths + ")"
    );
}
```

### 4.2 Shell 命令检查

```java
private void checkCommand(String command) {
    // 取命令的第一个 token（去除管道和重定向）
    String firstToken = command.trim().split("\\s+")[0];
    // 处理 sudo / python3 等情况
    if (firstToken.equals("sudo") && command.trim().split("\\s+").length > 1) {
        firstToken = command.trim().split("\\s+")[1];
    }
    if (!commands.contains(firstToken)) {
        throw new SandboxViolationException(
            ActionType.SHELL_COMMAND,
            firstToken + " (允许的命令: " + commands + ")"
        );
    }
}
```

### 4.3 HTTP 域名检查（分级策略）

```java
private void checkHttp(String url) {
    String host = new URI(url).getHost();
    String method = extractMethod(url); // 从上下文获取

    if ("GET".equalsIgnoreCase(method)) {
        // GET 请求：默认放行，但检查黑名单
        checkSsrfBlacklist(host);
    } else {
        // POST/PUT/DELETE：必须走白名单
        if (!isDomainAllowed(host)) {
            throw new SandboxViolationException(
                ActionType.HTTP_REQUEST,
                host + " (POST 请求需要域名白名单)"
            );
        }
    }
}

private void checkSsrfBlacklist(String host) {
    // 禁止内网地址
    if (host.startsWith("10.") || host.startsWith("192.168.")
        || host.startsWith("172.16.") || host.equals("127.0.0.1")
        || host.equals("localhost") || host.equals("[::1]")) {
        throw new SandboxViolationException(
            ActionType.HTTP_REQUEST,
            host + " (内网地址被禁止)"
        );
    }
    // 禁止云元数据服务
    if (host.equals("169.254.169.254")) {
        throw new SandboxViolationException(
            ActionType.HTTP_REQUEST,
            host + " (云元数据服务被禁止)"
        );
    }
}
```

### 4.4 REST API

```java
@RestController
@RequestMapping("/api/v1/sandbox")
public class SandboxApiController {

    @GetMapping("/rules")
    public ApiResponse<List<SandboxRule>> listRules(
            @RequestParam(required = false) String type) {
        // 列出所有规则，可按 type 过滤
    }

    @PostMapping("/rules")
    public ApiResponse<SandboxRule> addRule(@RequestBody AddRuleRequest req) {
        // 新增规则 + 写穿 SQLite + 刷新内存缓存
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable Long id) {
        // 删除规则 + 刷新内存缓存
    }
}
```

### 4.5 配置播种

```java
@Component
public class SandboxRuleSeeder {

    @Value("${oryxos.sandbox.file.allowed-paths}")
    private List<String> filePaths;

    @Value("${oryxos.sandbox.shell.allowed-commands}")
    private List<String> commands;

    @Value("${oryxos.sandbox.http.allowed-domains}")
    private List<String> domains;

    @PostConstruct
    void seed() {
        for (String path : filePaths) {
            ruleRepo.findByTypeAndValue("FILE", path)
                .orElseGet(() -> ruleRepo.save(new SandboxRule("FILE", path)));
        }
        // 同理处理 commands 和 domains
    }
}
```

---

## 五、Harness：如何验收

### 编译
```bash
mvn clean compile -pl oryxos-tool
```

### 单元测试
```java
@Test void fileRead_shouldPassWhenPathInWhitelist() { ... }
@Test void fileRead_shouldThrowWhenPathNotInWhitelist() { ... }
@Test void shellCommand_shouldPassForAllowedCommand() { ... }
@Test void shellCommand_shouldBlockRmDashRf() { ... }
@Test void httpGet_shouldPassForAnyDomain() { ... }
@Test void httpGet_shouldBlockInternalNetwork() { ... }
@Test void httpPost_shouldRequireWhitelistedDomain() { ... }
@Test void ruleSeeder_shouldInsertMissingRules() { ... }
@Test void ruleSeeder_shouldSkipExistingRules() { ... }
```

### 集成测试
```bash
# 1. 查看当前白名单
curl http://localhost:8080/api/v1/sandbox/rules

# 2. 添加新规则
curl -X POST http://localhost:8080/api/v1/sandbox/rules \
  -d '{"type":"FILE","value":"/data/external","description":"外部数据目录"}'

# 3. 验证 Agent 工具调用受白名单约束
# 尝试读取不在白名单的路径 → 应返回 403

# 4. 管理台 → OS 运行时 → SandBox 列表 → 查看/新增/删除
```

### 宪法检查
- [x] Sandbox 接口先行（宪法 #9）
- [x] 白名单存 SQLite，与审计表一致
- [x] HTTP GET 默认放行 + SSRF 黑名单
