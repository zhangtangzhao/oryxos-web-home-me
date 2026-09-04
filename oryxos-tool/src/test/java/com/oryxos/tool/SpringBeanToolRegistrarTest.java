package com.oryxos.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;
import com.oryxos.core.ToolResult;
import com.oryxos.tool.annotation.PluginTool;
import com.oryxos.tool.annotation.SpringBeanToolRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-015 方式三: a business-side annotated bean is auto-registered into the
 * ToolRegistry; execution goes through the registry (→ ToolExecutor →
 * Sandbox + audit), never bypassing it.
 */
class SpringBeanToolRegistrarTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @PluginTool(name = "sample_upper", description = "把输入转大写")
    static class UpperCaseTool {
        public String execute(String text) {
            return text == null ? "" : text.toUpperCase();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class App {
        @Bean
        UpperCaseTool upperCaseTool() {
            return new UpperCaseTool();
        }

        @Bean
        ToolRegistry toolRegistry() {
            return new InMemoryToolRegistry();
        }

        @Bean
        SpringBeanToolRegistrar springBeanToolRegistrar(org.springframework.context.ApplicationContext ctx,
                                                        ToolRegistry registry) {
            return new SpringBeanToolRegistrar(ctx, registry);
        }
    }

    @Test
    void annotatedBeanIsRegisteredAndExecutableThroughRegistry() {
        new ApplicationContextRunner().withUserConfiguration(App.class).run(context -> {
            assertThat(context).hasNotFailed();
            OryxTool tool = context.getBean(ToolRegistry.class).get("sample_upper");
            assertThat(tool).isNotNull();
            assertThat(tool.getDescription()).isEqualTo("把输入转大写");

            var schema = tool.getInputSchema();
            assertThat(schema.path("properties").has("text")).isTrue();

            ToolResult result = tool.execute(MAPPER.valueToTree(Map.of("text", "abc")));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getContent()).isEqualTo("ABC");
        });
    }

    @Test
    void registrarIsIdempotentWhenNoBeansAreAnnotated() {
        new ApplicationContextRunner()
                .withBean(ToolRegistry.class, InMemoryToolRegistry::new)
                .withBean(SpringBeanToolRegistrar.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ToolRegistry.class).listAll()).isEmpty();
                });
    }
}
