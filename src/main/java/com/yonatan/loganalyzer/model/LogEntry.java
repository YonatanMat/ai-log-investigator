package com.yonatan.loganalyzer.model;

// One parsed line from the uploaded log.
public class LogEntry {
    private final String time;
    private final String level;
    private final String message;

    public LogEntry(String time, String level, String message) {
        this.time = time;
        this.level = level;
        this.message = message;
    }

    public String getTime() {
        return time;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
