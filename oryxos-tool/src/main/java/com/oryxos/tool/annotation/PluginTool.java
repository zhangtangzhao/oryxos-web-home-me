package com.oryxos.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Spring bean as a plugin tool (FR-015 方式三). Spring AI 1.0.0-M5 has
 * no tool-annotation framework, so OryxOS owns this annotation: the registrar
 * derives the input schema from the bean's single public execution method and
 * wraps the bean as an {@code OryxTool}. Execution always goes through the
 * ToolRegistry → ToolExecutor chain (Sandbox + audit, no bypass).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PluginTool {

    /** Unique function name exposed to the LLM (^[A-Za-z0-9_-]+$). */
    String name();

    /** Human-readable description injected into the prompt as tool documentation. */
    String description() default "";
}
