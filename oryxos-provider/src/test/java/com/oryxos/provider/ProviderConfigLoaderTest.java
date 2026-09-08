package com.oryxos.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T053 (FR-008): 配置校验必须聚合报告全部问题、点名字段、给出修复指引，
 * 而不是遇到首个错误即中断。
 */
class ProviderConfigLoaderTest {

    private static ProviderProperties.Def def(String name, String baseUrl, String env) {
        ProviderProperties.Def d = new ProviderProperties.Def();
        d.setName(name);
        d.setBaseUrl(baseUrl);
        d.setApiKeyEnv(env);
        return d;
    }

    @Test
    void validDefsPassThroughWithoutProblems() {
        ProviderConfigLoader.Result result = ProviderConfigLoader.validate(List.of(
                def("deepseek", "https://api.deepseek.com", "DEEPSEEK_API_KEY"),
                def("kimi", "https://api.moonshot.cn/v1", "MOONSHOT_API_KEY")));
        assertTrue(result.problems().isEmpty());
        assertEquals(2, result.validDefs().size());
    }

    @Test
    void emptyConfigIsReportedAsSingleProblem() {
        assertTrue(ProviderConfigLoader.validate(List.of()).problems().size() == 1);
        assertTrue(ProviderConfigLoader.validate(null).problems().size() == 1);
        assertTrue(ProviderConfigLoader.validate(null).problems().get(0).message().contains("oryxos.providers"));
    }

    @Test
    void everyMissingFieldIsNamedInOnePass() {
        ProviderConfigLoader.Result result = ProviderConfigLoader.validate(List.of(def(null, null, null)));
        assertEquals(3, result.problems().size(), "一次性报出全部缺失字段");
        String joined = result.problems().toString();
        assertTrue(joined.contains("name"));
        assertTrue(joined.contains("base-url"));
        assertTrue(joined.contains("api-key-env"));
    }

    @Test
    void invalidBaseUrlSchemeReportsCurrentValue() {
        ProviderConfigLoader.Result result = ProviderConfigLoader.validate(List.of(
                def("bad", "ftp://x.example", "ENV")));
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().get(0).message().contains("ftp://x.example"));
        assertTrue(result.problems().get(0).message().contains("https://"));
    }

    @Test
    void duplicateNamesAreReportedNotSilentlyOverwritten() {
        ProviderConfigLoader.Result result = ProviderConfigLoader.validate(List.of(
                def("kimi", "https://api.moonshot.cn/v1", "MOONSHOT_API_KEY"),
                def("kimi", "https://api.moonshot.cn/v1", "MOONSHOT_API_KEY")));
        assertEquals(1, result.problems().size());
        assertTrue(result.problems().get(0).message().contains("重复"));
        assertEquals(1, result.validDefs().size());
    }

    @Test
    void mixedValidAndInvalidDefsKeepValidOnesUsable() {
        ProviderConfigLoader.Result result = ProviderConfigLoader.validate(List.of(
                def("ok", "https://api.deepseek.com", "DEEPSEEK_API_KEY"),
                def("bad", "no-scheme", "X")));
        assertEquals(1, result.problems().size());
        assertEquals(1, result.validDefs().size());
        assertEquals("ok", result.validDefs().get(0).getName());
    }
}
