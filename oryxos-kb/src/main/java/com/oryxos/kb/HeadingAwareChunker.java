package com.oryxos.kb;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ATX 标题感知分段（research D8）：
 * 1) 按 #~###### 切节，标题栈拼出 "部署指南 > Windows" 式标题路径；
 * 2) 节内按空行分段、段落打包到 target-chars，相邻分段携带 overlap-chars 重叠；
 * 3) 无标题文档整篇视为单节（headingPath=null）；超长单段硬切。
 */
public class HeadingAwareChunker implements KbChunker {

    private static final Pattern ATX = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    private final int targetChars;
    private final int overlapChars;

    public HeadingAwareChunker(int targetChars, int overlapChars) {
        this.targetChars = Math.max(50, targetChars);
        this.overlapChars = Math.max(0, Math.min(overlapChars, this.targetChars / 2));
    }

    public HeadingAwareChunker(KbProperties.Chunk cfg) {
        this(cfg.getTargetChars(), cfg.getOverlapChars());
    }

    private record Section(List<String> headingStack, StringBuilder body) {
    }

    @Override
    public List<KbChunk> chunk(String content) {
        List<KbChunk> out = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return out;
        }
        List<String> stack = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (String line : content.split("\r?\n", -1)) {
            Matcher m = ATX.matcher(line.trim());
            if (m.matches()) {
                if (!body.isEmpty()) {
                    pack(out, stack.isEmpty() ? null : String.join(" > ", stack), body.toString());
                }
                int level = m.group(1).length();
                String title = m.group(2).trim();
                while (stack.size() >= level) {
                    stack.remove(stack.size() - 1);
                }
                stack.add(title);
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        if (!body.isEmpty()) {
            pack(out, stack.isEmpty() ? null : String.join(" > ", stack), body.toString());
        }
        return out;
    }

    private void pack(List<KbChunk> out, String headingPath, String text) {
        String[] paragraphs = text.trim().split("\\n\\s*\\n+");
        StringBuilder buffer = new StringBuilder();
        for (String p : paragraphs) {
            String para = p.trim();
            if (para.isEmpty()) {
                continue;
            }
            if (para.length() > targetChars) {
                if (!buffer.isEmpty()) {
                    emit(out, headingPath, buffer);
                    buffer.setLength(0);
                }
                hardSplit(out, headingPath, para);
                continue;
            }
            if (!buffer.isEmpty() && buffer.length() + para.length() + 2 > targetChars) {
                emit(out, headingPath, buffer);
                buffer.setLength(0);
                buffer.append(tailOf(out.get(out.size() - 1).content()));
            }
            if (!buffer.isEmpty()) {
                buffer.append("\n\n");
            }
            buffer.append(para);
        }
        if (!buffer.isEmpty()) {
            emit(out, headingPath, buffer);
        }
    }

    private void hardSplit(List<KbChunk> out, String headingPath, String para) {
        int start = 0;
        while (start < para.length()) {
            int end = Math.min(start + targetChars, para.length());
            out.add(new KbChunk(headingPath, para.substring(start, end)));
            if (end >= para.length()) {
                break;
            }
            start = Math.max(end - overlapChars, start + 1);
        }
    }

    private void emit(List<KbChunk> out, String headingPath, StringBuilder buffer) {
        out.add(new KbChunk(headingPath, buffer.toString().trim()));
    }

    /** 上一分段的末尾 overlapChars 字符，作为下一分段的起始重叠。 */
    private String tailOf(String previous) {
        if (overlapChars == 0 || previous == null || previous.length() <= overlapChars) {
            return "";
        }
        String tail = previous.substring(previous.length() - overlapChars);
        int cut = tail.indexOf('\n');
        return cut >= 0 ? tail.substring(cut + 1) : tail;
    }
}
