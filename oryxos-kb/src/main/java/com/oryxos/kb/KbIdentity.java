package com.oryxos.kb;

/** 嵌入模型身份（模型 + 维度），首次摄取成功时落库（FR-015 / SC-007）。 */
public record KbIdentity(String model, int dimensions) {
}
