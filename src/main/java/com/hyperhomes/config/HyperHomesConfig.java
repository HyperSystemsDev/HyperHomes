package com.hyperhomes.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for HyperHomes.
 */
public class HyperHomesConfig {

    private static HyperHomesConfig instance;

    // Default home limit
    private int defaultHomeLimit = 3;

    // Teleport settings
    private int warmupSeconds = 3;
    private int cooldownSeconds = 5;
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;
    private boolean allowCrossWorld = true;

    // Safety settings
    private boolean safeTeleport = true;
    private int safeRadius = 3;

    // Bed sync settings
    private boolean bedSyncEnabled = true;
    private String bedSyncHomeName = "bed";

    // GUI settings
    private boolean guiEnabled = true;
    private int guiHomesPerPage = 6;
    private boolean guiConfirmDelete = true;

    // Update settings
    private boolean updateCheckEnabled = true;
    private String updateCheckUrl = "https://api.github.com/repos/ZenithDevHQ/HyperHomes/releases/latest";

    // Message settings
    private String prefix = "\u00A76[HyperHomes]\u00A7r ";
    private String homeSet = "\u00A7aHome '%name%' has been set!";
    private String homeDeleted = "\u00A7aHome '%name%' has been deleted.";
    private String teleporting = "\u00A7eTeleporting to '%name%' in %seconds% seconds...";
    private String teleportCancelled = "\u00A7cTeleportation cancelled.";
    private String homeNotFound = "\u00A7cHome '%name%' not found.";
    private String homeLimitReached = "\u00A7cYou have reached your home limit (%limit%).";
    private String noHomes = "\u00A77You don't have any homes set.";
    private String teleportSuccess = "\u00A7aTeleported to '%name%'.";
    private String onCooldown = "\u00A7cYou must wait %time% before teleporting again.";

    private HyperHomesConfig() {}

    /**
     * Gets the singleton config instance.
     *
     * @return the config instance
     */
    public static HyperHomesConfig get() {
        if (instance == null) {
            instance = new HyperHomesConfig();
        }
        return instance;
    }

    /**
     * Loads the configuration from file.
     *
     * @param dataDir the plugin data directory
     */
    public void load(@NotNull Path dataDir) {
        Path configFile = dataDir.resolve("config.json");
        Logger.info("[Config] Loading from: %s", configFile.toAbsolutePath());

        if (!Files.exists(configFile)) {
            Logger.info("[Config] Config file not found, creating default");
            save(dataDir);
            return;
        }

        try {
            String json = Files.readString(configFile);
            Logger.info("[Config] Read %d bytes from config file", json.length());
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            // Load values with defaults
            defaultHomeLimit = getInt(root, "defaultHomeLimit", defaultHomeLimit);
            Logger.info("[Config] Loaded defaultHomeLimit = %d", defaultHomeLimit);

            if (root.has("teleport") && root.get("teleport").isJsonObject()) {
                JsonObject teleport = root.getAsJsonObject("teleport");
                warmupSeconds = getInt(teleport, "warmup", warmupSeconds);
                cooldownSeconds = getInt(teleport, "cooldown", cooldownSeconds);
                cancelOnMove = getBool(teleport, "cancelOnMove", cancelOnMove);
                cancelOnDamage = getBool(teleport, "cancelOnDamage", cancelOnDamage);
                allowCrossWorld = getBool(teleport, "allowCrossWorld", allowCrossWorld);
                Logger.info("[Config] Loaded teleport: warmup=%d, cooldown=%d, cancelOnMove=%s",
                    warmupSeconds, cooldownSeconds, cancelOnMove);
            } else {
                Logger.warn("[Config] No 'teleport' section found in config!");
            }

            if (root.has("safety") && root.get("safety").isJsonObject()) {
                JsonObject safety = root.getAsJsonObject("safety");
                safeTeleport = getBool(safety, "safeTeleport", safeTeleport);
                safeRadius = getInt(safety, "safeRadius", safeRadius);
            }

            if (root.has("bedSync") && root.get("bedSync").isJsonObject()) {
                JsonObject bedSync = root.getAsJsonObject("bedSync");
                bedSyncEnabled = getBool(bedSync, "enabled", bedSyncEnabled);
                bedSyncHomeName = getString(bedSync, "homeName", bedSyncHomeName);
            }

            if (root.has("gui") && root.get("gui").isJsonObject()) {
                JsonObject gui = root.getAsJsonObject("gui");
                guiEnabled = getBool(gui, "enabled", guiEnabled);
                guiHomesPerPage = getInt(gui, "homesPerPage", guiHomesPerPage);
                guiConfirmDelete = getBool(gui, "confirmDelete", guiConfirmDelete);
            }

            if (root.has("updates") && root.get("updates").isJsonObject()) {
                JsonObject updates = root.getAsJsonObject("updates");
                updateCheckEnabled = getBool(updates, "enabled", updateCheckEnabled);
                updateCheckUrl = getString(updates, "url", updateCheckUrl);
            }

            if (root.has("messages") && root.get("messages").isJsonObject()) {
                JsonObject messages = root.getAsJsonObject("messages");
                prefix = getString(messages, "prefix", prefix);
                homeSet = getString(messages, "homeSet", homeSet);
                homeDeleted = getString(messages, "homeDeleted", homeDeleted);
                teleporting = getString(messages, "teleporting", teleporting);
                teleportCancelled = getString(messages, "teleportCancelled", teleportCancelled);
                homeNotFound = getString(messages, "homeNotFound", homeNotFound);
                homeLimitReached = getString(messages, "homeLimitReached", homeLimitReached);
                noHomes = getString(messages, "noHomes", noHomes);
                teleportSuccess = getString(messages, "teleportSuccess", teleportSuccess);
                onCooldown = getString(messages, "onCooldown", onCooldown);
            }

            Logger.info("Configuration loaded");
        } catch (Exception e) {
            Logger.severe("Failed to load configuration", e);
        }
    }

    /**
     * Saves the configuration to file.
     *
     * @param dataDir the plugin data directory
     */
    public void save(@NotNull Path dataDir) {
        Path configFile = dataDir.resolve("config.json");

        try {
            Files.createDirectories(dataDir);

            JsonObject root = new JsonObject();
            root.addProperty("defaultHomeLimit", defaultHomeLimit);

            JsonObject teleport = new JsonObject();
            teleport.addProperty("warmup", warmupSeconds);
            teleport.addProperty("cooldown", cooldownSeconds);
            teleport.addProperty("cancelOnMove", cancelOnMove);
            teleport.addProperty("cancelOnDamage", cancelOnDamage);
            teleport.addProperty("allowCrossWorld", allowCrossWorld);
            root.add("teleport", teleport);

            JsonObject safety = new JsonObject();
            safety.addProperty("safeTeleport", safeTeleport);
            safety.addProperty("safeRadius", safeRadius);
            root.add("safety", safety);

            JsonObject bedSync = new JsonObject();
            bedSync.addProperty("enabled", bedSyncEnabled);
            bedSync.addProperty("homeName", bedSyncHomeName);
            root.add("bedSync", bedSync);

            JsonObject gui = new JsonObject();
            gui.addProperty("enabled", guiEnabled);
            gui.addProperty("homesPerPage", guiHomesPerPage);
            gui.addProperty("confirmDelete", guiConfirmDelete);
            root.add("gui", gui);

            JsonObject updates = new JsonObject();
            updates.addProperty("enabled", updateCheckEnabled);
            updates.addProperty("url", updateCheckUrl);
            root.add("updates", updates);

            JsonObject messages = new JsonObject();
            messages.addProperty("prefix", prefix);
            messages.addProperty("homeSet", homeSet);
            messages.addProperty("homeDeleted", homeDeleted);
            messages.addProperty("teleporting", teleporting);
            messages.addProperty("teleportCancelled", teleportCancelled);
            messages.addProperty("homeNotFound", homeNotFound);
            messages.addProperty("homeLimitReached", homeLimitReached);
            messages.addProperty("noHomes", noHomes);
            messages.addProperty("teleportSuccess", teleportSuccess);
            messages.addProperty("onCooldown", onCooldown);
            root.add("messages", messages);

            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            Files.writeString(configFile, gson.toJson(root));

            Logger.info("Configuration saved");
        } catch (IOException e) {
            Logger.severe("Failed to save configuration", e);
        }
    }

    /**
     * Reloads the configuration.
     *
     * @param dataDir the plugin data directory
     */
    public void reload(@NotNull Path dataDir) {
        load(dataDir);
    }

    // Getters
    public int getDefaultHomeLimit() { return defaultHomeLimit; }
    public int getWarmupSeconds() { return warmupSeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public boolean isCancelOnMove() { return cancelOnMove; }
    public boolean isCancelOnDamage() { return cancelOnDamage; }
    public boolean isAllowCrossWorld() { return allowCrossWorld; }
    public boolean isSafeTeleport() { return safeTeleport; }
    public int getSafeRadius() { return safeRadius; }
    public boolean isBedSyncEnabled() { return bedSyncEnabled; }
    public String getBedSyncHomeName() { return bedSyncHomeName; }
    public boolean isGuiEnabled() { return guiEnabled; }
    public int getGuiHomesPerPage() { return guiHomesPerPage; }
    public boolean isGuiConfirmDelete() { return guiConfirmDelete; }
    public boolean isUpdateCheckEnabled() { return updateCheckEnabled; }
    public String getUpdateCheckUrl() { return updateCheckUrl; }

    // Setters for live config editing
    public void setWarmupSeconds(int warmupSeconds) {
        this.warmupSeconds = Math.max(0, Math.min(60, warmupSeconds));
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, Math.min(300, cooldownSeconds));
    }

    public void setDefaultHomeLimit(int defaultHomeLimit) {
        this.defaultHomeLimit = Math.max(1, Math.min(100, defaultHomeLimit));
    }

    public void setCancelOnMove(boolean cancelOnMove) {
        this.cancelOnMove = cancelOnMove;
    }

    public void setCancelOnDamage(boolean cancelOnDamage) {
        this.cancelOnDamage = cancelOnDamage;
    }

    // Message getters with placeholder replacement
    public String getPrefix() { return prefix; }

    public String getHomeSetMessage(String name) {
        return prefix + homeSet.replace("%name%", name);
    }

    public String getHomeDeletedMessage(String name) {
        return prefix + homeDeleted.replace("%name%", name);
    }

    public String getTeleportingMessage(String name, int seconds) {
        return prefix + teleporting.replace("%name%", name).replace("%seconds%", String.valueOf(seconds));
    }

    public String getTeleportCancelledMessage() {
        return prefix + teleportCancelled;
    }

    public String getHomeNotFoundMessage(String name) {
        return prefix + homeNotFound.replace("%name%", name);
    }

    public String getHomeLimitReachedMessage(int limit) {
        return prefix + homeLimitReached.replace("%limit%", String.valueOf(limit));
    }

    public String getNoHomesMessage() {
        return prefix + noHomes;
    }

    public String getTeleportSuccessMessage(String name) {
        return prefix + teleportSuccess.replace("%name%", name);
    }

    public String getOnCooldownMessage(String timeFormatted) {
        return prefix + onCooldown.replace("%time%", timeFormatted);
    }

    // Helper methods
    private int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private String getString(JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }
}
