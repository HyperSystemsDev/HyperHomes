package com.hyperhomes.config.modules;

import com.google.gson.JsonObject;
import com.hyperhomes.config.ModuleConfig;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Bed sync configuration module.
 * Controls automatic home creation when players set bed spawn.
 */
public class BedSyncConfig extends ModuleConfig {

    private String homeName = "bed";

    public BedSyncConfig(@NotNull Path filePath) {
        super(filePath);
    }

    @Override
    @NotNull
    public String getModuleName() {
        return "bed-sync";
    }

    @Override
    protected void createDefaults() {
        // All defaults are set via field initializers
    }

    @Override
    protected void loadModuleSettings(@NotNull JsonObject root) {
        homeName = getString(root, "homeName", homeName);
    }

    @Override
    protected void writeModuleSettings(@NotNull JsonObject root) {
        root.addProperty("homeName", homeName);
    }

    // === Getters ===

    public String getHomeName() { return homeName; }
}
