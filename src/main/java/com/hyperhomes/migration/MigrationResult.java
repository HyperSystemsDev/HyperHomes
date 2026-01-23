package com.hyperhomes.migration;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a migration operation.
 */
public record MigrationResult(
    int playersProcessed,
    int homesImported,
    int homesSkipped,
    int homesFailed,
    @NotNull List<String> warnings,
    @NotNull List<String> errors,
    long durationMs
) {
    /**
     * Creates a successful result with no issues.
     */
    public static MigrationResult success(int players, int imported, int skipped, long durationMs) {
        return new MigrationResult(players, imported, skipped, 0,
            Collections.emptyList(), Collections.emptyList(), durationMs);
    }

    /**
     * Creates a failed result with an error.
     */
    public static MigrationResult failure(String error) {
        return new MigrationResult(0, 0, 0, 0,
            Collections.emptyList(), List.of(error), 0);
    }

    /**
     * @return true if the migration completed without errors
     */
    public boolean isSuccess() {
        return errors.isEmpty();
    }

    /**
     * @return true if there were any warnings
     */
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    /**
     * Builder for creating migration results.
     */
    public static class Builder {
        private int playersProcessed = 0;
        private int homesImported = 0;
        private int homesSkipped = 0;
        private int homesFailed = 0;
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private long startTime;

        public Builder() {
            this.startTime = System.currentTimeMillis();
        }

        public Builder playersProcessed(int count) {
            this.playersProcessed = count;
            return this;
        }

        public Builder incrementPlayers() {
            this.playersProcessed++;
            return this;
        }

        public Builder incrementImported() {
            this.homesImported++;
            return this;
        }

        public Builder incrementSkipped() {
            this.homesSkipped++;
            return this;
        }

        public Builder incrementFailed() {
            this.homesFailed++;
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder addError(String error) {
            this.errors.add(error);
            return this;
        }

        public MigrationResult build() {
            long duration = System.currentTimeMillis() - startTime;
            return new MigrationResult(
                playersProcessed,
                homesImported,
                homesSkipped,
                homesFailed,
                Collections.unmodifiableList(new ArrayList<>(warnings)),
                Collections.unmodifiableList(new ArrayList<>(errors)),
                duration
            );
        }
    }
}
