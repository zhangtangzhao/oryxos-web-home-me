package com.oryxos.kb;

/** 知识库不存在 → REST 404 KB_NOT_FOUND / CLI 非零退出。 */
public class KbNotFoundException extends RuntimeException {

    private final String kb;

    public KbNotFoundException(String kb) {
        super("知识库不存在: " + kb);
        this.kb = kb;
    }

    public String getKb() {
        return kb;
    }
}
