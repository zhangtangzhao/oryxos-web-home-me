package com.oryxos.provider;

import com.oryxos.core.Profile;

import java.util.List;
import java.util.Map;

/**
 * Unified LLM provider abstraction.
 * Maintains explicit name-to-ChatModel mapping — never relies on type scanning.
 */
public interface ProviderService {

    /**
     * Get the list of registered provider names.
     */
    List<String> getProviderNames();

    /**
     * Call the LLM with the given messages and profile configuration.
     *
     * @param profile the agent profile (specifies which provider/model to use)
     * @param messages the conversation messages as prompt input
     * @param toolsJson the available tools in Function Calling JSON schema format
     * @return the LLM response (text + optional tool calls)
     */
    LlmResponse call(Profile profile, List<Map<String, Object>> messages, String toolsJson);
}
