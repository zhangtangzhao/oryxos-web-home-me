package com.oryxos.kb;

import java.util.List;

/**
 * 分段端口（research D8）：文档文本 → 带标题路径的分段列表。
 * 实现一档为 HeadingAwareChunker（ATX 标题切分 + 段落打包）。
 */
public interface KbChunker {

    List<KbChunk> chunk(String content);
}
