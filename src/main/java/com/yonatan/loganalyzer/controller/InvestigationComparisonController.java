package com.yonatan.loganalyzer.controller;

import com.yonatan.loganalyzer.dto.ComparisonDTO;
import com.yonatan.loganalyzer.repository.InvestigationRepository;
import com.yonatan.loganalyzer.service.InvestigationComparisonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class InvestigationComparisonController {
    private final InvestigationRepository history;
    private final InvestigationComparisonService comparison;

    public InvestigationComparisonController(InvestigationRepository history, InvestigationComparisonService comparison) {
        this.history = history;
        this.comparison = comparison;
    }

    @GetMapping("/investigations/compare")
    public ResponseEntity<ComparisonDTO> compare(@RequestParam UUID before, @RequestParam UUID after) {
        var first = history.find(before);
        if (first.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var second = before.equals(after) ? first : history.find(after);
        return second.map(report -> ResponseEntity.ok(comparison.compare(first.get(), report)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
