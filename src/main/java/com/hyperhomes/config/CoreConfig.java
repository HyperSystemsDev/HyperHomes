package com.hyperhomes.config;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Core configuration for HyperHomes.
 * <p>
 * Contains general plugin settings that don't fit into a specific module.
 * Stored in config.json.
 */
public class CoreConfig extends ConfigFile {

    // General
    private int defaultHomeLimit = 3;

    // Prefix/colors
    private String prefixText = "HyperHomes";
    private String prefixColor = "#FFAA00";
    private String prefixBracketColor = "#AAAAAA";
    private String primaryColor = "#55FFFF";

    // Update settings
    private boolean updateCheckEnabled = true;
    private String updateCheckUrl = "https://api.github.com/repos/ZenithDevHQ/HyperHomes/releases/latest";
    private String releaseChannel = "stable";

    // Permission settings
    private boolean adminRequiresOp = true;
    private boolean allowWithoutPermissionMod = true;

    // HyperFactions integration
    private boolean restrictHomesInEnemyTerritory = false;
    private boolean showTerritoryInfoOnHomes = true;

    // Config version for migration tracking
    private int configVersion = 2;

    public CoreConfig(@NotNull Path filePath) {
        super(filePath);
    }

    @Override
    protected void createDefaults() {
        // All defaults are set via field initializers
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        defaultHomeLimit = getInt(root, "defaultHomeLimit", defaultHomeLimit);
        prefixText = getString(root, "prefixText", prefixText);
        prefixColor = getString(root, "prefixColor", prefixColor);
        prefixBracketColor = getString(root, "prefixBracketColor", prefixBracketColor);
        primaryColor = getString(root, "primaryColor", primaryColor);
        updateCheckEnabled = getBool(root, "updateCheckEnabled", updateCheckEnabled);
        updateCheckUrl = getString(root, "updateCheckUrl", updateCheckUrl);
        releaseChannel = getString(root, "releaseChannel", releaseChannel);
        adminRequiresOp = getBool(root, "adminRequiresOp", adminRequiresOp);
        allowWithoutPermissionMod = getBool(root, "allowWithoutPermissionMod", allowWithoutPermissionMod);
        restrictHomesInEnemyTerritory = getBool(root, "restrictHomesInEnemyTerritory", restrictHomesInEnemyTerritory);
        showTerritoryInfoOnHomes = getBool(root, "showTerritoryInfoOnHomes", showTerritoryInfoOnHomes);
        configVersion = getInt(root, "configVersion", configVersion);
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("defaultHomeLimit", defaultHomeLimit);
        root.addProperty("prefixText", prefixText);
        root.addProperty("prefixColor", prefixColor);
        root.addProperty("prefixBracketColor", prefixBracketColor);
        root.addProperty("primaryColor", primaryColor);
        root.addProperty("updateCheckEnabled", updateCheckEnabled);
        root.addProperty("updateCheckUrl", updateCheckUrl);
        root.addProperty("releaseChannel", releaseChannel);
        root.addProperty("adminRequiresOp", adminRequiresOp);
        root.addProperty("allowWithoutPermissionMod", allowWithoutPermissionMod);
        root.addProperty("restrictHomesInEnemyTerritory", restrictHomesInEnemyTerritory);
        root.addProperty("showTerritoryInfoOnHomes", showTerritoryInfoOnHomes);
        root.addProperty("configVersion", configVersion);
        return root;
    }

    @Override
    @NotNull
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        defaultHomeLimit = validateMin(result, "defaultHomeLimit", defaultHomeLimit, 1, 3);
        validateHexColor(result, "prefixColor", prefixColor);
        validateHexColor(result, "prefixBracketColor", prefixBracketColor);
        validateHexColor(result, "primaryColor", primaryColor);
        releaseChannel = validateEnum(result, "releaseChannel", releaseChannel,
                new String[]{"stable", "beta", "dev"}, "stable");
        return result;
    }

    // === Getters ===

    public int getDefaultHomeLimit() { return defaultHomeLimit; }
    public String getPrefixText() { return prefixText; }
    public String getPrefixColor() { return prefixColor; }
    public String getPrefixBracketColor() { return prefixBracketColor; }
    public String getPrimaryColor() { return primaryColor; }
    public boolean isUpdateCheckEnabled() { return updateCheckEnabled; }
    public String getUpdateCheckUrl() { return updateCheckUrl; }
    public String getReleaseChannel() { return releaseChannel; }
    public boolean isAdminRequiresOp() { return adminRequiresOp; }
    public boolean isAllowWithoutPermissionMod() { return allowWithoutPermissionMod; }
    public boolean isRestrictHomesInEnemyTerritory() { return restrictHomesInEnemyTerritory; }
    public boolean isShowTerritoryInfoOnHomes() { return showTerritoryInfoOnHomes; }
    public int getConfigVersion() { return configVersion; }

    // === Setters (for live config editing) ===

    public void setDefaultHomeLimit(int defaultHomeLimit) {
        this.defaultHomeLimit = Math.max(1, Math.min(100, defaultHomeLimit));
    }

    public void setRestrictHomesInEnemyTerritory(boolean restrict) {
        this.restrictHomesInEnemyTerritory = restrict;
    }

    public void setShowTerritoryInfoOnHomes(boolean show) {
        this.showTerritoryInfoOnHomes = show;
    }
}
