package com.hyperhomes.config;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Abstract base class for module configuration files.
 * <p>
 * Module configs live in the config/ subdirectory and have an "enabled" field
 * that can completely disable the module's functionality.
 */
public abstract class ModuleConfig extends ConfigFile {

    protected boolean enabled = true;

    protected ModuleConfig(@NotNull Path filePath) {
        super(filePath);
    }

    @NotNull
    public abstract String getModuleName();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected boolean getDefaultEnabled() {
        return true;
    }

    @Override
    protected void loadFromJson(@NotNull JsonObject root) {
        this.enabled = getBool(root, "enabled", getDefaultEnabled());
        loadModuleSettings(root);
    }

    @Override
    @NotNull
    protected JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        writeModuleSettings(root);
        return root;
    }

    protected abstract void loadModuleSettings(@NotNull JsonObject root);

    protected abstract void writeModuleSettings(@NotNull JsonObject root);
}
