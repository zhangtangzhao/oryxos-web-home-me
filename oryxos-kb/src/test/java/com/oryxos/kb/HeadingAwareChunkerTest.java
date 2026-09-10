package com.oryxos.kb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadingAwareChunkerTest {

    private final KbChunker chunker = new HeadingAwareChunker(500, 50);

    @Test
    void headingPathExtraction() {
        String doc = """
                # 部署指南

                部署总述段落。

                ## Windows

                Windows 下的部署步骤。

                ## Linux

                Linux 下的部署步骤。
                """;
        List<KbChunk> chunks = chunker.chunk(doc);
        assertTrue(chunks.size() >= 3);
        assertEquals("部署指南", chunks.get(0).headingPath());
        assertTrue(chunks.stream().anyMatch(c -> "部署指南 > Windows".equals(c.headingPath())
                && c.content().contains("Windows 下的部署步骤")));
        assertTrue(chunks.stream().anyMatch(c -> "部署指南 > Linux".equals(c.headingPath())));
    }

    @Test
    void deepHeadingNesting() {
        String doc = """
                # A

                a 正文。

                ## B

                b 正文。

                ### C

                c 正文。
                """;
        List<KbChunk> chunks = chunker.chunk(doc);
        assertTrue(chunks.stream().anyMatch(c -> "A > B > C".equals(c.headingPath())));
    }

    @Test
    void packingRespectsTargetWithOverlap() {
        String paragraph = "这是一个用于打包测试的段落，包含若干汉字字符以逼近目标长度。" + "x".repeat(60) + "\n\n";
        StringBuilder doc = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            doc.append("段落").append(i).append("：").append(paragraph.repeat(1)).append("\n");
        }
        List<KbChunk> chunks = chunker.chunk(doc.toString());
        assertTrue(chunks.size() >= 2, "12 个约 90 字符的段落按 500 目标应产生多个分段");
        for (KbChunk c : chunks) {
            assertTrue(c.content().length() <= 500 + 90,
                    "分段不应显著超过目标长度: " + c.content().length());
            assertNull(c.headingPath());
        }
        // 相邻分段有重叠（后一段开头出现在前一段结尾附近）
        String first = chunks.get(0).content();
        String second = chunks.get(1).content();
        assertTrue(first.endsWith(second.substring(0, Math.min(20, second.length())).trim())
                        || first.contains(second.substring(0, Math.min(30, second.length())).trim()),
                "相邻分段应携带重叠内容");
    }

    @Test
    void oversizedSingleParagraphHardSplit() {
        String big = "长".repeat(1200);
        List<KbChunk> chunks = chunker.chunk(big);
        assertTrue(chunks.size() >= 2);
        for (KbChunk c : chunks) {
            assertTrue(c.content().length() <= 500 + 50);
        }
    }

    @Test
    void emptyDocumentYieldsNoChunks() {
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   \n\n  ").isEmpty());
    }

    @Test
    void plainTextPacksIntoChunks() {
        List<KbChunk> chunks = chunker.chunk("无标题纯文本第一段。\n\n无标题纯文本第二段。");
        assertEquals(1, chunks.size());
        assertNull(chunks.get(0).headingPath());
        assertTrue(chunks.get(0).content().contains("第一段"));
        assertTrue(chunks.get(0).content().contains("第二段"));
    }
}
