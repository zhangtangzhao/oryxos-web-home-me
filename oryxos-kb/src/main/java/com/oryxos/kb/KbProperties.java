package com.oryxos.kb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Knowledge base configuration (oryxos.kb.*, research D9). Defaults are
 * in-class so an unconfigured embedding section never breaks startup — the
 * "which key is missing" error is raised at use time (ingest/search).
 */
@ConfigurationProperties(prefix = "oryxos.kb")
public class KbProperties {

    private final Embedding embedding = new Embedding();
    private final Search search = new Search();
    private final Chunk chunk = new Chunk();

    public Embedding getEmbedding() { return embedding; }
    public Search getSearch() { return search; }
    public Chunk getChunk() { return chunk; }

    /** OpenAI-compatible /embeddings endpoint. Empty baseUrl = not configured. */
    public static class Embedding {
        private String baseUrl = "";
        private String apiKeyEnv = "";
        private String model = "";
        private int dimensions = 1024;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl.trim(); }
        public String getApiKeyEnv() { return apiKeyEnv; }
        public void setApiKeyEnv(String apiKeyEnv) { this.apiKeyEnv = apiKeyEnv == null ? "" : apiKeyEnv.trim(); }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model == null ? "" : model.trim(); }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    }

    public static class Search {
        private int topK = 5;
        private int maxTopK = 20;
        private int candidatePool = 50;
        private int rrfK = 60;
        private double semanticWeight = 0.7;
        private double keywordWeight = 0.3;
        private double semanticFloor = 0.35;

        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public int getMaxTopK() { return maxTopK; }
        public void setMaxTopK(int maxTopK) { this.maxTopK = maxTopK; }
        public int getCandidatePool() { return candidatePool; }
        public void setCandidatePool(int candidatePool) { this.candidatePool = candidatePool; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int rrfK) { this.rrfK = rrfK; }
        public double getSemanticWeight() { return semanticWeight; }
        public void setSemanticWeight(double semanticWeight) { this.semanticWeight = semanticWeight; }
        public double getKeywordWeight() { return keywordWeight; }
        public void setKeywordWeight(double keywordWeight) { this.keywordWeight = keywordWeight; }
        /** 语义路相似度下限：低于此余弦的候选不参与合并（零结果的判定依据之一）。 */
        public double getSemanticFloor() { return semanticFloor; }
        public void setSemanticFloor(double semanticFloor) { this.semanticFloor = semanticFloor; }
    }

    public static class Chunk {
        private int targetChars = 500;
        private int overlapChars = 50;

        public int getTargetChars() { return targetChars; }
        public void setTargetChars(int targetChars) { this.targetChars = targetChars; }
        public int getOverlapChars() { return overlapChars; }
        public void setOverlapChars(int overlapChars) { this.overlapChars = overlapChars; }
    }
}
