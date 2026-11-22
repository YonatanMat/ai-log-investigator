package com.yonatan.loganalyzer;

import com.yonatan.loganalyzer.dto.AnalysisResultDTO;
import com.yonatan.loganalyzer.service.LogAnalysisService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LogAnalysisServiceTest {
    private final LogAnalysisService service = new LogAnalysisService();

    @Test
    void originalSampleKeepsTheStepTwoResults() throws Exception {
        AnalysisResultDTO result = service.analyze(Files.newInputStream(Path.of("samples/sample.log")));
        assertEquals(10, result.getTotalEntries());
        assertEquals(Map.of("INFO", 6, "WARN", 1, "ERROR", 3), result.getLevels());
        assertEquals(Map.of("Database connection failed", 1,
                "Invalid authentication token", 1,
                "Audio file not found: sample song.mp3", 1), result.getErrors());
    }

    @Test
    void repeatedSampleGroupsErrorsAcrossDifferentTimes() throws Exception {
        AnalysisResultDTO result = service.analyze(Files.newInputStream(Path.of("samples/sample-repeated.log")));
        assertEquals(11, result.getTotalEntries());
        assertEquals(4, result.getLevels().get("ERROR"));
        assertEquals(2, result.getErrors().get("Database connection failed"));
        assertEquals(3, result.getErrors().size());
    }

    @Test
    void onlyErrorsAreGroupedAndMessagesRemainCaseSensitive() throws Exception {
        AnalysisResultDTO result = analyze("00:00:00 INFO Same failure\n"
                + "00:00:01 WARN Same failure\n00:00:02 ERROR Same failure\n"
                + "00:00:03 ERROR Same failure\n00:00:04 ERROR same failure\n"
                + "00:00:05 DEBUG Diagnostic details\n");
        assertEquals(Map.of("INFO", 1, "WARN", 1, "ERROR", 3, "OTHER", 1), result.getLevels());
        assertEquals(Map.of("Same failure", 2, "same failure", 1), result.getErrors());
    }

    @Test
    void blankInputProducesAnEmptyReport() throws Exception {
        AnalysisResultDTO result = analyze(" \n\t\r\n");
        assertEquals(0, result.getTotalEntries());
        assertEquals(Map.of("INFO", 0, "WARN", 0, "ERROR", 0), result.getLevels());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void tabsWindowsNewlinesAndMessageSpacesArePreserved() throws Exception {
        AnalysisResultDTO result = analyze("\r\n  21:00:00\tERROR\tAudio file not found: sample  song.mp3  \r\n");
        assertEquals(1, result.getTotalEntries());
        assertEquals(Map.of("Audio file not found: sample  song.mp3", 1), result.getErrors());
    }

    @Test
    void incompleteLineNamesItsOriginalLineNumber() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> analyze("\n21:00:00 ERROR\n"));
        assertEquals("Line 2: expected TIME LEVEL MESSAGE.", error.getMessage());
    }

    @Test
    void invalidUtf8IsRejected() {
        byte[] invalid = {(byte) 0xC3, (byte) 0x28};
        assertThrows(CharacterCodingException.class,
                () -> service.analyze(new ByteArrayInputStream(invalid)));
    }

    private AnalysisResultDTO analyze(String text) throws Exception {
        return service.analyze(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
}
