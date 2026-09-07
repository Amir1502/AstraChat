package com.folzi.astrachat.core;

import java.io.*;

/** Dependency-free assertions against the same production classes used on Android. */
public final class OfflineChecks {
    private static int assertions;
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        SseReader parser = new SseReader(new StringReader("\ufeff: ping\r\nevent: token\r\ndata: one\r\ndata: two\r\n\r\ndata: [DONE]\n\n"), 4096);
        var first = parser.next();
        check(first.event().equals("token"), "event name");
        check(first.data().equals("one\ntwo"), "multiline and CRLF");
        check(parser.next().data().equals("[DONE]"), "done event");
        check(parser.next() == null, "EOF");
        parser = new SseReader(new StringReader("data: x\r\rdata: y\r\r"), 4096);
        check(parser.next().data().equals("x") && parser.next().data().equals("y"), "CR-only line endings");
        parser = new SseReader(new StringReader("data: final"), 4096);
        check(parser.next().data().equals("final"), "unterminated EOF frame retained");
        parser = new SseReader(new StringReader("id: 2\nretry: 900\ndata\n\n"), 4096);
        check(parser.next().data().isEmpty(), "empty data field");
        parser = new SseReader(new StringReader(": heartbeat\n\n"), 4096);
        check(parser.next() == null, "comments produce no tokens");
        try { new SseReader(new StringReader("data: oversized\n\n"), 4).next(); throw new AssertionError("limit ignored"); }
        catch (IOException expected) { assertions++; }
        check(TokenMath.estimate("") == 0, "empty estimate");
        check(TokenMath.estimate("12345") == 2, "rounding up");
        check(TokenMath.estimate("😀😀😀😀") == 1, "Unicode code points, not UTF-16 units");
        check(TokenMath.speed(20, 1_000_000_000L, 3_000_000_000L) == 10, "TPS uses interval after first token");
        check(TokenMath.speed(20, null, 3_000_000_000L) == 0, "no first token");
        check(TokenMath.speed(20, 1L, 1L) == 0, "zero interval");
        check(TokenMath.ttft(1_000_000L, 2_500_000L) == 1.5, "TTFT milliseconds");
        check(TokenMath.ttft(1L, null) == null, "TTFT unavailable before text");
        System.out.println("PASS: " + assertions + " production SSE/metrics assertions");
    }
}
