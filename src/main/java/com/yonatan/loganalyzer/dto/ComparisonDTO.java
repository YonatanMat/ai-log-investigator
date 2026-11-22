package com.yonatan.loganalyzer.dto;

import java.util.List;
import java.util.UUID;

public record ComparisonDTO(Report before, Report after, List<ErrorChange> changes) {
    public record Report(UUID id, int totalEntries) {}
    public record ErrorChange(String message, int beforeCount, int afterCount, int delta) {}
}
