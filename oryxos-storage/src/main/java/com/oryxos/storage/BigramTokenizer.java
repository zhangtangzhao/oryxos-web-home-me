package com.oryxos.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * 依赖零成本的中文友好切分（research D3）：CJK 连续串按 2 字滑窗成 bigram，
 * ASCII 连续字母数字按整词输出并转小写，其余字符视为分隔。
 * 输出以空格连接，供 FTS5 unicode61 tokenizer 与 LIKE 降级路共用。
 * 检索质量升级点 = 换本类的实现（如 jieba），调用方无感。
 */
public final class BigramTokenizer {

    private BigramTokenizer() {
    }

    public static String tokenize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        List<Integer> cjkRun = new ArrayList<>();

        int i = 0;
        int length = text.length();
        while (i < length) {
            int cp = text.codePointAt(i);
            if (isAsciiWordChar(cp)) {
                flushCjk(out, cjkRun);
                int start = i;
                while (i < length && isAsciiWordChar(text.codePointAt(i))) {
                    i += Character.charCount(text.codePointAt(i));
                }
                out.append(' ').append(text.substring(start, i).toLowerCase());
            } else if (isCjk(cp)) {
                cjkRun.add(cp);
                i += Character.charCount(cp);
            } else {
                flushCjk(out, cjkRun);
                i += Character.charCount(cp);
            }
        }
        flushCjk(out, cjkRun);
        return out.toString().trim();
    }

    private static void flushCjk(StringBuilder out, List<Integer> run) {
        if (run.isEmpty()) {
            return;
        }
        if (run.size() == 1) {
            out.append(' ').appendCodePoint(run.get(0));
        } else {
            for (int j = 0; j < run.size() - 1; j++) {
                out.append(' ').appendCodePoint(run.get(j)).appendCodePoint(run.get(j + 1));
            }
        }
        run.clear();
    }

    private static boolean isAsciiWordChar(int cp) {
        return (cp >= 'a' && cp <= 'z') || (cp >= 'A' && cp <= 'Z') || (cp >= '0' && cp <= '9');
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)      // CJK Unified Ideographs
                || (cp >= 0x3400 && cp <= 0x4DBF)  // Extension A
                || (cp >= 0xF900 && cp <= 0xFAFF); // Compatibility Ideographs
    }
}
