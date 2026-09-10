package com.oryxos.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.Profile;
import com.oryxos.core.llm.LlmClient;
import com.oryxos.core.llm.LlmRequest;
import com.oryxos.core.llm.LlmResult;
import com.oryxos.core.llm.ToolCallSpec;
import com.oryxos.core.llm.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Explicit provider registry: provider name -> OpenAiApi client (constitution III —
 * no type scanning). Calls go straight to the protocol layer with raw FunctionTool
 * schemas: no FunctionCallback/ToolCallback is ever registered with the library, so
 * auto tool-execution is structurally impossible — tools run only in OryxOS's own
 * ToolExecutor (constitution II, R-9).
 */
@Component
public class DefaultProviderService implements ProviderService, LlmClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultProviderService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, OpenAiApi> clients = new ConcurrentHashMap<>();
    private final Map<String, String> apiKeyEnvByName = new LinkedHashMap<>();

    public DefaultProviderService(ProviderProperties properties) {
        ProviderConfigLoader.Result result = ProviderConfigLoader.validate(properties.getProviders());
        if (!result.problems().isEmpty()) {
            // FR-008: 启动失败必须点名全部非法字段，而不是只报第一个
            StringBuilder msg = new StringBuilder("Provider 配置校验失败，共 ")
                    .append(result.problems().size()).append(" 处：");
            for (ProviderConfigLoader.Problem p : result.problems()) {
                msg.append("\n  - ").append(p);
            }
            throw new IllegalStateException(msg.toString());
        }
        for (ProviderProperties.Def def : result.validDefs()) {
            String envName = def.getApiKeyEnv().trim();
            String key = System.getenv(envName);
            if (key == null || key.isBlank()) {
                log.warn("Provider [{}] 已跳过注册：环境变量 {} 未设置或为空（配置合法；补齐后重启即可启用）",
                        def.getName(), envName);
                continue;
            }
            // base-url 已含版本段（如 /v1），补全路径仅保留资源段
            OpenAiApi api = new OpenAiApi(
                    def.getBaseUrl(), key,
                    "/chat/completions", "/v1/embeddings",
                    RestClient.builder(), WebClient.builder(),
                    new DefaultResponseErrorHandler());
            clients.put(def.getName(), api);
            apiKeyEnvByName.put(def.getName(), envName);
            log.info("Provider [{}] 注册成功（{}）", def.getName(), def.getBaseUrl());
        }
    }

    @Override
    public List<String> getProviderNames() {
        return new ArrayList<>(clients.keySet());
    }

    @Override
    public LlmResponse call(Profile profile, List<Map<String, Object>> messages, String toolsJson) {
        LlmRequest request = new LlmRequest();
        request.setProviderName(profile.getProvider().getName());
        request.setModel(profile.getProvider().getModel());
        request.setTemperature(profile.getProvider().getTemperature());
        request.setMessages(messages);
        request.setTools(parseTools(toolsJson));
        LlmResult result = complete(request);

        LlmResponse response = new LlmResponse();
        response.setContent(result.getContent());
        response.setPromptTokens(result.getPromptTokens());
        response.setCompletionTokens(result.getCompletionTokens());
        response.setTotalTokens(result.getTotalTokens());
        response.setDurationMs(result.getDurationMs());
        if (result.hasToolCalls()) {
            List<LlmResponse.ToolCall> calls = new ArrayList<>();
            for (ToolCallSpec spec : result.getToolCalls()) {
                calls.add(new LlmResponse.ToolCall(spec.getId(), spec.getName(), spec.getArguments()));
            }
            response.setToolCalls(calls);
        }
        return response;
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        OpenAiApi api = clients.get(request.getProviderName());
        if (api == null) {
            throw new IllegalStateException("Provider '" + request.getProviderName() + "' 未注册或密钥缺失"
                    + "（请设置环境变量 " + apiKeyEnvByName.getOrDefault(request.getProviderName(),
                    request.getProviderName().toUpperCase() + "_API_KEY") + " 后重启）");
        }

        List<OpenAiApi.FunctionTool> tools = toFunctionTools(request.getTools());
        List<OpenAiApi.ChatCompletionMessage> completionMessages = toCompletionMessages(request.getMessages());
        Double temperature = request.getTemperature() == null ? null : request.getTemperature().doubleValue();
        // 规范构造器组件序：messages, model, store, metadata, frequencyPenalty, logitBias,
        // logprobs, topLogprobs, maxTokens, maxCompletionTokens, n, outputModalities,
        // audioParameters, presencePenalty, responseFormat, seed, serviceTier, stop,
        // stream, streamOptions, temperature, topP, tools, toolChoice, parallelToolCalls, user
        // stream 必须显式 false：M5 chatCompletionEntity 有 Assert.isTrue(!request.stream())，
        // 传 null 会在拆箱处 NPE
        OpenAiApi.ChatCompletionRequest completionRequest = new OpenAiApi.ChatCompletionRequest(
                completionMessages, request.getModel(),
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, false, null,
                temperature, null, tools.isEmpty() ? null : tools, null, null, null);

        long start = System.currentTimeMillis();
        OpenAiApi.ChatCompletion completion;
        try {
            completion = api.chatCompletionEntity(completionRequest).getBody();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "LLM 调用失败（provider=" + request.getProviderName() + ", model=" + request.getModel() + "）: "
                            + e.getMessage(), e);
        }
        long durationMs = System.currentTimeMillis() - start;

        LlmResult result = new LlmResult();
        result.setDurationMs(durationMs);
        if (completion.usage() != null) {
            result.setPromptTokens(nvl(completion.usage().promptTokens()));
            result.setCompletionTokens(nvl(completion.usage().completionTokens()));
            result.setTotalTokens(nvl(completion.usage().totalTokens()));
        }
        if (completion.choices() == null || completion.choices().isEmpty()) {
            result.setContent("");
            return result;
        }
        OpenAiApi.ChatCompletionMessage message = completion.choices().get(0).message();
        result.setContent(message.content());
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            List<ToolCallSpec> calls = new ArrayList<>();
            for (OpenAiApi.ChatCompletionMessage.ToolCall tc : message.toolCalls()) {
                calls.add(new ToolCallSpec(tc.id(),
                        tc.function() == null ? null : tc.function().name(),
                        parseArguments(tc.function() == null ? null : tc.function().arguments())));
            }
            result.setToolCalls(calls);
        }
        return result;
    }

    private List<OpenAiApi.ChatCompletionMessage> toCompletionMessages(List<Map<String, Object>> raw) {
        List<OpenAiApi.ChatCompletionMessage> messages = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            String role = String.valueOf(m.get("role"));
            String content = m.get("content") == null ? "" : String.valueOf(m.get("content"));
            switch (role) {
                case "system" -> messages.add(new OpenAiApi.ChatCompletionMessage(
                        content, OpenAiApi.ChatCompletionMessage.Role.SYSTEM));
                case "user" -> messages.add(new OpenAiApi.ChatCompletionMessage(
                        content, OpenAiApi.ChatCompletionMessage.Role.USER));
                case "assistant" -> messages.add(new OpenAiApi.ChatCompletionMessage(
                        content, OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                        null, null, parseToolCalls(m.get("tool_calls_json")), null, null));
                case "tool" -> messages.add(new OpenAiApi.ChatCompletionMessage(
                        content, OpenAiApi.ChatCompletionMessage.Role.TOOL,
                        m.get("name") == null ? null : String.valueOf(m.get("name")),
                        m.get("tool_call_id") == null ? null : String.valueOf(m.get("tool_call_id")),
                        null, null, null));
                default -> throw new IllegalArgumentException("未知消息角色: " + role);
            }
        }
        return messages;
    }

    @SuppressWarnings("unchecked")
    private List<OpenAiApi.ChatCompletionMessage.ToolCall> parseToolCalls(Object callsJson) {
        if (!(callsJson instanceof String json) || json.isBlank()) {
            return null;
        }
        try {
            List<Map<String, Object>> list = MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Map.class));
            List<OpenAiApi.ChatCompletionMessage.ToolCall> calls = new ArrayList<>();
            for (Map<String, Object> c : list) {
                String args = c.get("arguments") == null ? "{}" : MAPPER.writeValueAsString(c.get("arguments"));
                calls.add(new OpenAiApi.ChatCompletionMessage.ToolCall(
                        (String) c.get("id"), "function",
                        new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction((String) c.get("name"), args)));
            }
            return calls;
        } catch (Exception e) {
            throw new IllegalStateException("tool_calls 反序列化失败", e);
        }
    }

    private List<OpenAiApi.FunctionTool> toFunctionTools(List<ToolSpec> specs) {
        List<OpenAiApi.FunctionTool> tools = new ArrayList<>();
        for (ToolSpec spec : specs) {
            // Spring AI M5 FunctionTool.Function 构造序是 (description, name, jsonSchema)，
            // 不是 (name, description, ...)——顺序错会把工具名和描述对调、真实模型无法按名调用
            tools.add(new OpenAiApi.FunctionTool(new OpenAiApi.FunctionTool.Function(
                    spec.getDescription() == null ? "" : spec.getDescription(),
                    spec.getName(),
                    spec.getInputSchemaJson() == null ? "{}" : spec.getInputSchemaJson())));
        }
        return tools;
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.warn("工具参数不是合法 JSON，按原文传递: {}", argumentsJson);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("_raw", argumentsJson);
            return fallback;
        }
    }

    private List<ToolSpec> parseTools(String toolsJson) {
        if (toolsJson == null || toolsJson.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> list = MAPPER.readValue(toolsJson, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Map.class));
            List<ToolSpec> specs = new ArrayList<>();
            for (Map<String, Object> t : list) {
                specs.add(new ToolSpec(
                        (String) t.get("name"),
                        (String) t.get("description"),
                        t.get("input_schema") == null ? "{}" : MAPPER.writeValueAsString(t.get("input_schema"))));
            }
            return specs;
        } catch (Exception e) {
            throw new IllegalStateException("toolsJson 解析失败", e);
        }
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
