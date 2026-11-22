package com.yonatan.loganalyzer.dto;

import java.util.Map;

// Carries the analysis results. Spring converts its getters to JSON fields.
public class AnalysisResultDTO {
    private final int totalEntries;
    private final Map<String, Integer> levels;
    private final Map<String, Integer> errors;

    public AnalysisResultDTO(int totalEntries, Map<String, Integer> levels,
                             Map<String, Integer> errors) {
        this.totalEntries = totalEntries;
        this.levels = levels;
        this.errors = errors;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public Map<String, Integer> getLevels() {
        return levels;
    }

    public Map<String, Integer> getErrors() {
        return errors;
    }
}
