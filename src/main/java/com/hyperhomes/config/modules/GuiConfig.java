package com.hyperhomes.config.modules;

import com.google.gson.JsonObject;
import com.hyperhomes.config.ModuleConfig;
import com.hyperhomes.config.ValidationResult;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * GUI configuration module.
 * Controls homes GUI display settings.
 */
public class GuiConfig extends ModuleConfig {

    private int homesPerPage = 6;
    private boolean confirmDelete = true;

    public GuiConfig(@NotNull Path filePath) {
        super(filePath);
    }

    @Override
    @NotNull
    public String getModuleName() {
        return "gui";
    }

    @Override
    protected void createDefaults() {
        // All defaults are set via field initializers
    }

    @Override
    protected void loadModuleSettings(@NotNull JsonObject root) {
        homesPerPage = getInt(root, "homesPerPage", homesPerPage);
        confirmDelete = getBool(root, "confirmDelete", confirmDelete);
    }

    @Override
    protected void writeModuleSettings(@NotNull JsonObject root) {
        root.addProperty("homesPerPage", homesPerPage);
        root.addProperty("confirmDelete", confirmDelete);
    }

    @Override
    @NotNull
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        homesPerPage = validateRange(result, "homesPerPage", homesPerPage, 1, 24, 6);
        return result;
    }

    // === Getters ===

    public int getHomesPerPage() { return homesPerPage; }
    public boolean isConfirmDelete() { return confirmDelete; }

    // === Setters ===

    public void setHomesPerPage(int homesPerPage) {
        this.homesPerPage = Math.max(1, Math.min(24, homesPerPage));
    }

    public void setConfirmDelete(boolean confirmDelete) {
        this.confirmDelete = confirmDelete;
    }
}
