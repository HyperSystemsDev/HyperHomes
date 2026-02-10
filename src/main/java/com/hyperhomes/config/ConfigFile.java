package com.hyperhomes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for configuration files.
 * <p>
 * Provides common functionality for loading and saving JSON config files,
 * with helper methods for reading typed values with defaults, and validation
 * support for detecting and auto-correcting invalid values.
 */
public abstract class ConfigFile {

    protected static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    protected final Path filePath;
    protected boolean needsSave = false;
    protected ValidationResult lastValidationResult = null;

    protected ConfigFile(@NotNull Path filePath) {
        this.filePath = filePath;
    }

    @NotNull
    public Path getFilePath() {
        return filePath;
    }

    /**
     * Loads the configuration from file.
     * If the file doesn't exist, creates a new one with defaults.
     */
    public void load() {
        if (!Files.exists(filePath)) {
            Logger.info("[Config] Creating new config file: %s", filePath.getFileName());
            createDefaults();
            save();
            return;
        }

        needsSave = false;

        try {
            String json = Files.readString(filePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            loadFromJson(root);

            if (needsSave) {
                Logger.info("[Config] Adding missing keys to: %s", filePath.getFileName());
                save();
            }
        } catch (Exception e) {
            Logger.severe("[Config] Failed to load %s: %s", filePath.getFileName(), e.getMessage());
            createDefaults();
        }
    }

    /**
     * Saves the configuration to file.
     */
    public void save() {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject json = toJson();
            Files.writeString(filePath, GSON.toJson(json));
            needsSave = false;
            Logger.debug("[Config] Saved: %s", filePath.getFileName());
        } catch (IOException e) {
            Logger.severe("[Config] Failed to save %s: %s", filePath.getFileName(), e.getMessage());
        }
    }

    public void reload() {
        load();
    }

    protected abstract void loadFromJson(@NotNull JsonObject root);

    @NotNull
    protected abstract JsonObject toJson();

    protected abstract void createDefaults();

    @NotNull
    public String getConfigName() {
        return filePath.getFileName().toString();
    }

    // === Validation ===

    @NotNull
    public ValidationResult validate() {
        return new ValidationResult();
    }

    public ValidationResult getLastValidationResult() {
        return lastValidationResult;
    }

    public void validateAndLog() {
        lastValidationResult = validate();

        if (lastValidationResult.hasIssues()) {
            for (ValidationResult.Issue issue : lastValidationResult.getIssues()) {
                Logger.warn("[Config] %s", issue);
            }

            if (lastValidationResult.needsSave()) {
                Logger.info("[Config] Saving corrected values to: %s", getConfigName());
                save();
            }
        }
    }

    // === Validation Helper Methods ===

    protected int validateRange(@NotNull ValidationResult result, @NotNull String field,
                                int value, int min, int max, int defaultVal) {
        if (value < min || value > max) {
            result.addWarning(getConfigName(), field,
                    String.format("Value must be between %d and %d", min, max),
                    value, defaultVal);
            needsSave = true;
            return defaultVal;
        }
        return value;
    }

    protected int validateMin(@NotNull ValidationResult result, @NotNull String field,
                              int value, int min, int defaultVal) {
        if (value < min) {
            result.addWarning(getConfigName(), field,
                    String.format("Value must be at least %d", min),
                    value, defaultVal);
            needsSave = true;
            return defaultVal;
        }
        return value;
    }

    protected double validateRange(@NotNull ValidationResult result, @NotNull String field,
                                   double value, double min, double max, double defaultVal) {
        if (value < min || value > max) {
            result.addWarning(getConfigName(), field,
                    String.format("Value must be between %.2f and %.2f", min, max),
                    value, defaultVal);
            needsSave = true;
            return defaultVal;
        }
        return value;
    }

    protected double validateMin(@NotNull ValidationResult result, @NotNull String field,
                                 double value, double min, double defaultVal) {
        if (value < min) {
            result.addWarning(getConfigName(), field,
                    String.format("Value must be at least %.2f", min),
                    value, defaultVal);
            needsSave = true;
            return defaultVal;
        }
        return value;
    }

    @NotNull
    protected String validateEnum(@NotNull ValidationResult result, @NotNull String field,
                                  @NotNull String value, @NotNull String[] allowed, @NotNull String defaultVal) {
        for (String a : allowed) {
            if (a.equalsIgnoreCase(value)) {
                return value;
            }
        }
        result.addWarning(getConfigName(), field,
                String.format("Value must be one of: %s", String.join(", ", allowed)),
                value, defaultVal);
        needsSave = true;
        return defaultVal;
    }

    protected boolean validateHexColor(@NotNull ValidationResult result, @NotNull String field,
                                       @NotNull String value) {
        if (!value.matches("^#[0-9A-Fa-f]{6}$")) {
            result.addWarning(getConfigName(), field,
                    "Should be a hex color in format #RRGGBB", value);
            return false;
        }
        return true;
    }

    // === Helper methods for reading JSON values ===

    protected int getInt(@NotNull JsonObject obj, @NotNull String key, int defaultVal) {
        if (obj.has(key)) {
            return obj.get(key).getAsInt();
        }
        needsSave = true;
        return defaultVal;
    }

    protected double getDouble(@NotNull JsonObject obj, @NotNull String key, double defaultVal) {
        if (obj.has(key)) {
            return obj.get(key).getAsDouble();
        }
        needsSave = true;
        return defaultVal;
    }

    protected boolean getBool(@NotNull JsonObject obj, @NotNull String key, boolean defaultVal) {
        if (obj.has(key)) {
            return obj.get(key).getAsBoolean();
        }
        needsSave = true;
        return defaultVal;
    }

    @NotNull
    protected String getString(@NotNull JsonObject obj, @NotNull String key, @NotNull String defaultVal) {
        if (obj.has(key)) {
            return obj.get(key).getAsString();
        }
        needsSave = true;
        return defaultVal;
    }

    @NotNull
    protected List<String> getStringList(@NotNull JsonObject obj, @NotNull String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            obj.getAsJsonArray(key).forEach(el -> list.add(el.getAsString()));
        } else if (!obj.has(key)) {
            needsSave = true;
        }
        return list;
    }

    protected boolean hasSection(@NotNull JsonObject obj, @NotNull String sectionName) {
        if (obj.has(sectionName) && obj.get(sectionName).isJsonObject()) {
            return true;
        }
        needsSave = true;
        return false;
    }

    @NotNull
    protected JsonObject getSection(@NotNull JsonObject obj, @NotNull String sectionName) {
        if (obj.has(sectionName) && obj.get(sectionName).isJsonObject()) {
            return obj.getAsJsonObject(sectionName);
        }
        needsSave = true;
        return new JsonObject();
    }

    @NotNull
    protected JsonArray toJsonArray(@NotNull List<String> list) {
        JsonArray array = new JsonArray();
        list.forEach(array::add);
        return array;
    }
}
