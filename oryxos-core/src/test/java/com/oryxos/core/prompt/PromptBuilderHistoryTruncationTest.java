package com.oryxos.core.prompt;

import com.oryxos.core.Message;
import com.oryxos.core.Profile;
import com.oryxos.core.Session;
import com.oryxos.core.agent.ContextLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T034 (FR-017): history exceeding max_history_turns is truncated to the most
 * recent turns — conversation continues without interruption.
 */
class PromptBuilderHistoryTruncationTest {

    @TempDir
    Path workspace;

    private Profile profile(int maxHistoryTurns) {
        Profile profile = new Profile();
        Profile.Settings settings = new Profile.Settings();
        settings.setMaxHistoryTurns(maxHistoryTurns);
        profile.setSettings(settings);
        return profile;
    }

    private Session session(int turns) {
        Session session = new Session("s", "demo", "cli", "u");
        for (int i = 1; i <= turns; i++) {
            session.addMessage(Message.user("消息" + i));
            session.addMessage(Message.assistant("回复" + i));
        }
        return session;
    }

    @Test
    void historyWithinLimitIsKeptIntact() {
        PromptBuilder builder = new PromptBuilder(new ContextLoader(workspace));
        List<Map<String, Object>> history = builder.historyMessages(session(3), profile(20));

        assertEquals(6, history.size());
        assertEquals("user", history.get(0).get("role"));
        assertEquals("消息1", history.get(0).get("content"));
    }

    @Test
    void historyOverLimitKeepsMostRecentTurns() {
        PromptBuilder builder = new PromptBuilder(new ContextLoader(workspace));
        List<Map<String, Object>> history = builder.historyMessages(session(10), profile(3));

        long userTurns = history.stream()
                .filter(m -> "user".equals(m.get("role"))).count();
        assertEquals(3, userTurns);
        assertFalse(history.stream().anyMatch(m -> "消息1".equals(m.get("content"))), "最早的轮次必须被截断");
        assertFalse(history.stream().anyMatch(m -> "消息7".equals(m.get("content"))));
        assertTrue(history.stream().anyMatch(m -> "消息8".equals(m.get("content"))));
        assertTrue(history.stream().anyMatch(m -> "消息10".equals(m.get("content"))));

        assertEquals("回复7", history.get(0).get("content"));
        assertEquals("assistant", history.get(0).get("role"));
    }

    @Test
    void toolMessagesSurviveTruncation() {
        Session session = session(2);
        session.addMessage(Message.assistant("call"));
        session.addMessage(Message.tool("read_file", "call-1", "内容"));
        PromptBuilder builder = new PromptBuilder(new ContextLoader(workspace));

        List<Map<String, Object>> history = builder.historyMessages(session, profile(1));

        Map<String, Object> toolEntry = history.stream()
                .filter(m -> "tool".equals(m.get("role"))).findFirst().orElseThrow();
        assertEquals("read_file", toolEntry.get("name"));
        assertEquals("call-1", toolEntry.get("tool_call_id"));
    }
}
