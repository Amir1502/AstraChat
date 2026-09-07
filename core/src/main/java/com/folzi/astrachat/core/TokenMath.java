package com.folzi.astrachat.core;

/** Provider usage always takes precedence; this fallback is not a tokenizer. */
public final class TokenMath {
    private TokenMath() {}
    public static long estimate(String text) { return (text.codePointCount(0, text.length()) + 3L) / 4L; }
    public static double speed(long outputTokens, Long firstTokenNanos, long endNanos) {
        if (firstTokenNanos == null || endNanos <= firstTokenNanos || outputTokens <= 0) return 0;
        return outputTokens / ((endNanos - firstTokenNanos) / 1_000_000_000.0);
    }
    public static Double ttft(long start, Long first) {
        return first == null ? null : Math.max(0, first - start) / 1_000_000.0;
    }
}
