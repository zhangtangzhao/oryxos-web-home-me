package com.oryxos.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider definitions from oryxos.providers in application.yml:
 * explicit name -> {base-url, api-key-env} mapping (constitution III/VI).
 */
@ConfigurationProperties(prefix = "oryxos")
public class ProviderProperties {

    private List<Def> providers = new ArrayList<>();

    public List<Def> getProviders() { return providers; }
    public void setProviders(List<Def> providers) { this.providers = providers; }

    public static class Def {
        private String name;
        private String baseUrl;
        private String apiKeyEnv;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKeyEnv() { return apiKeyEnv; }
        public void setApiKeyEnv(String apiKeyEnv) { this.apiKeyEnv = apiKeyEnv; }
    }
}
