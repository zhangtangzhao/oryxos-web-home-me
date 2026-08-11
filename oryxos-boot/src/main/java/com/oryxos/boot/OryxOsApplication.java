package com.oryxos.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * OryxOS Spring Boot main class.
 * Launches the full Agent OS runtime: REST API, scheduler, and all capabilities.
 *
 * Run with: java -jar oryxos-boot-1.0.0-SNAPSHOT.jar <command>
 */
@SpringBootApplication(scanBasePackages = {
    "com.oryxos.core",
    "com.oryxos.provider",
    "com.oryxos.memory",
    "com.oryxos.tool",
    "com.oryxos.channel",
    "com.oryxos.web",
    "com.oryxos.storage",
    "com.oryxos.cli",
    "com.oryxos.boot"
})
@EnableJpaRepositories(basePackages = "com.oryxos.storage")
@EntityScan(basePackages = "com.oryxos.storage")
public class OryxOsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OryxOsApplication.class, args);
    }
}
