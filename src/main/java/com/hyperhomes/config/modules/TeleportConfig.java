package com.hyperhomes.config.modules;

import com.google.gson.JsonObject;
import com.hyperhomes.config.ModuleConfig;
import com.hyperhomes.config.ValidationResult;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Teleportation configuration module.
 * Controls warmup, cooldown, cancellation, and safety settings.
 */
public class TeleportConfig extends ModuleConfig {

    private int warmupSeconds = 3;
    private int cooldownSeconds = 5;
    private boolean cancelOnMove = true;
    private boolean cancelOnDamage = true;
    private boolean allowCrossWorld = true;
    private boolean safeTeleport = true;
    private int safeRadius = 3;

    public TeleportConfig(@NotNull Path filePath) {
        super(filePath);
    }

    @Override
    @NotNull
    public String getModuleName() {
        return "teleport";
    }

    @Override
    protected void createDefaults() {
        // All defaults are set via field initializers
    }

    @Override
    protected void loadModuleSettings(@NotNull JsonObject root) {
        warmupSeconds = getInt(root, "warmupSeconds", warmupSeconds);
        cooldownSeconds = getInt(root, "cooldownSeconds", cooldownSeconds);
        cancelOnMove = getBool(root, "cancelOnMove", cancelOnMove);
        cancelOnDamage = getBool(root, "cancelOnDamage", cancelOnDamage);
        allowCrossWorld = getBool(root, "allowCrossWorld", allowCrossWorld);
        safeTeleport = getBool(root, "safeTeleport", safeTeleport);
        safeRadius = getInt(root, "safeRadius", safeRadius);
    }

    @Override
    protected void writeModuleSettings(@NotNull JsonObject root) {
        root.addProperty("warmupSeconds", warmupSeconds);
        root.addProperty("cooldownSeconds", cooldownSeconds);
        root.addProperty("cancelOnMove", cancelOnMove);
        root.addProperty("cancelOnDamage", cancelOnDamage);
        root.addProperty("allowCrossWorld", allowCrossWorld);
        root.addProperty("safeTeleport", safeTeleport);
        root.addProperty("safeRadius", safeRadius);
    }

    @Override
    @NotNull
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();
        warmupSeconds = validateRange(result, "warmupSeconds", warmupSeconds, 0, 60, 3);
        cooldownSeconds = validateRange(result, "cooldownSeconds", cooldownSeconds, 0, 300, 5);
        safeRadius = validateRange(result, "safeRadius", safeRadius, 1, 10, 3);
        return result;
    }

    // === Getters ===

    public int getWarmupSeconds() { return warmupSeconds; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public boolean isCancelOnMove() { return cancelOnMove; }
    public boolean isCancelOnDamage() { return cancelOnDamage; }
    public boolean isAllowCrossWorld() { return allowCrossWorld; }
    public boolean isSafeTeleport() { return safeTeleport; }
    public int getSafeRadius() { return safeRadius; }

    // === Setters (for live config editing) ===

    public void setWarmupSeconds(int warmupSeconds) {
        this.warmupSeconds = Math.max(0, Math.min(60, warmupSeconds));
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, Math.min(300, cooldownSeconds));
    }

    public void setCancelOnMove(boolean cancelOnMove) {
        this.cancelOnMove = cancelOnMove;
    }

    public void setCancelOnDamage(boolean cancelOnDamage) {
        this.cancelOnDamage = cancelOnDamage;
    }
}
