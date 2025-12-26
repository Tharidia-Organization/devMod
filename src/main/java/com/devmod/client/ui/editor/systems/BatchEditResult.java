package com.devmod.client.ui.editor.systems;

import java.util.ArrayList;
import java.util.List;

public class BatchEditResult {
    public static class FailureDetail {
        public final String itemName;
        public final String itemId; // registry id if available
        public final int slot;
        public final String message;
        public final String stackTrace; // optional

        public FailureDetail(String itemName, String itemId, int slot, String message, String stackTrace) {
            this.itemName = itemName;
            this.itemId = itemId;
            this.slot = slot;
            this.message = message;
            this.stackTrace = stackTrace;
        }

        @Override
        public String toString() {
            String idPart = itemId == null ? "<unknown-id>" : itemId;
            return "slot#" + slot + " " + idPart + " (" + itemName + ") - " + message;
        }
    }

    private final List<String> successes;
    private final List<FailureDetail> failureDetails;

    public BatchEditResult(List<String> successes, List<FailureDetail> failureDetails) {
        this.successes = successes == null ? List.of() : successes;
        this.failureDetails = failureDetails == null ? List.of() : failureDetails;
    }

    public int totalCount() {
        return successes.size() + failureDetails.size();
    }

    public int successCount() {
        return successes.size();
    }

    public int failureCount() {
        return failureDetails.size();
    }

    public boolean allSucceeded() {
        return failureDetails.isEmpty();
    }

    public boolean allFailed() {
        return successes.isEmpty();
    }

    public List<String> successes() { return successes; }

    /**
     * Human-readable failure messages (backwards-compatible).
     */
    public List<String> failures() {
        List<String> out = new ArrayList<>();
        for (FailureDetail d : failureDetails) out.add(d.toString());
        return out;
    }

    /** Structured failure details for richer UI and export. */
    public List<FailureDetail> failureDetails() { return failureDetails; }
    
    /**
     * Generate clipboard-friendly error report.
     */
    public String generateErrorReport() {
        if (failureDetails.isEmpty()) {
            return "No errors to report.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== DevMod Batch Edit Errors ===").append("\n");
        sb.append("Total items: ").append(totalCount()).append("\n");
        sb.append("Successes: ").append(successCount()).append("\n");
        sb.append("Failures: ").append(failureCount()).append("\n\n");
        
        sb.append("--- Error Details ---\n");
        for (int i = 0; i < failureDetails.size(); i++) {
            FailureDetail detail = failureDetails.get(i);
            sb.append(String.format("%d. %s\n", i + 1, detail.toString()));
            if (detail.stackTrace != null && !detail.stackTrace.trim().isEmpty()) {
                sb.append("   Stack trace: ").append(detail.stackTrace.split("\n")[0]).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
