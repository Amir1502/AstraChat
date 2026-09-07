package com.folzi.astrachat.core;

import java.io.IOException;
import java.io.Reader;

/** Incremental SSE framing, including CRLF, CR, BOM, comments and multiline data. */
public final class SseReader {
    public static final class Event {
        private final String event;
        private final String data;
        public Event(String event, String data) { this.event = event; this.data = data; }
        public String event() { return event; }
        public String data() { return data; }
    }
    private final Reader reader;
    private final int maxChars;
    private boolean beginning = true;
    private int pending = -1;
    public SseReader(Reader reader, int maxChars) { this.reader = reader; this.maxChars = maxChars; }
    private String line() throws IOException {
        StringBuilder result = new StringBuilder();
        while (true) {
            int c = pending >= 0 ? pending : reader.read(); pending = -1;
            if (beginning) { beginning = false; if (c == 0xfeff) continue; }
            if (c < 0) return result.length() == 0 ? null : result.toString();
            if (c == '\n') return result.toString();
            if (c == '\r') {
                int next = reader.read(); if (next != '\n') pending = next;
                return result.toString();
            }
            result.append((char) c);
            if (result.length() > maxChars) throw new IOException("SSE line exceeds safety limit");
        }
    }
    public Event next() throws IOException {
        StringBuilder data = new StringBuilder();
        String type = "message";
        boolean hasData = false;
        while (true) {
            String line = line();
            if (line == null || line.isEmpty()) {
                if (hasData) return new Event(type, data.substring(0, data.length() - 1));
                if (line == null) return null;
                type = "message"; continue;
            }
            if (line.charAt(0) == ':') continue;
            int split = line.indexOf(':');
            String name = split < 0 ? line : line.substring(0, split);
            String value = split < 0 ? "" : line.substring(split + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            if (name.equals("event")) type = value;
            if (name.equals("data")) { data.append(value).append('\n'); hasData = true; }
            if (data.length() > maxChars) throw new IOException("SSE event exceeds safety limit");
        }
    }
}
