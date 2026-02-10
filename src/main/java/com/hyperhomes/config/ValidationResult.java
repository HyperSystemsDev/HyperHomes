package com.hyperhomes.config;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of configuration validation.
 * <p>
 * Collects warnings and errors found during validation. Warnings indicate
 * invalid values that were corrected to defaults. Errors indicate values
 * that couldn't be corrected but won't crash the plugin.
 */
public class ValidationResult {

    public enum Severity {
        WARNING,
        ERROR
    }

    public record Issue(
        @NotNull Severity severity,
        @NotNull String configFile,
        @NotNull String field,
        @NotNull String message,
        @NotNull String originalValue,
        String correctedValue
    ) {
        public static Issue warning(@NotNull String configFile, @NotNull String field,
                                    @NotNull String message, Object original, Object corrected) {
            return new Issue(Severity.WARNING, configFile, field, message,
                    String.valueOf(original), String.valueOf(corrected));
        }

        public static Issue warning(@NotNull String configFile, @NotNull String field,
                                    @NotNull String message, Object original) {
            return new Issue(Severity.WARNING, configFile, field, message,
                    String.valueOf(original), null);
        }

        public static Issue error(@NotNull String configFile, @NotNull String field,
                                  @NotNull String message, Object original) {
            return new Issue(Severity.ERROR, configFile, field, message,
                    String.valueOf(original), null);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(severity).append("] ");
            sb.append(configFile).append(" -> ").append(field).append(": ");
            sb.append(message);
            sb.append(" (was: ").append(originalValue);
            if (correctedValue != null) {
                sb.append(", corrected to: ").append(correctedValue);
            }
            sb.append(")");
            return sb.toString();
        }
    }

    private final List<Issue> issues = new ArrayList<>();
    private boolean needsSave = false;

    public void addIssue(@NotNull Issue issue) {
        issues.add(issue);
        if (issue.correctedValue() != null) {
            needsSave = true;
        }
    }

    public void addWarning(@NotNull String configFile, @NotNull String field,
                          @NotNull String message, Object original, Object corrected) {
        addIssue(Issue.warning(configFile, field, message, original, corrected));
    }

    public void addWarning(@NotNull String configFile, @NotNull String field,
                          @NotNull String message, Object original) {
        addIssue(Issue.warning(configFile, field, message, original));
    }

    public void addError(@NotNull String configFile, @NotNull String field,
                        @NotNull String message, Object original) {
        addIssue(Issue.error(configFile, field, message, original));
    }

    @NotNull
    public List<Issue> getIssues() {
        return issues;
    }

    @NotNull
    public List<Issue> getWarnings() {
        return issues.stream()
                .filter(i -> i.severity() == Severity.WARNING)
                .toList();
    }

    @NotNull
    public List<Issue> getErrors() {
        return issues.stream()
                .filter(i -> i.severity() == Severity.ERROR)
                .toList();
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }

    public boolean needsSave() {
        return needsSave;
    }

    public int size() {
        return issues.size();
    }

    public void merge(@NotNull ValidationResult other) {
        issues.addAll(other.issues);
        if (other.needsSave) {
            needsSave = true;
        }
    }
}
