package com.hyperhomes.config.modules;

import com.google.gson.JsonObject;
import com.hyperhomes.config.ModuleConfig;
import com.hyperhomes.config.ValidationResult;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Home sharing configuration module.
 * Controls sharing behavior and limits.
 */
public class ShareConfig extends ModuleConfig {

    private int maxSharesPerHome = 10;
    private boolean requireAcceptance = false;
    private int expirationMinutes = 60;

    public ShareConfig(@NotNull Path filePath) {
        super(filePath);
    }

    @Override
    @NotNull
    public String getModuleName() {
        return "share";
    }

    @Override
    protected void createDefaults() {
        // All defaults are set via field initializers
    }

    @Override
    protected void loadModuleSettings(@NotNull JsonObject root) {
        maxSharesPerHome = getInt(root, "maxSharesPerHome", maxSharesPerHome);
        requireAcceptance = getBool(root, "requireAcceptance", requireAcceptance);
        expirationMinutes = getInt(root, "expirationMinutes", expirationMinutes);
    }

    @Override
    protected void writeModuleSettings(@NotNull JsonObject root) {
        root.addProperty("maxSharesPerHome", maxSharesPerHome);
        root.addProperty("requireAcceptance", requireAcceptance);
        root.addProperty("expirationMinutes", expirationMinutes);
    }

    @Override
    @NotNull
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        maxSharesPerHome = validateRange(result, "maxSharesPerHome", maxSharesPerHome, 1, 100, 10);
        expirationMinutes = validateRange(result, "expirationMinutes", expirationMinutes, 1, 1440, 60);
        return result;
    }

    // === Getters ===

    public int getMaxSharesPerHome() { return maxSharesPerHome; }
    public boolean isRequireAcceptance() { return requireAcceptance; }
    public int getExpirationMinutes() { return expirationMinutes; }
}
