package com.oryxos.kb;

import com.oryxos.core.OryxTool;
import com.oryxos.core.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * Registers kb_search into the shared ToolRegistry at startup (same shape as
 * MemoryToolRegistrar). Registration does NOT make the tool visible to every
 * agent — an Agent must list kb_search in its frontmatter `tools` to see it
 * (Profile 最小权限，research D5).
 */
@Component
public class KbToolRegistrar {

    public KbToolRegistrar(ToolRegistry registry, KbStore kbStore, KbSearchService searchService) {
        registry.register(KbTools.kbSearchTool(searchService));
        registry.register(KbTools.kbOverviewTool(kbStore, searchService));
    }
}
