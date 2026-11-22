package com.yonatan.loganalyzer.service;

import com.yonatan.loganalyzer.dto.AnalysisResultDTO;
import com.yonatan.loganalyzer.model.LogEntry;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LogAnalysisService {

    public AnalysisResultDTO analyze(InputStream input) throws IOException {
        List<LogEntry> entries = readEntries(input);
        Map<String, Integer> levels = countLevels(entries);
        Map<String, Integer> errors = countErrors(entries);

        // The results are returned as an object, rather than printed.
        return new AnalysisResultDTO(entries.size(), levels, errors);
    }

    private List<LogEntry> readEntries(InputStream input) throws IOException {
        List<LogEntry> entries = new ArrayList<>();

        // The UTF-8 decoder reports invalid text. This try block closes the stream.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Keep the entire message together, including its spaces.
                String[] parts = line.split("\\s+", 3);
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Line " + lineNumber
                            + ": expected TIME LEVEL MESSAGE.");
                }

                entries.add(new LogEntry(parts[0], parts[1], parts[2]));
            }
        }

        return entries;
    }

    private Map<String, Integer> countLevels(List<LogEntry> entries) {
        Map<String, Integer> levels = new LinkedHashMap<>();
        levels.put("INFO", 0);
        levels.put("WARN", 0);
        levels.put("ERROR", 0);

        for (LogEntry entry : entries) {
            String level = entry.getLevel();
            if (!level.equals("INFO") && !level.equals("WARN") && !level.equals("ERROR")) {
                level = "OTHER";
            }
            increaseCount(levels, level);
        }

        return levels;
    }

    private Map<String, Integer> countErrors(List<LogEntry> entries) {
        Map<String, Integer> errors = new LinkedHashMap<>();
        for (LogEntry entry : entries) {
            if (entry.getLevel().equals("ERROR")) {
                increaseCount(errors, entry.getMessage());
            }
        }
        return errors;
    }

    private void increaseCount(Map<String, Integer> counts, String key) {
        if (counts.containsKey(key)) {
            counts.put(key, counts.get(key) + 1);
        } else {
            counts.put(key, 1);
        }
    }
}
