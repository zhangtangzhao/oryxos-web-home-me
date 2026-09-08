package com.oryxos.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FR-008: provider 配置启动期聚合校验 —— 一次性报告全部问题，每条都点名
 * 出错字段并给出修复指引，而不是遇到首个错误就中断。
 */
public final class ProviderConfigLoader {

    private ProviderConfigLoader() {
    }

    /** 一处配置问题：出错字段 + 含修复指引的说明。 */
    public record Problem(String providerName, String field, String message) {
        @Override
        public String toString() {
            return "[" + providerName + "] " + field + ": " + message;
        }
    }

    /** 校验结果：合法定义 + 全部问题（调用方对非空 problems 聚合抛错）。 */
    public record Result(List<ProviderProperties.Def> validDefs, List<Problem> problems) {
    }

    public static Result validate(List<ProviderProperties.Def> defs) {
        List<ProviderProperties.Def> valid = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        if (defs == null || defs.isEmpty()) {
            problems.add(new Problem("(配置)", "oryxos.providers",
                    "未配置任何 Provider → 在 config/application.yml 的 oryxos.providers 下至少定义一项（name/base-url/api-key-env）"));
            return new Result(valid, problems);
        }

        for (int i = 0; i < defs.size(); i++) {
            ProviderProperties.Def def = defs.get(i);
            String label = "providers[" + i + "]";
            int before = problems.size();
            String name = def.getName() == null ? "" : def.getName().trim();

            if (name.isEmpty()) {
                problems.add(new Problem(label, "name",
                        "缺失或为空 → 填写唯一 Provider 名称（如 deepseek/kimi/qwen）"));
            } else if (!seenNames.add(name)) {
                problems.add(new Problem(name, "name",
                        "重复定义 → 同名 Provider 只允许注册一次，请重命名或删除多余项"));
            }
            String who = name.isEmpty() ? label : name;

            String baseUrl = def.getBaseUrl() == null ? "" : def.getBaseUrl().trim();
            if (baseUrl.isEmpty()) {
                problems.add(new Problem(who, "base-url",
                        "缺失或为空 → 填写 OpenAI 兼容端点（如 https://api.deepseek.com）"));
            } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                problems.add(new Problem(who, "base-url",
                        "格式非法（须以 http:// 或 https:// 开头），当前值: " + baseUrl));
            }

            String env = def.getApiKeyEnv() == null ? "" : def.getApiKeyEnv().trim();
            if (env.isEmpty()) {
                problems.add(new Problem(who, "api-key-env",
                        "缺失或为空 → 填写存放 API Key 的环境变量名（如 DEEPSEEK_API_KEY），密钥本身绝不写入配置文件"));
            }

            if (problems.size() == before) {
                valid.add(def);
            }
        }
        return new Result(valid, problems);
    }
}
