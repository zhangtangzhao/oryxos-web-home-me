package com.oryxos.cli;

import com.oryxos.core.workspace.WorkspaceInitializer;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * oryxos provider list — FR-008/022: 列出真实配置中的 Provider（name/base_url/
 * 密钥环境变量名 + 设置状态），绝不回显密钥值。轻命令：不起 Spring，直接读
 * 外部 config/application.yml（优先）或 classpath application.yml。
 */
@Command(name = "provider", description = "Manage LLM providers",
         subcommands = ProviderCommand.ListCommand.class)
public class ProviderCommand {

    @Command(name = "list", description = "List configured providers (never echoes secrets)")
    public static class ListCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            Path external = Path.of("config", "application.yml");
            Map<String, Object> externalRoot = Files.isRegularFile(external)
                    ? loadYaml(quietRead(external)) : null;
            Map<String, Object> root;
            String source;
            if (externalRoot != null && !flattenProviders(externalRoot).isEmpty()) {
                root = externalRoot;
                source = external.toString();
            } else {
                Map<String, Object> classpathRoot = loadYaml(classpathApplicationYaml());
                if (classpathRoot == null || flattenProviders(classpathRoot).isEmpty()) {
                    System.err.println("未找到 Provider 配置：请在 " + external + " 的 oryxos.providers 下定义（name/base-url/api-key-env）");
                    return 1;
                }
                root = classpathRoot;
                source = "classpath:application.yml";
            }

            List<Map<String, Object>> providers = flattenProviders(root);
            if (providers.isEmpty()) {
                System.err.println("配置中未定义任何 Provider（oryxos.providers 为空）→ 请在 " + source + " 中添加");
                return 1;
            }

            System.out.println("已配置 Provider（来源: " + source + "）:");
            for (Map<String, Object> p : providers) {
                String name = str(p.get("name"));
                String baseUrl = str(firstNonNull(p.get("base-url"), p.get("baseUrl")));
                String env = str(firstNonNull(p.get("api-key-env"), p.get("apiKeyEnv")));
                boolean present = !env.isEmpty() && System.getenv(env) != null && !System.getenv(env).isBlank();
                System.out.printf("  %-12s base_url=%-45s 密钥环境变量=%-20s [%s]%n",
                        name, baseUrl, env, present ? "✓ 已设置" : "✗ 未设置");
            }

            printUsageFromAgents();
            System.out.println("（密钥值永不回显，仅显示环境变量名与设置状态 — FR-022）");
            return 0;
        }

        /** 扫描工作区各 Agent 目录的 AGENT.md frontmatter，报告各 Provider 下的模型使用情况。 */
        private void printUsageFromAgents() {
            Path agentsDir = WorkspaceInitializer.resolveRoot().resolve("agents");
            if (!Files.isDirectory(agentsDir)) {
                return;
            }
            List<String> lines = new ArrayList<>();
            try (Stream<Path> dirs = Files.list(agentsDir)) {
                dirs.filter(Files::isDirectory).forEach(dir -> {
                    Path agentMd = dir.resolve("AGENT.md");
                    if (!Files.isRegularFile(agentMd)) {
                        return;
                    }
                    Map<String, Object> fm = frontmatter(quietRead(agentMd));
                    if (fm == null) {
                        return;
                    }
                    Object provider = fm.get("provider");
                    if (provider instanceof Map<?, ?> p) {
                        String agentName = str(firstNonNull(fm.get("name"), dir.getFileName().toString()));
                        lines.add("  " + String.format("%-12s → %s / %s", agentName,
                                str(p.get("name")), str(p.get("model"))));
                    }
                });
            } catch (IOException e) {
                return;
            }
            if (!lines.isEmpty()) {
                System.out.println("模型使用情况（agents/*/AGENT.md）:");
                lines.forEach(System.out::println);
            }
        }

        /** 提取 YAML frontmatter（首行 --- 与下一个 --- 之间）并解析为 Map。 */
        @SuppressWarnings("unchecked")
        static Map<String, Object> frontmatter(String text) {
            if (text == null || !text.startsWith("---")) {
                return null;
            }
            int end = text.indexOf("\n---", 3);
            if (end < 0) {
                return null;
            }
            Object parsed = new Yaml().load(text.substring(3, end));
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        }

        @SuppressWarnings("unchecked")
        static List<Map<String, Object>> flattenProviders(Map<String, Object> root) {
            Object oryxos = root.get("oryxos");
            if (!(oryxos instanceof Map) || !(((Map<String, Object>) oryxos).get("providers") instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map) {
                    out.add((Map<String, Object>) o);
                }
            }
            return out;
        }

        static Map<String, Object> loadYaml(String text) {
            if (text == null) {
                return null;
            }
            try {
                Object parsed = new Yaml().load(text);
                return parsed instanceof Map ? (Map<String, Object>) parsed : null;
            } catch (Exception e) {
                System.err.println("YAML 解析失败: " + e.getMessage());
                return null;
            }
        }

        static String classpathApplicationYaml() {
            try (InputStream in = ListCommand.class.getResourceAsStream("/application.yml")) {
                return in == null ? null : new String(in.readAllBytes());
            } catch (IOException e) {
                return null;
            }
        }

        private static String quietRead(Path path) {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                return null;
            }
        }

        private static Object firstNonNull(Object a, Object b) {
            return a != null ? a : b;
        }

        private static String str(Object o) {
            return o == null ? "" : String.valueOf(o);
        }
    }
}
