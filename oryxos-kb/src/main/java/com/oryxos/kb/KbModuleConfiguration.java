package com.oryxos.kb;

import com.oryxos.storage.KbChunkRepository;
import com.oryxos.storage.KbDocumentRepository;
import com.oryxos.storage.KbFtsIndex;
import com.oryxos.storage.KbRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Paths;

/**
 * KB module wiring (模式同 MemoryModuleConfiguration): store/chunker/
 * embedding/reranker/ingest/facade beans. Search-side beans (search service,
 * tool registrar) join here in US2.
 */
@Configuration
@EnableConfigurationProperties(KbProperties.class)
public class KbModuleConfiguration {

    @Bean
    public KbStore kbStore(KbRepository kbRepository, KbDocumentRepository documentRepository,
                           KbChunkRepository chunkRepository, JdbcTemplate jdbcTemplate) {
        return new LocalKbStore(kbRepository, documentRepository, chunkRepository,
                new KbFtsIndex(jdbcTemplate));
    }

    @Bean
    public KbChunker kbChunker(KbProperties properties) {
        return new HeadingAwareChunker(properties.getChunk());
    }

    @Bean
    public EmbeddingClient kbEmbeddingClient(KbProperties properties) {
        return new OpenAiCompatEmbeddingClient(properties);
    }

    @Bean
    public Reranker kbReranker() {
        return new NoopReranker();
    }

    @Bean
    public KbIngestService kbIngestService(KbStore kbStore, KbChunker kbChunker,
                                           EmbeddingClient kbEmbeddingClient,
                                           @Value("${oryxos.root:.oryxos}") String workspaceRoot) {
        return new KbIngestService(kbStore, kbChunker, kbEmbeddingClient, Paths.get(workspaceRoot));
    }

    @Bean
    public KbSearchService kbSearchService(KbStore kbStore, EmbeddingClient kbEmbeddingClient,
                                           Reranker kbReranker, KbProperties properties) {
        return new KbSearchService(kbStore, kbEmbeddingClient, kbReranker, properties);
    }

    @Bean
    public DefaultKbService kbService(KbStore kbStore, KbIngestService kbIngestService,
                                      @Value("${oryxos.root:.oryxos}") String workspaceRoot) {
        return new DefaultKbService(kbStore, kbIngestService, Paths.get(workspaceRoot));
    }

    @Bean
    public KbEvalService kbEvalService(KbStore kbStore, KbSearchService kbSearchService,
                                       @Value("${oryxos.root:.oryxos}") String workspaceRoot) {
        return new KbEvalService(kbStore, kbSearchService, Paths.get(workspaceRoot));
    }
}
