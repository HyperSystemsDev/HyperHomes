package com.hyperhomes.gui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which GUI pages players currently have open.
 * Used for targeted refresh/cleanup when data changes.
 */
public class ActivePageTracker {

    private final Map<UUID, GuiType> activePlayers = new ConcurrentHashMap<>();

    /**
     * Registers that a player has opened a GUI page.
     *
     * @param playerUuid the player's UUID
     * @param guiType    the type of GUI opened
     */
    public void setActivePage(@NotNull UUID playerUuid, @NotNull GuiType guiType) {
        activePlayers.put(playerUuid, guiType);
    }

    /**
     * Removes a player's active page tracking (e.g., when page is closed).
     *
     * @param playerUuid the player's UUID
     */
    public void clearActivePage(@NotNull UUID playerUuid) {
        activePlayers.remove(playerUuid);
    }

    /**
     * Gets the player's currently active GUI page type.
     *
     * @param playerUuid the player's UUID
     * @return the active GuiType, or null if no page is open
     */
    @Nullable
    public GuiType getActivePage(@NotNull UUID playerUuid) {
        return activePlayers.get(playerUuid);
    }

    /**
     * Checks if a player has a specific page open.
     *
     * @param playerUuid the player's UUID
     * @param guiType    the GUI type to check
     * @return true if the player has that page open
     */
    public boolean hasPageOpen(@NotNull UUID playerUuid, @NotNull GuiType guiType) {
        return guiType.equals(activePlayers.get(playerUuid));
    }

    /**
     * Checks if any player has a specific page type open.
     *
     * @param guiType the GUI type to check
     * @return true if at least one player has this page open
     */
    public boolean isAnyPlayerOnPage(@NotNull GuiType guiType) {
        return activePlayers.containsValue(guiType);
    }

    /**
     * Clears all tracking data. Called on plugin shutdown.
     */
    public void clearAll() {
        activePlayers.clear();
    }

    /**
     * Gets the number of players with active pages.
     *
     * @return the count
     */
    public int getActiveCount() {
        return activePlayers.size();
    }
}
