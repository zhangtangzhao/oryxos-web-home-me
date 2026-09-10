package com.oryxos.core.tool;

import com.oryxos.core.Profile;

/**
 * 正在执行 Agent 请求的 Profile 上下文。工具实例是全局单例，但部分工具
 * （kb_search 等）的行为取决于当前 Agent 的绑定配置；DefaultAgentService
 * 在进入 ReAct 循环前绑定、finally 清理。同步执行模型（宪法 I）下
 * ThreadLocal 安全——一次 process 一个线程。
 */
public final class ToolContext {

    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

    private ToolContext() {
    }

    public static void bind(Profile profile) {
        CURRENT.set(profile);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 当前请求的 Profile；无绑定时返回 null（工具须按未绑定处理）。 */
    public static Profile current() {
        return CURRENT.get();
    }
}
