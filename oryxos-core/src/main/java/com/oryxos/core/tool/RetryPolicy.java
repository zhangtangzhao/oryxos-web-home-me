package com.oryxos.core.tool;

/**
 * Retry policy for tool execution (FR-014): retryable failures are retried
 * with exponential backoff, at most {@value #MAX_ATTEMPTS} attempts total.
 * Every attempt is audited individually by ToolExecutor; the final failure is
 * returned to the model so reasoning can continue.
 */
public final class RetryPolicy {

    public static final int MAX_ATTEMPTS = 3;
    public static final long BASE_DELAY_MS = 500;

    private RetryPolicy() {
    }

    /** Backoff before the given attempt (1-based): 500ms, 1000ms, … */
    public static long backoffDelayMs(int attemptJustFinished) {
        return BASE_DELAY_MS << (attemptJustFinished - 1);
    }

    public static boolean shouldRetry(int attemptJustFinished, boolean retryable) {
        return retryable && attemptJustFinished < MAX_ATTEMPTS;
    }
}
