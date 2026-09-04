package com.oryxos.web;

import com.oryxos.memory.MemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Long-term memory query (contract §7): no query = injection view (truncated
 * full text); ?query=keyword = case-insensitive substring match (FR-018).
 */
@RestController
@RequestMapping("/api/v1/memory")
public class MemoryApiController {

    private final MemoryService memoryService;

    public MemoryApiController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> memory(@RequestParam(required = false) String query) {
        List<Map<String, Object>> items = new ArrayList<>();
        boolean truncated = false;
        int totalChars;

        if (query != null && !query.isBlank()) {
            String hits = memoryService.recallMemory(query);
            for (String line : hits.split("\r?\n")) {
                if (!line.isBlank()) {
                    items.add(Map.of("content", line, "matched", true));
                }
            }
            totalChars = hits.length();
        } else {
            String view = memoryService.getMemoryContext(null);
            truncated = view.contains("已截断");
            for (String line : view.split("\r?\n")) {
                if (!line.isBlank()) {
                    items.add(Map.of("content", line, "matched", false));
                }
            }
            totalChars = view.length();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_chars", totalChars);
        data.put("truncated", truncated);
        data.put("items", items);
        return ApiResponse.ok(data);
    }
}
