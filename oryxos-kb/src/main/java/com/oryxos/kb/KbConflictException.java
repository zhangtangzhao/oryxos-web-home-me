package com.oryxos.kb;

/**
 * 知识库冲突 → REST 409。code 取 KB_CONFLICT（重名）或
 * EMBEDDING_MISMATCH（嵌入模型身份与库记录不符，FR-015）。
 */
public class KbConflictException extends RuntimeException {

    public static final String KB_CONFLICT = "KB_CONFLICT";
    public static final String EMBEDDING_MISMATCH = "EMBEDDING_MISMATCH";

    private final String code;

    public KbConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
