package com.oryxos.kb;

/** 分段结果：标题路径（无标题文档为 null）+ 分段正文。 */
public record KbChunk(String headingPath, String content) {
}
