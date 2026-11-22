package com.yonatan.loganalyzer.dto;

import java.time.Instant;
import java.util.UUID;

public record InvestigationDTO(UUID id, Instant createdAt, AnalysisResultDTO analysis, TopError topError) {
    public record TopError(String message, int count, String explanation) {}
}
