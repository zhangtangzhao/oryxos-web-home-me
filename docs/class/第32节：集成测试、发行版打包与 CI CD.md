# 第32节：集成测试、发行版打包与 CI/CD

> **课时目标**：完成 OryxOS 的工程化收尾——集成测试、发行版打包（make release）、GitHub Actions CI/CD、版本管理。

---

## 一、本节目的

OryxOS 开发进入收尾阶段。本节完成工程化：

1. **集成测试**：全链路端到端测试
2. **发行版打包**：`make release` → `.tar.gz`（含 bin/ config/ libs/）
3. **CI/CD**：GitHub Actions 自动构建 + Release
4. **版本管理**：语义化版本 + Maven 版本号

---

## 二、集成测试策略

### 2.1 测试金字塔

```
            ┌──────┐
            │ E2E  │  ← 全链路（启动服务 → 真实调用）
           ┌┴──────┴┐
           │ 集成测试│  ← 跨模块交互
          ┌┴────────┴┐
          │  单元测试  │  ← 单个类/方法
         └────────────┘
```

### 2.2 关键集成测试

| 测试 | 覆盖范围 | 依赖 |
|------|---------|------|
| `MockProviderFlowTest` | 会话创建 → 消息 → ReAct → 响应 | Mock Provider |
| `HttpApiIntegrationTest` | 真实 HTTP 调用 `/api/v1` | 启动服务 |
| `ScheduledTaskE2ETest` | 定时任务 → Agent 执行 → 历史记录 | Mock Provider |
| `SandboxSecurityTest` | 白名单检查（通过/拒绝） | 真实 Sandbox |
| `MemoryPersistenceTest` | 记忆保存 → 重启 → 仍可查询 | SQLite |

### 2.3 集成测试示例

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HttpApiIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestClient client = RestClient.create();

    @Test
    void shouldCompleteFullPipeline() {
        String baseUrl = "http://localhost:" + port + "/api/v1";

        // 1. 健康检查
        var health = client.get().uri(baseUrl + "/health")
            .retrieve().body(Map.class);
        assertThat(health.get("status")).isEqualTo("UP");

        // 2. 创建会话（随机名称避免冲突）
        String sessionName = "test-" + UUID.randomUUID().toString().substring(0, 8);
        var session = client.post().uri(baseUrl + "/sessions")
            .body(Map.of("profileName", sessionName))
            .retrieve().body(Map.class);
        String sessionId = (String) session.get("data").get("sessionId");

        // 3. 发送消息
        var response = client.post()
            .uri(baseUrl + "/sessions/" + sessionId + "/messages")
            .body(Map.of("message", "你好"))
            .retrieve().body(Map.class);

        // 4. 验证响应
        assertThat(response.get("code")).isEqualTo(200);
        assertThat((String) response.get("data")).isNotEmpty();

        // 5. 验证会话可查询
        var sessions = client.get().uri(baseUrl + "/sessions")
            .retrieve().body(Map.class);
        assertThat(sessions.get("data")).isNotNull();
    }
}
```

---

## 三、发行版打包

### 3.1 make release

```bash
make release VERSION=1.0.0
```

产出结构：
```
release/oryxos-1.0.0/
├── bin/
│   ├── oryxos-server     # 启动脚本（start/stop）
│   └── oryxos-cli        # CLI 入口
├── config/
│   ├── application.yml           # 默认配置
│   └── application.yml.example   # 安全模板
├── libs/
│   └── oryxos-boot-1.0.0.jar    # 可执行 JAR
├── README.md
└── LICENSE
```

打包为 `.tar.gz`：
```bash
tar -czf oryxos-1.0.0.tar.gz oryxos-1.0.0/
```

### 3.2 Makefile

```makefile
VERSION ?= 1.0.0-SNAPSHOT
RELEASE_DIR = release/oryxos-$(VERSION)

.PHONY: clean build release

clean:
	mvn clean

build:
	mvn package -DskipTests -Drevision=$(VERSION)

release: clean build
	@echo "Building release $(VERSION)..."
	mkdir -p $(RELEASE_DIR)/bin $(RELEASE_DIR)/config $(RELEASE_DIR)/libs

	# Copy JAR
	cp oryxos-boot/target/oryxos-boot-$(VERSION).jar $(RELEASE_DIR)/libs/

	# Copy scripts
	cp bin/start.sh $(RELEASE_DIR)/bin/oryxos-server
	cp bin/stop.sh $(RELEASE_DIR)/bin/
	chmod +x $(RELEASE_DIR)/bin/*

	# Copy config
	cp config/application.yml $(RELEASE_DIR)/config/
	cp config/application.yml.example $(RELEASE_DIR)/config/

	# Copy docs
	cp README.md $(RELEASE_DIR)/
	cp LICENSE $(RELEASE_DIR)/ 2>/dev/null || true

	# Package
	cd release && tar -czf oryxos-$(VERSION).tar.gz oryxos-$(VERSION)/
	@echo "Release: release/oryxos-$(VERSION).tar.gz"
```

---

## 四、GitHub Actions CI/CD

### 4.1 CI 工作流

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
      - name: Build
        run: mvn clean package -DskipTests
      - name: Test
        run: mvn test
      - name: Verify
        run: mvn verify
```

### 4.2 Release 工作流

```yaml
# .github/workflows/release.yml
name: Release

on:
  pull_request:
    types: [closed]
    branches: [main]

jobs:
  release:
    if: startsWith(github.event.pull_request.title, 'release:') &&
        github.event.pull_request.merged == true
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin

      - name: Extract version
        id: version
        run: |
          VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
          echo "version=$VERSION" >> $GITHUB_OUTPUT

      - name: Build release
        run: make release VERSION=${{ steps.version.outputs.version }}

      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          tag_name: v${{ steps.version.outputs.version }}
          files: release/oryxos-${{ steps.version.outputs.version }}.tar.gz
          generate_release_notes: true
```

---

## 五、版本管理

### 5.1 语义化版本

```
MAJOR.MINOR.PATCH-QUALIFIER

0.1.0-SNAPSHOT   → 开发快照
0.1.0-RELEASE    → 第一个可用版本
0.1.1-RELEASE    → Bug 修复
0.2.0-RELEASE    → 新功能
1.0.0-RELEASE    → 正式发布
```

### 5.2 Maven 版本号更新

```bash
# 设置版本
mvn versions:set -DnewVersion=0.2.0-RELEASE

# 验证
mvn validate

# 提交
git add . && git commit -m "release: 0.2.0-RELEASE"
```

---

## 六、Harness：如何验收

### 编译 + 测试
```bash
mvn clean verify
```

### 打包
```bash
make release VERSION=1.0.0

# 验证打包结果
tar -tzf release/oryxos-1.0.0.tar.gz
# bin/  config/  libs/  README.md
```

### 运行验证
```bash
# 解压 + 启动
tar -xzf release/oryxos-1.0.0.tar.gz
cd oryxos-1.0.0
bin/oryxos-server start --port 8080

# 验证服务
curl http://localhost:8080/api/v1/health
curl http://localhost:8080/admin/

# 停止
bin/oryxos-server stop
```

### 检查点
- [x] `mvn clean verify` 全部通过
- [x] `make release` 产出正确的 `.tar.gz`
- [x] 解压后 `bin/oryxos-server start` 可启动
- [x] 管理台可访问
- [x] CI 工作流正常
- [x] Release 工作流正常触发
