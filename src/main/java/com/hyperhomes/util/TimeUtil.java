package com.hyperhomes.util;

import org.jetbrains.annotations.NotNull;

/**
 * Utility class for time formatting.
 */
public final class TimeUtil {

    private TimeUtil() {}

    /**
     * Formats milliseconds into a human-readable string.
     *
     * @param millis the milliseconds
     * @return formatted string (e.g., "5s", "2m 30s", "1h 5m")
     */
    @NotNull
    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return "0s";
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds %= 60;
        minutes %= 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    /**
     * Formats seconds into a human-readable string.
     *
     * @param seconds the seconds
     * @return formatted string
     */
    @NotNull
    public static String formatSeconds(int seconds) {
        return formatDuration(seconds * 1000L);
    }
}
