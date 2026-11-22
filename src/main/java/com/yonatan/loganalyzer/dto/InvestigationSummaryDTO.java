package com.yonatan.loganalyzer.dto;

import java.time.Instant;
import java.util.UUID;

public record InvestigationSummaryDTO(UUID id, Instant createdAt, int totalEntries, String topMessage) {}
