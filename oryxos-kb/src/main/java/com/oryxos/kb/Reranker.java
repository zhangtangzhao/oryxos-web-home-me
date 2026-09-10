package com.oryxos.kb;

import java.util.List;

/**
 * 重排槽位（research D4）：v1 直通实现（NoopReranker），后续可挂本地/远程
 * 重排模型而不动检索管线。
 */
public interface Reranker {

    /** 返回重排后的前 topK 个候选。 */
    <T> List<T> rerank(String query, List<T> candidates, int topK);
}
