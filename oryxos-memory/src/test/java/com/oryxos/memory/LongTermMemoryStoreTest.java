package com.oryxos.memory;

import com.oryxos.core.agent.ContextLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T038: MEMORY.md append / case-insensitive substring recall (clarification
 * #5) / FR-018 4000-char injection view truncation.
 */
class LongTermMemoryStoreTest {

    @TempDir
    Path workspace;

    @Test
    void appendCreatesFileAndEntry() {
        MarkdownMemoryStore store = new MarkdownMemoryStore(workspace);
        store.append("用户偏好美式咖啡", MemoryScope.CORE);

        String content = store.load();
        assertTrue(content.contains("用户偏好美式咖啡"));
        assertTrue(Files.exists(workspace.resolve("memory").resolve("MEMORY.md")));
    }

    @Test
    void appendAccumulatesEntriesAndIsIdempotentAboutDirs() {
        MarkdownMemoryStore store = new MarkdownMemoryStore(workspace);
        store.append("第一条记忆", MemoryScope.CORE);
        store.append("第二条记忆", MemoryScope.ARCHIVAL);

        String content = store.load();
        assertTrue(content.contains("第一条记忆"));
        assertTrue(content.contains("第二条记忆"));
        assertEquals(2, content.split("\r?\n").length);
    }

    @Test
    void recallIsCaseInsensitiveSubstringMatchReturningLines() {
        MarkdownMemoryStore store = new MarkdownMemoryStore(workspace);
        store.append("用户使用 CoffeeScript 写脚本", MemoryScope.CORE);
        store.append("部署在杭州机房", MemoryScope.CORE);

        assertTrue(store.recallByKeyword("coffee").contains("CoffeeScript"));
        assertTrue(store.recallByKeyword("COFFEE").contains("CoffeeScript"));
        assertTrue(store.recallByKeyword("杭州").contains("部署在杭州机房"));
        assertTrue(store.recallByKeyword("不存在的关键词").isEmpty());
        assertTrue(store.recallByKeyword("  ").isEmpty());
    }

    @Test
    void appendBlankContentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarkdownMemoryStore(workspace).append("   ", MemoryScope.CORE));
    }

    @Test
    void memoryServiceInjectionViewTruncatedAt4000() {
        MarkdownMemoryStore store = new MarkdownMemoryStore(workspace);
        for (int i = 0; i < 200; i++) {
            store.append("记忆" + i + "：" + "x".repeat(100), MemoryScope.CORE);
        }
        DefaultMemoryService service = new DefaultMemoryService(store);

        String view = service.getMemoryContext(null);
        assertTrue(view.contains("已截断"), "超限视图必须带截断标记（FR-018）");
        assertTrue(view.length() <= ContextLoader.MEMORY_VIEW_LIMIT + "\n…（已截断）".length());
        assertFalse(view.contains("记忆199"), "截断点之后的内容不得泄漏进视图");
    }

    @Test
    void memoryServiceEmptyStoreYieldsEmptyContext() {
        DefaultMemoryService service = new DefaultMemoryService(new MarkdownMemoryStore(workspace));
        assertTrue(service.getMemoryContext(null).isEmpty());
        assertTrue(service.recallMemory("任意").isEmpty());
    }
}
