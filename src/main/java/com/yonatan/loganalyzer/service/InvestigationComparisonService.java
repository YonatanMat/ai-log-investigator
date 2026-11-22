package com.yonatan.loganalyzer.service;

import com.yonatan.loganalyzer.dto.ComparisonDTO;
import com.yonatan.loganalyzer.dto.InvestigationDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.TreeSet;

@Service
public class InvestigationComparisonService {
    public ComparisonDTO compare(InvestigationDTO before, InvestigationDTO after) {
        var beforeErrors = before.analysis().getErrors();
        var afterErrors = after.analysis().getErrors();
        // The sorted union includes messages that appeared in only one report.
        var messages = new TreeSet<>(beforeErrors.keySet());
        messages.addAll(afterErrors.keySet());

        var changes = new ArrayList<ComparisonDTO.ErrorChange>();
        for (String message : messages) {
            int oldCount = beforeErrors.getOrDefault(message, 0);
            int newCount = afterErrors.getOrDefault(message, 0);
            if (oldCount != newCount) {
                changes.add(new ComparisonDTO.ErrorChange(message, oldCount, newCount, newCount - oldCount));
            }
        }
        return new ComparisonDTO(
                new ComparisonDTO.Report(before.id(), before.analysis().getTotalEntries()),
                new ComparisonDTO.Report(after.id(), after.analysis().getTotalEntries()), changes);
    }
}
