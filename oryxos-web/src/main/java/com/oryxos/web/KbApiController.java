package com.oryxos.web;

import com.oryxos.kb.DefaultKbService;
import com.oryxos.kb.KbDocumentRecord;
import com.oryxos.kb.KbDocumentStatus;
import com.oryxos.kb.KbIngestService;
import com.oryxos.kb.KbOverviewEntry;
import com.oryxos.kb.KbRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Knowledge base management REST (contracts/rest-api.md). Envelope = the
 * shared ApiResponse; error mapping lives in GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/v1/kbs")
public class KbApiController {

    private final DefaultKbService kbService;

    public KbApiController(DefaultKbService kbService) {
        this.kbService = kbService;
    }

    public record CreateRequest(String name, String description) {
    }

    public record AddDocumentRequest(String path, String filename, String content) {
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@RequestBody CreateRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("缺少必填字段 name");
        }
        KbRecord record = kbService.create(request.name(), request.description());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", record.name());
        data.put("description", record.description());
        data.put("document_count", 0);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (KbRecord kb : kbService.list()) {
            data.add(summary(kb, kbService.documents(kb.name())));
        }
        return ApiResponse.ok(data);
    }

    @GetMapping("/{name}")
    public ApiResponse<Map<String, Object>> show(@PathVariable String name) {
        KbRecord kb = kbService.require(name);
        Map<String, Object> data = summary(kb, kbService.documents(name));
        data.put("embedding_dimensions", kb.embeddingDimensions());
        data.put("created_at", kb.createdAt() == null ? null : kb.createdAt().toString());
        data.put("updated_at", kb.updatedAt() == null ? null : kb.updatedAt().toString());
        List<Map<String, Object>> documents = new ArrayList<>();
        for (KbDocumentRecord d : kbService.documents(name)) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("doc_path", d.docPath());
            doc.put("status", d.status().name().toLowerCase());
            doc.put("chunk_count", d.chunkCount());
            doc.put("size_bytes", d.sizeBytes());
            doc.put("error_message", d.errorMessage());
            doc.put("ingested_at", d.ingestedAt() == null ? null : d.ingestedAt().toString());
            documents.add(doc);
        }
        data.put("documents", documents);
        return ApiResponse.ok(data);
    }

    @PostMapping("/{name}/documents")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addDocument(
            @PathVariable String name, @RequestBody AddDocumentRequest request) {
        if (request == null || (request.path() == null && request.filename() == null)) {
            throw new IllegalArgumentException("需提供 path（复制文件）或 filename+content（直传内容）");
        }
        KbDocumentRecord doc = request.path() != null
                ? kbService.addDocument(name, Path.of(request.path()))
                : kbService.addDocumentContent(name, request.filename(), request.content());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("doc_path", doc.docPath());
        data.put("status", "pending");
        data.put("content_hash", doc.contentHash());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(data));
    }

    @PostMapping("/{name}/ingest")
    public ApiResponse<Map<String, Object>> ingest(@PathVariable String name) {
        KbIngestService.IngestSummary s = kbService.ingest(name);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processed", s.processed());
        data.put("skipped", s.skipped());
        data.put("removed", s.removed());
        data.put("failed", s.failed());
        data.put("duration_ms", s.durationMs());
        List<Map<String, Object>> documents = new ArrayList<>();
        for (KbIngestService.DocumentOutcome o : s.documents()) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("doc_path", o.docPath());
            doc.put("status", o.status());
            doc.put("chunk_count", o.chunkCount());
            documents.add(doc);
        }
        data.put("documents", documents);
        return ApiResponse.ok(data);
    }

    @DeleteMapping("/{name}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable String name) {
        kbService.require(name);
        kbService.delete(name);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("removed", true);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{name}/overview")
    public ApiResponse<Map<String, Object>> overview(@PathVariable String name) {
        kbService.require(name);
        List<KbOverviewEntry> entries = kbService.overview(name);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("document_count", entries.size());
        List<Map<String, Object>> documents = new ArrayList<>();
        for (KbOverviewEntry e : entries) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("doc_path", e.docPath());
            doc.put("headings", e.headings() == null ? List.of() : e.headings());
            documents.add(doc);
        }
        data.put("documents", documents);
        return ApiResponse.ok(data);
    }

    private static Map<String, Object> summary(KbRecord kb, List<KbDocumentRecord> docs) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", kb.name());
        data.put("description", kb.description());
        data.put("document_count", docs.size());
        data.put("ready_count", docs.stream().filter(d -> d.status() == KbDocumentStatus.READY).count());
        data.put("failed_count", docs.stream().filter(d -> d.status() == KbDocumentStatus.FAILED).count());
        data.put("embedding_model", kb.embeddingModel());
        data.put("updated_at", kb.updatedAt() == null ? null : kb.updatedAt().toString());
        return data;
    }
}
