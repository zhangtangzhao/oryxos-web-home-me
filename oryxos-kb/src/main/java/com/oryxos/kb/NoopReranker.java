package com.oryxos.kb;

import java.util.List;

/** 直通重排：保持 RRF 融合序，截取前 topK（research D4 一档实现）。 */
public class NoopReranker implements Reranker {

    @Override
    public <T> List<T> rerank(String query, List<T> candidates, int topK) {
        return candidates.size() <= topK ? candidates : candidates.subList(0, topK);
    }
}
