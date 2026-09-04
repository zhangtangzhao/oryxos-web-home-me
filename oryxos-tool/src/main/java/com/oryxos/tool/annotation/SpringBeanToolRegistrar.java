package com.oryxos.tool.annotation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;
import com.oryxos.core.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.Map;

/**
 * Scans the Spring context for beans annotated {@link PluginTool} and
 * registers each as an {@code OryxTool} in the shared ToolRegistry (FR-015
 * 方式三). The input schema is derived from the bean's public execution
 * method; invocation is reflective. Because the wrapper lives in the
 * ToolRegistry, every call flows through ToolExecutor — sandbox + audit are
 * mandatory, there is no bypass path (FR-011).
 */
@Component
public class SpringBeanToolRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SpringBeanToolRegistrar.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApplicationContext applicationContext;
    private final ToolRegistry registry;

    public SpringBeanToolRegistrar(ApplicationContext applicationContext, ToolRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
        registerAll();
    }

    private void registerAll() {
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Class<?> beanType;
            try {
                beanType = applicationContext.getType(beanName);
            } catch (Exception e) {
                continue;
            }
            if (beanType == null || !beanType.isAnnotationPresent(PluginTool.class)) {
                continue;
            }
            PluginTool annotation = beanType.getAnnotation(PluginTool.class);
            try {
                Object bean = applicationContext.getBean(beanName);
                Method entry = executionMethod(beanType);
                registry.register(new ReflectiveTool(bean, entry, annotation.name(), annotation.description()));
                log.info("已注册 @PluginTool 工具: {}（bean={})", annotation.name(), beanName);
            } catch (Exception e) {
                log.warn("@PluginTool 注册失败: name={} bean={} 原因: {}",
                        annotation.name(), beanName, e.getMessage());
            }
        }
    }

    /** The single public method (excluding Object methods) is the execution entry. */
    private static Method executionMethod(Class<?> type) {
        Method found = null;
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            if (found != null) {
                throw new IllegalStateException(
                        "bean 必须只暴露一个公共执行方法: " + type.getName() + " 同时有 " + found.getName()
                                + " 和 " + method.getName());
            }
            found = method;
        }
        if (found == null) {
            throw new IllegalStateException("bean 没有公共执行方法: " + type.getName());
        }
        return found;
    }

    static class ReflectiveTool implements OryxTool {

        private final Object bean;
        private final Method entry;
        private final String name;
        private final String description;

        ReflectiveTool(Object bean, Method entry, String name, String description) {
            this.bean = bean;
            this.entry = entry;
            this.name = name;
            this.description = description == null || description.isBlank()
                    ? "业务方自定义工具 " + name
                    : description;
            try {
                this.entry.setAccessible(true);
            } catch (Exception ignored) {
                // module restrictions may deny it; invoke will surface the real error
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public JsonNode getInputSchema() {
            ObjectNode schema = MAPPER.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            Parameter[] parameters = entry.getParameters();
            if (parameters.length == 1 && Map.class.isAssignableFrom(parameters[0].getType())) {
                properties.putObject("input").put("type", "object")
                        .put("description", "自由格式参数对象");
                schema.putArray("required").add("input");
                return schema;
            }
            for (Parameter parameter : parameters) {
                properties.putObject(parameter.getName())
                        .put("type", jsonType(parameter.getType()))
                        .put("description", parameter.getName());
            }
            if (parameters.length > 0) {
                var required = schema.putArray("required");
                for (Parameter parameter : parameters) {
                    required.add(parameter.getName());
                }
            }
            return schema;
        }

        @Override
        public ToolResult execute(JsonNode input) {
            try {
                Object[] args;
                Parameter[] parameters = entry.getParameters();
                if (parameters.length == 1 && Map.class.isAssignableFrom(parameters[0].getType())) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = MAPPER.convertValue(input, Map.class);
                    args = new Object[]{map};
                } else {
                    args = new Object[parameters.length];
                    for (int i = 0; i < parameters.length; i++) {
                        JsonNode value = input.get(parameters[i].getName());
                        args[i] = value == null || value.isNull() ? null
                                : MAPPER.convertValue(value, parameters[i].getType());
                    }
                }
                Object result = entry.invoke(bean, args);
                return ToolResult.success(result == null ? "（无返回值）" : String.valueOf(result));
            } catch (java.lang.reflect.InvocationTargetException e) {
                return ToolResult.failure("工具执行失败: " + e.getCause().getMessage(), true);
            } catch (Exception e) {
                return ToolResult.failure("工具调用失败: " + e.getMessage(), true);
            }
        }

        private static String jsonType(Class<?> type) {
            if (type == int.class || type == long.class || type == Integer.class || type == Long.class) {
                return "integer";
            }
            if (Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class)) {
                return "number";
            }
            if (type == boolean.class || Boolean.class.isAssignableFrom(type)) {
                return "boolean";
            }
            if (Collection.class.isAssignableFrom(type) || type.isArray()) {
                return "array";
            }
            if (Map.class.isAssignableFrom(type)) {
                return "object";
            }
            return "string";
        }
    }
}
