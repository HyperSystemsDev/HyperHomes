package com.hyperhomes.command.util;

import com.hyperhomes.config.ConfigManager;
import com.hyperhomes.integration.PermissionManager;
import com.hypixel.hytale.server.core.Message;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Unified messaging utilities for HyperHomes commands.
 * <p>
 * Replaces duplicated prefix() methods and color constants
 * scattered across all command classes.
 */
public final class CommandUtil {

    // Standard colors (matching HyperFactions/HyperHomes brand)
    public static final String COLOR_GOLD = "#FFAA00";
    public static final String COLOR_GREEN = "#55FF55";
    public static final String COLOR_RED = "#FF5555";
    public static final String COLOR_YELLOW = "#FFFF55";
    public static final String COLOR_GRAY = "#AAAAAA";
    public static final String COLOR_WHITE = "#FFFFFF";
    public static final String COLOR_AQUA = "#55FFFF";
    public static final String COLOR_DARK_GRAY = "#555555";

    private CommandUtil() {}

    /**
     * Creates the configurable prefix message.
     * Format: [PrefixText]_ (with configurable text and colors)
     *
     * @return the prefix message
     */
    @NotNull
    public static Message prefix() {
        ConfigManager config = ConfigManager.get();
        String bracketColor = config.getPrefixBracketColor();
        String textColor = config.getPrefixColor();
        String text = config.getPrefixText();

        return Message.raw("[").color(bracketColor)
            .insert(Message.raw(text).color(textColor))
            .insert(Message.raw("] ").color(bracketColor));
    }

    /**
     * Creates a message with the prefix and text.
     *
     * @param text  the message text
     * @param color the text color
     * @return the formatted message
     */
    @NotNull
    public static Message msg(@NotNull String text, @NotNull String color) {
        return prefix().insert(Message.raw(text).color(color));
    }

    /**
     * Creates an error message (red text with prefix).
     *
     * @param text the error text
     * @return the formatted error message
     */
    @NotNull
    public static Message error(@NotNull String text) {
        return msg(text, COLOR_RED);
    }

    /**
     * Creates a success message (green text with prefix).
     *
     * @param text the success text
     * @return the formatted success message
     */
    @NotNull
    public static Message success(@NotNull String text) {
        return msg(text, COLOR_GREEN);
    }

    /**
     * Creates an info message (yellow text with prefix).
     *
     * @param text the info text
     * @return the formatted info message
     */
    @NotNull
    public static Message info(@NotNull String text) {
        return msg(text, COLOR_YELLOW);
    }

    /**
     * Checks if a player has a permission via PermissionManager.
     *
     * @param playerUuid the player's UUID
     * @param permission the permission to check
     * @return true if the player has the permission
     */
    public static boolean hasPermission(@NotNull UUID playerUuid, @NotNull String permission) {
        return PermissionManager.get().hasPermission(playerUuid, permission);
    }

    /**
     * Gets a numeric permission value (e.g., hyperhomes.limit.5 → 5).
     *
     * @param playerUuid   the player's UUID
     * @param prefix       the permission prefix
     * @param defaultValue the default value
     * @return the highest numeric value found, or defaultValue
     */
    public static int getPermissionValue(@NotNull UUID playerUuid, @NotNull String prefix, int defaultValue) {
        return PermissionManager.get().getPermissionValue(playerUuid, prefix, defaultValue);
    }

    /**
     * Formats milliseconds to human-readable time.
     *
     * @param ms time in milliseconds
     * @return formatted string (e.g., "5 seconds", "2m 30s")
     */
    @NotNull
    public static String formatTime(long ms) {
        long seconds = (ms + 999) / 1000; // Round up
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (seconds == 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        return minutes + "m " + seconds + "s";
    }
}
