package com.hyperhomes.gui;

import com.hyperhomes.data.Home;
import com.hypixel.hytale.server.core.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper utilities for UI formatting and styling.
 */
public final class UIHelper {

    // Legacy color code to hex color mapping
    private static final Map<Character, String> COLOR_MAP = new HashMap<>();
    static {
        COLOR_MAP.put('0', "#000000"); // Black
        COLOR_MAP.put('1', "#0000AA"); // Dark Blue
        COLOR_MAP.put('2', "#00AA00"); // Dark Green
        COLOR_MAP.put('3', "#00AAAA"); // Dark Aqua
        COLOR_MAP.put('4', "#AA0000"); // Dark Red
        COLOR_MAP.put('5', "#AA00AA"); // Dark Purple
        COLOR_MAP.put('6', "#FFAA00"); // Gold
        COLOR_MAP.put('7', "#AAAAAA"); // Gray
        COLOR_MAP.put('8', "#555555"); // Dark Gray
        COLOR_MAP.put('9', "#5555FF"); // Blue
        COLOR_MAP.put('a', "#55FF55"); // Green
        COLOR_MAP.put('b', "#55FFFF"); // Aqua
        COLOR_MAP.put('c', "#FF5555"); // Red
        COLOR_MAP.put('d', "#FF55FF"); // Light Purple
        COLOR_MAP.put('e', "#FFFF55"); // Yellow
        COLOR_MAP.put('f', "#FFFFFF"); // White
        COLOR_MAP.put('r', "#FFFFFF"); // Reset (white)
    }

    // Brand colors
    public static final String COLOR_GOLD = "#FFAA00";
    public static final String COLOR_GOLD_LIGHT = "#FFD700";

    // Status colors
    public static final String COLOR_SUCCESS = "#4aff7f";
    public static final String COLOR_DANGER = "#ff4a4a";
    public static final String COLOR_PRIMARY = "#4a9eff";
    public static final String COLOR_WARNING = "#FFFF55";

    // Text colors
    public static final String COLOR_TEXT_PRIMARY = "#ffffff";
    public static final String COLOR_TEXT_SECONDARY = "#96a9be";
    public static final String COLOR_TEXT_MUTED = "#556677";

    // Background colors
    public static final String COLOR_BG_DARK = "#0a1119";
    public static final String COLOR_BG_LIGHT = "#141c26";

    private UIHelper() {
        // Utility class
    }

    /**
     * Formats coordinates to a readable string.
     *
     * @param home The home
     * @return Formatted coordinates (e.g., "123, 64, -456")
     */
    public static String formatCoords(Home home) {
        return String.format("%.0f, %.0f, %.0f", home.x(), home.y(), home.z());
    }

    /**
     * Formats coordinates with more precision.
     *
     * @param home The home
     * @return Formatted coordinates (e.g., "123.5, 64.0, -456.2")
     */
    public static String formatCoordsDetailed(Home home) {
        return String.format("%.1f, %.1f, %.1f", home.x(), home.y(), home.z());
    }

    /**
     * Formats a number with thousands separators.
     *
     * @param value The number
     * @return Formatted string (e.g., "1,234")
     */
    public static String formatNumber(long value) {
        return String.format("%,d", value);
    }

    /**
     * Formats a time duration in seconds to a human-readable string.
     *
     * @param seconds The duration in seconds
     * @return Formatted string (e.g., "4m 30s" or "30s")
     */
    public static String formatDuration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        int minutes = seconds / 60;
        int secs = seconds % 60;
        if (secs == 0) {
            return minutes + "m";
        }
        return minutes + "m " + secs + "s";
    }

    /**
     * Formats a time duration in milliseconds.
     *
     * @param millis The duration in milliseconds
     * @return Formatted string
     */
    public static String formatDurationMs(long millis) {
        return formatDuration((int) (millis / 1000));
    }

    /**
     * Truncates a string to a maximum length with ellipsis.
     *
     * @param text      The text
     * @param maxLength Maximum length
     * @return Truncated text or original if short enough
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Formats a world name for display.
     * Converts snake_case to Title Case.
     *
     * @param worldName The world name
     * @return Formatted display name
     */
    public static String formatWorldName(String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            return "Unknown";
        }

        // Replace underscores with spaces
        String result = worldName.replace('_', ' ');

        // Capitalize first letter of each word
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : result.toCharArray()) {
            if (c == ' ') {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }

        return sb.toString();
    }

    /**
     * Formats a relative time (how long ago something happened).
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted string (e.g., "5 minutes ago", "2 days ago")
     */
    public static String formatRelativeTime(long timestamp) {
        if (timestamp == 0) {
            return "Never";
        }

        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + (days == 1 ? " day ago" : " days ago");
        } else if (hours > 0) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (minutes > 0) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        } else {
            return "Just now";
        }
    }

    /**
     * Gets the home limit display string.
     *
     * @param limit The home limit (-1 for unlimited)
     * @return Display string (e.g., "5" or "∞")
     */
    public static String formatLimit(int limit) {
        return limit < 0 ? "∞" : String.valueOf(limit);
    }

    /**
     * Parses legacy color codes (§X) into a properly formatted Message.
     *
     * @param text Text with §X color codes
     * @return Formatted Message object
     */
    public static Message parseColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }

        Message result = null;
        StringBuilder currentSegment = new StringBuilder();
        String currentColor = "#FFFFFF";

        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);

            // Check for color code (§X or \u00A7X)
            if (c == '§' || c == '\u00A7') {
                // Flush current segment
                if (currentSegment.length() > 0) {
                    Message segment = Message.raw(currentSegment.toString()).color(currentColor);
                    result = (result == null) ? segment : result.insert(segment);
                    currentSegment = new StringBuilder();
                }

                // Get the color code
                if (i + 1 < text.length()) {
                    char colorCode = Character.toLowerCase(text.charAt(i + 1));
                    String newColor = COLOR_MAP.get(colorCode);
                    if (newColor != null) {
                        currentColor = newColor;
                    }
                    i += 2; // Skip both § and the color code
                    continue;
                }
            }

            currentSegment.append(c);
            i++;
        }

        // Flush remaining segment
        if (currentSegment.length() > 0) {
            Message segment = Message.raw(currentSegment.toString()).color(currentColor);
            result = (result == null) ? segment : result.insert(segment);
        }

        return result != null ? result : Message.raw("");
    }
}
