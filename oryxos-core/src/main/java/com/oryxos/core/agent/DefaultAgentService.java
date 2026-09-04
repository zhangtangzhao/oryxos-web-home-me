package com.oryxos.core.agent;

import com.oryxos.core.AgentService;
import com.oryxos.core.Message;
import com.oryxos.core.OryxTool;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.ToolRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.SessionStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The unified orchestration chain every trigger source goes through:
 * Session -> Profile -> tools -> ReAct loop -> persisted reply (FR-020/021).
 */
@Service
public class DefaultAgentService implements AgentService {

    private final AgentLoader agentLoader;
    private final ReActLoop reActLoop;
    private final ToolRegistry toolRegistry;
    private final SessionStore sessionStore;

    public DefaultAgentService(AgentLoader agentLoader, ReActLoop reActLoop,
                               ToolRegistry toolRegistry, SessionStore sessionStore) {
        this.agentLoader = agentLoader;
        this.reActLoop = reActLoop;
        this.toolRegistry = toolRegistry;
        this.sessionStore = sessionStore;
    }

    @Override
    public String process(Session session, String userMessage) {
        Profile profile = agentLoader.require(session.getProfileName());
        session.addMessage(Message.user(userMessage));
        List<OryxTool> tools = toolRegistry.listForAgent(profile.getTools());
        String reply = reActLoop.run(session, profile, tools);
        if (!session.isEphemeral()) {
            sessionStore.save(session);
        }
        return reply;
    }
}
