package com.oryxos.kb;

import java.util.List;

/**
 * 嵌入服务端口（research D2）。实现必须同步（宪法 I）且凭证只经环境变量
 * 解析（宪法 VI）。返回向量顺序与入参一致。
 */
public interface EmbeddingClient {

    /** 批量嵌入；未配置抛 {@link EmbeddingNotConfiguredException}，调用失败抛
     *  {@link EmbeddingUnavailableException}。 */
    List<float[]> embed(List<String> inputs);

    /** 配置声明的模型名（身份记录用，FR-015）。 */
    String model();

    /** 配置声明的向量维度（身份记录用）。 */
    int dimensions();
}
