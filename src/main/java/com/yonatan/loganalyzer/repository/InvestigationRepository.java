package com.yonatan.loganalyzer.repository;

import com.yonatan.loganalyzer.dto.AnalysisResultDTO;
import com.yonatan.loganalyzer.dto.InvestigationDTO;
import com.yonatan.loganalyzer.dto.InvestigationSummaryDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class InvestigationRepository {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public InvestigationRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public InvestigationDTO save(AnalysisResultDTO analysis, InvestigationDTO.TopError topError) {
        UUID id = UUID.randomUUID();
        long created = System.currentTimeMillis();
        // One insert commits the complete report, including reports with no errors.
        jdbc.update("""
                INSERT INTO investigations
                    (id, created_ms, total_entries, analysis_json, top_message, top_count, explanation)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id.toString(), created, analysis.getTotalEntries(), json.writeValueAsString(analysis),
                topError == null ? null : topError.message(),
                topError == null ? null : topError.count(),
                topError == null ? null : topError.explanation());
        return new InvestigationDTO(id, Instant.ofEpochMilli(created), analysis, topError);
    }

    public List<InvestigationSummaryDTO> recent() {
        return jdbc.query("""
                SELECT id, created_ms, total_entries, top_message FROM investigations
                ORDER BY created_ms DESC, id DESC LIMIT 50
                """, (row, index) -> new InvestigationSummaryDTO(UUID.fromString(row.getString("id")),
                Instant.ofEpochMilli(row.getLong("created_ms")), row.getInt("total_entries"),
                row.getString("top_message")));
    }

    public Optional<InvestigationDTO> find(UUID id) {
        return jdbc.query("""
                SELECT created_ms, analysis_json, top_message, top_count, explanation
                FROM investigations WHERE id = ?
                """, (row, index) -> {
            String message = row.getString("top_message");
            var topError = message == null ? null : new InvestigationDTO.TopError(
                    message, row.getInt("top_count"), row.getString("explanation"));
            return new InvestigationDTO(id, Instant.ofEpochMilli(row.getLong("created_ms")),
                    json.readValue(row.getString("analysis_json"), AnalysisResultDTO.class), topError);
        }, id.toString()).stream().findFirst();
    }
}
