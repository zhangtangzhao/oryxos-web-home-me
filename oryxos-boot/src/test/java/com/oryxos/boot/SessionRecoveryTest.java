package com.oryxos.boot;

import com.oryxos.core.Message;
import com.oryxos.core.Session;
import com.oryxos.core.session.SessionService;
import com.oryxos.core.session.SessionStore;
import com.oryxos.storage.JpaSessionStore;
import com.oryxos.storage.SessionRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T036 (FR-017, SC-007): sessions persist to SQLite, and a fresh store
 * instance — like a restarted process — reloads session id and message
 * history, so conversation continues across restarts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"oryxos.root=target/ws-recovery"})
class SessionRecoveryTest {

    private static final String AGENT = "recobot";

    @Autowired
    SessionService sessionService;
    @Autowired
    SessionRepository sessionRepository;

    @BeforeAll
    static void createWorkspace() throws Exception {
        Path root = Path.of("target/ws-recovery");
        Files.createDirectories(root.resolve("agents").resolve(AGENT));
        Path agentMd = root.resolve("agents").resolve(AGENT).resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            Files.writeString(agentMd, String.join("\n",
                    "---",
                    "name: " + AGENT,
                    "description: recovery test agent",
                    "provider:",
                    "  name: deepseek",
                    "  model: deepseek-chat",
                    "tools: []",
                    "bootstrap: []",
                    "---",
                    "",
                    "测试用 Agent。"));
        }
    }

    @Test
    void sessionAndHistorySurviveRestart() {
        String user = "rec-" + System.nanoTime();
        Session session = sessionService.getOrCreate("cli", user, AGENT);
        session.addMessage(Message.user("记住我喜欢美式咖啡"));
        session.addMessage(Message.assistant("好的，已记住。"));
        sessionService.save(session);

        SessionStore restartedStore = new JpaSessionStore(sessionRepository);
        Session reloaded = restartedStore.findById(session.getSessionId()).orElseThrow();

        assertEquals(2, reloaded.getMessages().size());
        assertEquals(Message.Role.USER, reloaded.getMessages().get(0).getRole());
        assertEquals("记住我喜欢美式咖啡", reloaded.getMessages().get(0).getContent());
        assertEquals(Session.SessionStatus.ACTIVE, reloaded.getStatus());

        Session continued = sessionService.getOrCreate("cli", user, AGENT);
        assertEquals(session.getSessionId(), continued.getSessionId());
        assertEquals(2, continued.getMessages().size());
    }
}
