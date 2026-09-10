package com.oryxos.storage;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BigramTokenizerTest {

    private static List<String> tokens(String text) {
        String t = BigramTokenizer.tokenize(text);
        return t.isBlank() ? List.of() : Arrays.asList(t.split("\\s+"));
    }

    @Test
    void cjkRunBecomesBigrams() {
        assertEquals(List.of("知识", "识库"), tokens("知识库"));
        assertEquals(List.of("默认", "认端", "端口"), tokens("默认端口"));
    }

    @Test
    void singleCjkCharStaysSingle() {
        assertEquals(List.of("问"), tokens("问"));
    }

    @Test
    void asciiWordsLowercased() {
        assertEquals(List.of("hello", "world"), tokens("Hello World"));
    }

    @Test
    void mixedChineseAndAscii() {
        // "OryxOS 默认端口8080" → oryxos | 默认 认端 端口 | 8080
        assertEquals(List.of("oryxos", "默认", "认端", "端口", "8080"),
                tokens("OryxOS 默认端口8080"));
    }

    @Test
    void punctuationSeparates() {
        assertEquals(List.of("部署", "署指", "指南", "windows"), tokens("部署指南（Windows）"));
    }

    @Test
    void nullAndBlankAreEmpty() {
        assertTrue(BigramTokenizer.tokenize(null).isEmpty());
        assertTrue(BigramTokenizer.tokenize("   ").isEmpty());
        assertTrue(tokens("### --- ===").isEmpty());
    }
}
