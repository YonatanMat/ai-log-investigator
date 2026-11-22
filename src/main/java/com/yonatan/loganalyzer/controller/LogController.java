package com.yonatan.loganalyzer.controller;

import com.yonatan.loganalyzer.dto.AnalysisResultDTO;
import com.yonatan.loganalyzer.dto.InvestigationDTO;
import com.yonatan.loganalyzer.dto.InvestigationSummaryDTO;
import com.yonatan.loganalyzer.repository.InvestigationRepository;
import com.yonatan.loganalyzer.service.ErrorExplainerService;
import com.yonatan.loganalyzer.service.LogAnalysisService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class LogController {
    private final LogAnalysisService analysisService;
    private final ErrorExplainerService explainer;
    private final InvestigationRepository history;

    public LogController(LogAnalysisService analysisService, ErrorExplainerService explainer,
                         InvestigationRepository history) {
        this.history = history;
        this.analysisService = analysisService;
        this.explainer = explainer;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping(value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public AnalysisResultDTO analyze(@RequestParam("file") MultipartFile file)
            throws IOException {
        return analysisService.analyze(file.getInputStream());
    }

    @PostMapping(value = "/investigate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InvestigationDTO investigate(@RequestParam("file") MultipartFile file) throws IOException {
        AnalysisResultDTO analysis = analysisService.analyze(file.getInputStream());
        Map.Entry<String, Integer> mostFrequent = null;
        // Errors are in first-seen order. A tie keeps the earlier message.
        for (var error : analysis.getErrors().entrySet()) {
            if (mostFrequent == null || error.getValue() > mostFrequent.getValue()) {
                mostFrequent = error;
            }
        }
        if (mostFrequent == null) {
            return history.save(analysis, null);
        }

        String message = mostFrequent.getKey();
        var topError = new InvestigationDTO.TopError(message, mostFrequent.getValue(), explainer.explain(message));
        return history.save(analysis, topError);
    }

    @GetMapping("/investigations")
    public List<InvestigationSummaryDTO> recentInvestigations() {
        return history.recent();
    }

    @GetMapping("/investigations/{id}")
    public ResponseEntity<InvestigationDTO> investigation(@PathVariable UUID id) {
        return ResponseEntity.of(history.find(id));
    }
}
