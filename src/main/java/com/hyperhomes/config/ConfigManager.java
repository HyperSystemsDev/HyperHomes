package com.hyperhomes.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperhomes.config.modules.*;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Central manager for all HyperHomes configuration.
 * <p>
 * Orchestrates loading of the core config and all module configs,
 * handles migration from the old flat config.json format, and
 * provides unified access to all settings.
 */
public class ConfigManager {

    private static ConfigManager instance;

    private Path dataDir;
    private CoreConfig coreConfig;
    private TeleportConfig teleportConfig;
    private GuiConfig guiConfig;
    private ShareConfig shareConfig;
    private BedSyncConfig bedSyncConfig;

    private ConfigManager() {}

    /**
     * Gets the singleton config manager instance.
     *
     * @return config manager
     */
    @NotNull
    public static ConfigManager get() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    /**
     * Loads all configuration files.
     * <p>
     * This method:
     * <ol>
     *   <li>Checks for and runs migration from old flat config.json</li>
     *   <li>Loads the core config from config.json</li>
     *   <li>Loads all module configs from config/ directory</li>
     *   <li>Validates all configs</li>
     * </ol>
     *
     * @param dataDir the plugin data directory
     */
    public void loadAll(@NotNull Path dataDir) {
        this.dataDir = dataDir;
        Logger.info("[Config] Loading configuration from: %s", dataDir.toAbsolutePath());

        // Step 1: Migrate old flat config.json if needed
        migrateOldConfig();

        // Step 2: Load core config
        coreConfig = new CoreConfig(dataDir.resolve("config.json"));
        coreConfig.load();

        // Step 3: Load module configs from config/ subdirectory
        Path configDir = dataDir.resolve("config");

        teleportConfig = new TeleportConfig(configDir.resolve("teleport.json"));
        teleportConfig.load();

        guiConfig = new GuiConfig(configDir.resolve("gui.json"));
        guiConfig.load();

        shareConfig = new ShareConfig(configDir.resolve("share.json"));
        shareConfig.load();

        bedSyncConfig = new BedSyncConfig(configDir.resolve("bed-sync.json"));
        bedSyncConfig.load();

        // Step 4: Validate all configs and log any issues
        validateAll();

        Logger.info("[Config] Configuration loaded successfully");
    }

    /**
     * Validates all configuration files and logs any issues found.
     */
    private void validateAll() {
        ValidationResult combined = new ValidationResult();

        coreConfig.validateAndLog();
        if (coreConfig.getLastValidationResult() != null) {
            combined.merge(coreConfig.getLastValidationResult());
        }

        teleportConfig.validateAndLog();
        if (teleportConfig.getLastValidationResult() != null) {
            combined.merge(teleportConfig.getLastValidationResult());
        }

        guiConfig.validateAndLog();
        if (guiConfig.getLastValidationResult() != null) {
            combined.merge(guiConfig.getLastValidationResult());
        }

        shareConfig.validateAndLog();
        if (shareConfig.getLastValidationResult() != null) {
            combined.merge(shareConfig.getLastValidationResult());
        }

        bedSyncConfig.validateAndLog();
        if (bedSyncConfig.getLastValidationResult() != null) {
            combined.merge(bedSyncConfig.getLastValidationResult());
        }

        if (combined.hasIssues()) {
            int warnings = combined.getWarnings().size();
            int errors = combined.getErrors().size();
            Logger.info("[Config] Validation complete: %d warning(s), %d error(s)", warnings, errors);
        }
    }

    /**
     * Migrates old flat config.json to the new split format.
     * <p>
     * Detects the old format by checking for the presence of the "teleport" section
     * (which exists in the old format but not the new one). If found, extracts values
     * into the new split config files and rewrites the core config.json.
     */
    private void migrateOldConfig() {
        Path oldConfigFile = dataDir.resolve("config.json");
        if (!Files.exists(oldConfigFile)) {
            return;
        }

        try {
            String json = Files.readString(oldConfigFile);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Check if this is the old format (has "teleport" section)
            if (!root.has("teleport") || !root.get("teleport").isJsonObject()) {
                return; // Already new format or empty
            }

            Logger.info("[Config] Detected old config.json format, migrating to split configs...");

            Path configDir = dataDir.resolve("config");
            Files.createDirectories(configDir);

            // Extract teleport settings
            JsonObject teleportSection = root.getAsJsonObject("teleport");
            JsonObject newTeleport = new JsonObject();
            newTeleport.addProperty("enabled", true);
            copyPropInt(teleportSection, newTeleport, "warmup", "warmupSeconds");
            copyPropInt(teleportSection, newTeleport, "cooldown", "cooldownSeconds");
            copyPropBool(teleportSection, newTeleport, "cancelOnMove", "cancelOnMove");
            copyPropBool(teleportSection, newTeleport, "cancelOnDamage", "cancelOnDamage");
            copyPropBool(teleportSection, newTeleport, "allowCrossWorld", "allowCrossWorld");
            // Safety settings were in a separate section in old config
            if (root.has("safety") && root.get("safety").isJsonObject()) {
                JsonObject safety = root.getAsJsonObject("safety");
                copyPropBool(safety, newTeleport, "safeTeleport", "safeTeleport");
                copyPropInt(safety, newTeleport, "safeRadius", "safeRadius");
            }
            Files.writeString(configDir.resolve("teleport.json"),
                    ConfigFile.GSON.toJson(newTeleport));

            // Extract GUI settings
            if (root.has("gui") && root.get("gui").isJsonObject()) {
                JsonObject guiSection = root.getAsJsonObject("gui");
                JsonObject newGui = new JsonObject();
                newGui.addProperty("enabled", guiSection.has("enabled") ? guiSection.get("enabled").getAsBoolean() : true);
                copyPropInt(guiSection, newGui, "homesPerPage", "homesPerPage");
                copyPropBool(guiSection, newGui, "confirmDelete", "confirmDelete");
                Files.writeString(configDir.resolve("gui.json"),
                        ConfigFile.GSON.toJson(newGui));
            }

            // Extract bed sync settings
            if (root.has("bedSync") && root.get("bedSync").isJsonObject()) {
                JsonObject bedSyncSection = root.getAsJsonObject("bedSync");
                JsonObject newBedSync = new JsonObject();
                newBedSync.addProperty("enabled", bedSyncSection.has("enabled") ? bedSyncSection.get("enabled").getAsBoolean() : true);
                copyPropStr(bedSyncSection, newBedSync, "homeName", "homeName");
                Files.writeString(configDir.resolve("bed-sync.json"),
                        ConfigFile.GSON.toJson(newBedSync));
            }

            // Create default share config (didn't exist in old format)
            JsonObject newShare = new JsonObject();
            newShare.addProperty("enabled", true);
            newShare.addProperty("maxSharesPerHome", 10);
            newShare.addProperty("requireAcceptance", false);
            newShare.addProperty("expirationMinutes", 60);
            Files.writeString(configDir.resolve("share.json"),
                    ConfigFile.GSON.toJson(newShare));

            // Rewrite config.json as new core config format
            JsonObject newCore = new JsonObject();
            if (root.has("defaultHomeLimit")) {
                newCore.addProperty("defaultHomeLimit", root.get("defaultHomeLimit").getAsInt());
            }
            // Extract update settings
            if (root.has("updates") && root.get("updates").isJsonObject()) {
                JsonObject updates = root.getAsJsonObject("updates");
                if (updates.has("enabled")) {
                    newCore.addProperty("updateCheckEnabled", updates.get("enabled").getAsBoolean());
                }
                copyPropStr(updates, newCore, "url", "updateCheckUrl");
            }
            // Set defaults for new fields
            newCore.addProperty("prefixText", "HyperHomes");
            newCore.addProperty("prefixColor", "#FFAA00");
            newCore.addProperty("prefixBracketColor", "#AAAAAA");
            newCore.addProperty("primaryColor", "#55FFFF");
            newCore.addProperty("adminRequiresOp", true);
            newCore.addProperty("allowWithoutPermissionMod", true);
            newCore.addProperty("restrictHomesInEnemyTerritory", false);
            newCore.addProperty("showTerritoryInfoOnHomes", true);
            newCore.addProperty("configVersion", 2);

            // Backup old config before overwriting
            Path backupPath = dataDir.resolve("config.json.v1.bak");
            if (!Files.exists(backupPath)) {
                Files.copy(oldConfigFile, backupPath);
                Logger.info("[Config] Old config backed up to: %s", backupPath.getFileName());
            }

            Files.writeString(oldConfigFile, ConfigFile.GSON.toJson(newCore));

            Logger.info("[Config] Migration complete! Split configs created in config/ directory");

        } catch (IOException e) {
            Logger.severe("[Config] Failed to migrate old config: %s", e.getMessage());
        } catch (Exception e) {
            Logger.severe("[Config] Error during config migration: %s", e.getMessage());
        }
    }

    private void copyPropInt(JsonObject from, JsonObject to, String fromKey, String toKey) {
        if (from.has(fromKey)) {
            to.addProperty(toKey, from.get(fromKey).getAsInt());
        }
    }

    private void copyPropBool(JsonObject from, JsonObject to, String fromKey, String toKey) {
        if (from.has(fromKey)) {
            to.addProperty(toKey, from.get(fromKey).getAsBoolean());
        }
    }

    private void copyPropStr(JsonObject from, JsonObject to, String fromKey, String toKey) {
        if (from.has(fromKey)) {
            to.addProperty(toKey, from.get(fromKey).getAsString());
        }
    }

    /**
     * Reloads all configuration files.
     */
    public void reloadAll() {
        Logger.info("[Config] Reloading configuration...");

        coreConfig.reload();
        teleportConfig.reload();
        guiConfig.reload();
        shareConfig.reload();
        bedSyncConfig.reload();

        validateAll();

        Logger.info("[Config] Configuration reloaded");
    }

    /**
     * Saves all configuration files.
     */
    public void saveAll() {
        coreConfig.save();
        teleportConfig.save();
        guiConfig.save();
        shareConfig.save();
        bedSyncConfig.save();
    }

    // === Config Accessors ===

    @NotNull
    public CoreConfig core() {
        return coreConfig;
    }

    @NotNull
    public TeleportConfig teleport() {
        return teleportConfig;
    }

    @NotNull
    public GuiConfig gui() {
        return guiConfig;
    }

    @NotNull
    public ShareConfig share() {
        return shareConfig;
    }

    @NotNull
    public BedSyncConfig bedSync() {
        return bedSyncConfig;
    }

    // === Convenience Methods (for backward compatibility) ===

    // Core
    public int getDefaultHomeLimit() { return coreConfig.getDefaultHomeLimit(); }
    @NotNull public String getPrefixText() { return coreConfig.getPrefixText(); }
    @NotNull public String getPrefixColor() { return coreConfig.getPrefixColor(); }
    @NotNull public String getPrefixBracketColor() { return coreConfig.getPrefixBracketColor(); }
    @NotNull public String getPrimaryColor() { return coreConfig.getPrimaryColor(); }
    public boolean isUpdateCheckEnabled() { return coreConfig.isUpdateCheckEnabled(); }
    @NotNull public String getUpdateCheckUrl() { return coreConfig.getUpdateCheckUrl(); }
    public boolean isAdminRequiresOp() { return coreConfig.isAdminRequiresOp(); }
    public boolean isAllowWithoutPermissionMod() { return coreConfig.isAllowWithoutPermissionMod(); }

    // Teleport
    public int getWarmupSeconds() { return teleportConfig.getWarmupSeconds(); }
    public int getCooldownSeconds() { return teleportConfig.getCooldownSeconds(); }
    public boolean isCancelOnMove() { return teleportConfig.isCancelOnMove(); }
    public boolean isCancelOnDamage() { return teleportConfig.isCancelOnDamage(); }
    public boolean isAllowCrossWorld() { return teleportConfig.isAllowCrossWorld(); }
    public boolean isSafeTeleport() { return teleportConfig.isSafeTeleport(); }
    public int getSafeRadius() { return teleportConfig.getSafeRadius(); }

    // GUI
    public int getHomesPerPage() { return guiConfig.getHomesPerPage(); }
    public boolean isConfirmDelete() { return guiConfig.isConfirmDelete(); }

    // Bed Sync
    public boolean isBedSyncEnabled() { return bedSyncConfig.isEnabled(); }
    @NotNull public String getBedSyncHomeName() { return bedSyncConfig.getHomeName(); }

    // Share
    public int getMaxSharesPerHome() { return shareConfig.getMaxSharesPerHome(); }
    public boolean isRequireAcceptance() { return shareConfig.isRequireAcceptance(); }
    public int getExpirationMinutes() { return shareConfig.getExpirationMinutes(); }

    // Faction integration
    public boolean isRestrictHomesInEnemyTerritory() { return coreConfig.isRestrictHomesInEnemyTerritory(); }
    public boolean isShowTerritoryInfoOnHomes() { return coreConfig.isShowTerritoryInfoOnHomes(); }

    // === Message Formatting (backward compatibility) ===

    /**
     * Gets the chat prefix string with color codes.
     */
    @NotNull
    public String getPrefix() {
        return "\u00A77[\u00A76" + coreConfig.getPrefixText() + "\u00A77] ";
    }

    /**
     * Gets a teleporting warmup message.
     */
    @NotNull
    public String getTeleportingMessage(@NotNull String homeName, int seconds) {
        return getPrefix() + "\u00A7eTeleporting to \u00A7b" + homeName
                + "\u00A7e in \u00A7f" + seconds + "\u00A7e second" + (seconds != 1 ? "s" : "") + "...";
    }

    /**
     * Gets a teleport success message.
     */
    @NotNull
    public String getTeleportSuccessMessage(@NotNull String homeName) {
        return getPrefix() + "\u00A7aTeleported to \u00A7b" + homeName + "\u00A7a!";
    }

    /**
     * Gets a teleport cancelled message.
     */
    @NotNull
    public String getTeleportCancelledMessage() {
        return getPrefix() + "\u00A7cTeleportation cancelled.";
    }
}
