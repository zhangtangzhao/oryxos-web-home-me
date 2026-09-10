package com.oryxos.kb;

import java.util.List;

/** 总览条目：文档路径 + 去重保序的标题路径大纲（US5）。 */
public record KbOverviewEntry(String docPath, List<String> headings) {
}
