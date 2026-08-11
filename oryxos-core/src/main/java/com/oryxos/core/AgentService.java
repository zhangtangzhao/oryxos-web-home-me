package com.oryxos.core;

/**
 * Unified entry point for all trigger sources (CLI, Web Service, AgentScheduler).
 * Processes a user message within a session context via the ReAct loop.
 */
public interface AgentService {

    /**
     * Process a user message within the given session.
     *
     * @param session the conversation session
     * @param userMessage the user's input message
     * @return the agent's final response
     */
    String process(Session session, String userMessage);
}
