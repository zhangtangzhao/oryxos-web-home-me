package com.oryxos.core.llm;

/**
 * Port from the ReAct loop to the LLM layer. Implemented by the provider module —
 * core never touches Spring AI directly (constitution II).
 */
public interface LlmClient {

    LlmResult complete(LlmRequest request);
}
