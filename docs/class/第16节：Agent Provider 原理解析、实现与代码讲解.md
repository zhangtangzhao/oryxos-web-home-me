# 第16节：Agent Provider 原理解析、实现与代码讲解

> **课时目标**：理解 LLM Provider 抽象层的设计原理，完成 `oryxos-provider` 模块的实现，使 OryxOS 能够统一对接多个大模型厂商。

---

## 一、本节目的

OryxOS 作为 Agent OS，需要对接多种大模型（DeepSeek、通义千问、Kimi、OpenAI、Anthropic 等）。Provider 层解决一个问题：

> **让 Agent 不感知具体厂商，运行时自由切换模型，不被任何一家锁定（no vendor lock-in）。**

本节完成后：
- `oryxos-provider` 模块可编译运行
- 支持通过 YAML 配置声明多个 Provider
- 运行时按名称获取对应的 ChatModel 实例
- CLI 可列出已配置的 Provider

---

## 二、原理：为什么需要 Provider 抽象？

### 2.1 业界现状

| 厂商 | 协议 | SDK |
|------|------|-----|
| OpenAI | OpenAI Chat Completions API | openai-java |
| DeepSeek | OpenAI 兼容协议 | 无官方 Java SDK |
| 通义千问 | OpenAI 兼容 + 阿里云 DashScope | dashscope-sdk-java |
| Kimi (Moonshot) | OpenAI 兼容协议 | 无官方 Java SDK |
| Anthropic | Anthropic Messages API | anthropic-java |
| 智谱 (GLM) | OpenAI 兼容协议 | zhipuai-sdk |

大多数国产模型厂商选择 **OpenAI 兼容协议**，这大大降低了适配成本。

### 2.2 Spring AI 的角色

Spring AI 提供了统一的 `ChatModel` 接口（`org.springframework.ai.chat.model.ChatModel`）：

```java
public interface ChatModel {
    ChatResponse call(Prompt prompt);
    Flux<ChatResponse> stream(Prompt prompt);
}
```

OryxOS 使用 Spring AI 做**协议转换**（把各家 API 转成统一的 `ChatModel`），但**不用 Spring AI 的 Agent 框架**。核心循环由 OryxOS 自实现的 ReAct Loop 控制。

### 2.3 关键设计决策：显式 Map 映射

**问题**：多 Provider 并存时，如果都用 `@Autowired ChatModel`，Spring 不知道该注入哪个。

**方案**：使用 `Map<String, ChatModel>` 显式映射，按名称获取：

```java
// ✅ 正确：显式按名映射
@Autowired
Map<String, ChatModel> chatModels;  // key = provider name

// ❌ 错误：类型扫描歧义
@Autowired
ChatModel chatModel;  // 多个 Bean 时崩溃
```

---

## 三、设计与架构

### 3.1 模块结构

```
oryxos-provider/
├── pom.xml
└── src/main/java/com/oryxos/provider/
    ├── ProviderProperties.java        # 配置模型
    ├── ProviderService.java           # 核心服务接口
    ├── DefaultProviderService.java    # 默认实现
    └── ProviderAutoConfiguration.java # 自动配置
```

### 3.2 架构图

```
┌──────────────────────────────────────────────┐
│                 OryxOS Core                    │
│  ReActLoop → ToolExecutor → PromptBuilder     │
│                    │                           │
│              ProviderService                   │
│                    │                           │
├──────────────────────────────────────────────┤
│              OryxOS Provider                   │
│                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐    │
│  │ DeepSeek │  │  Qwen    │  │  Kimi    │    │
│  │ ChatModel│  │ ChatModel│  │ ChatModel│    │
│  └──────────┘  └──────────┘  └──────────┘    │
│         │             │             │          │
│    ┌────▼─────┐ ┌────▼─────┐ ┌────▼─────┐    │
│    │OpenAI    │ │OpenAI    │ │OpenAI    │    │
│    │兼容协议  │ │兼容协议  │ │兼容协议  │    │
│    └──────────┘ └──────────┘ └──────────┘    │
└──────────────────────────────────────────────┘
```

### 3.3 配置模型

```yaml
oryxos:
  providers:
    - name: deepseek
      base-url: https://api.deepseek.com
      api-key-env: DEEPSEEK_API_KEY
    - name: qwen
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key-env: DASHSCOPE_API_KEY
    - name: kimi
      base-url: https://api.moonshot.cn/v1
      api-key-env: MOONSHOT_API_KEY
```

- `name` — Provider 唯一标识，后续在 Profile 中引用
- `base-url` — API 端点地址
- `api-key-env` — API Key 所在的环境变量名（**绝不**在 YAML 中写明文 Key）

---

## 四、实现计划

| 步骤 | 任务 | 产出 |
|------|------|------|
| D1 | 创建 `oryxos-provider` 模块 | pom.xml、目录结构 |
| D2 | 实现 `ProviderProperties` | 配置绑定类 |
| D3 | 实现 `ProviderService` 接口 | 核心服务契约 |
| D4 | 实现 `DefaultProviderService` | 按名获取 ChatModel |
| D5 | 实现 `ProviderAutoConfiguration` | 自动创建 ChatModel Bean |
| D6 | 编写单元测试 | ProviderServiceTest |
| D7 | 集成到 CLI 和 Web | `oryxos provider list` 命令、管理台 |

---

## 五、代码讲解

### 5.1 ProviderProperties — 配置绑定

```java
@ConfigurationProperties(prefix = "oryxos")
public class ProviderProperties {
    private List<ProviderConfig> providers = new ArrayList<>();

    public static class ProviderConfig {
        private String name;        // provider 唯一标识
        private String baseUrl;     // API 端点
        private String apiKeyEnv;   // 环境变量名
        // getters / setters
    }
}
```

Spring Boot 自动将 `application.yml` 中的 `oryxos.providers` 列表绑定到 `List<ProviderConfig>`。

### 5.2 ProviderService — 核心服务

```java
public interface ProviderService {
    /** 列出所有已配置的 Provider 名称 */
    List<String> listProviders();

    /** 按名称获取 ChatModel */
    Optional<ChatModel> getChatModel(String providerName);

    /** 获取默认 Provider */
    ChatModel getDefaultChatModel();
}
```

### 5.3 DefaultProviderService — 默认实现

```java
@Service
public class DefaultProviderService implements ProviderService {

    private final Map<String, ChatModel> chatModels;

    public DefaultProviderService(Map<String, ChatModel> chatModels) {
        this.chatModels = chatModels;
    }

    @Override
    public List<String> listProviders() {
        return new ArrayList<>(chatModels.keySet());
    }

    @Override
    public Optional<ChatModel> getChatModel(String providerName) {
        return Optional.ofNullable(chatModels.get(providerName));
    }

    @Override
    public ChatModel getDefaultChatModel() {
        if (chatModels.isEmpty()) {
            throw new IllegalStateException("No provider configured");
        }
        return chatModels.values().iterator().next();
    }
}
```

### 5.4 ProviderAutoConfiguration — 自动配置

这是最关键的部分。它为每个 Provider 配置创建一个 `ChatModel` Bean：

```java
@Configuration
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderAutoConfiguration {

    @Bean
    public ProviderService providerService(Map<String, ChatModel> chatModels) {
        return new DefaultProviderService(chatModels);
    }

    // 为每个 provider 配置创建 ChatModel Bean
    @Bean
    Map<String, ChatModel> chatModels(ProviderProperties properties) {
        Map<String, ChatModel> map = new HashMap<>();
        for (ProviderConfig config : properties.getProviders()) {
            String apiKey = System.getenv(config.getApiKeyEnv());
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("Provider '{}': env var {} not set, skipping", 
                         config.getName(), config.getApiKeyEnv());
                continue;
            }
            var chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(config.getBaseUrl())
                .build();
            map.put(config.getName(), chatModel);
        }
        return map;
    }
}
```

### 5.5 模块间依赖

```
oryxos-cli ──→ oryxos-provider ──→ oryxos-core
oryxos-web ──→ oryxos-provider ──→ oryxos-core
oryxos-boot ──→ oryxos-provider (transitive)
```

---

## 六、Harness：如何验收

### Layer 1：编译门禁
```bash
cd oryxos-provider && mvn clean compile
```

### Layer 2：单元测试
```java
@Test
void shouldListConfiguredProviders() {
    List<String> providers = providerService.listProviders();
    assertThat(providers).contains("deepseek", "qwen", "kimi");
}

@Test
void shouldGetChatModelByName() {
    Optional<ChatModel> model = providerService.getChatModel("deepseek");
    assertThat(model).isPresent();
}

@Test
void shouldReturnEmptyForUnknownProvider() {
    Optional<ChatModel> model = providerService.getChatModel("unknown");
    assertThat(model).isEmpty();
}
```

### Layer 3：CLI 验收
```bash
java -jar oryxos-boot.jar provider list
# 预期输出：
#   deepseek
#   qwen
#   kimi
```

### Layer 4：管理台验收
- 启动服务 → 打开 `http://localhost:8080/admin/`
- 侧边栏 → OS 运行时 → Provider 列表
- 应显示所有已配置的 Provider

### Layer 5：宪法检查
- [x] Java 21 编译通过
- [x] 使用 `Map<String, ChatModel>` 显式映射（宪法 #3）
- [x] API Key 走环境变量，不写明文（宪法 #6）

---

## 七、常见问题

**Q：为什么 API Key 配置不成功？**
A：检查环境变量是否已设置：`echo $DEEPSEEK_API_KEY`。Provider 初始化时会跳过未设置环境变量的配置。

**Q：如何添加新的 Provider？**
A：在 `application.yml` 的 `oryxos.providers` 列表中新增一条，重启服务即可。如果新厂商使用非 OpenAI 兼容协议，需要在 `ProviderAutoConfiguration` 中添加对应的 ChatModel Builder。

**Q：Mock Provider 如何工作？**
A：OryxOS 内置一个 `mock` Provider（配置 `name: mock`），不需要真实 API Key，返回预设响应，用于开发和测试。
