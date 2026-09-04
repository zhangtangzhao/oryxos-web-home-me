package com.oryxos.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Sandbox whitelist configuration from oryxos.sandbox in application.yml
 * (constitution IV). Defaults = minimal privilege inside the workspace.
 */
@ConfigurationProperties(prefix = "oryxos.sandbox")
public class SandboxProperties {

    private FileRules file = new FileRules();
    private ShellRules shell = new ShellRules();
    private HttpRules http = new HttpRules();

    public FileRules getFile() { return file; }
    public void setFile(FileRules file) { this.file = file; }
    public ShellRules getShell() { return shell; }
    public void setShell(ShellRules shell) { this.shell = shell; }
    public HttpRules getHttp() { return http; }
    public void setHttp(HttpRules http) { this.http = http; }

    public static class FileRules {
        private List<String> allowedPaths = new ArrayList<>();
        public List<String> getAllowedPaths() { return allowedPaths; }
        public void setAllowedPaths(List<String> allowedPaths) { this.allowedPaths = allowedPaths; }
    }

    public static class ShellRules {
        private List<String> allowedCommands = new ArrayList<>();
        public List<String> getAllowedCommands() { return allowedCommands; }
        public void setAllowedCommands(List<String> allowedCommands) { this.allowedCommands = allowedCommands; }
    }

    public static class HttpRules {
        private List<String> allowedDomains = new ArrayList<>();
        public List<String> getAllowedDomains() { return allowedDomains; }
        public void setAllowedDomains(List<String> allowedDomains) { this.allowedDomains = allowedDomains; }
    }
}
