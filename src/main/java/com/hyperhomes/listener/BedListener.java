package com.hyperhomes.listener;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.config.ConfigManager;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.data.Home;
import com.hyperhomes.util.Logger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Listener for bed interactions to auto-create "bed" home.
 *
 * When a player sets their spawn at a bed, this listener automatically
 * creates or updates a home called "bed" at that location.
 *
 * Note: This needs to be connected to the appropriate Hytale event
 * for bed spawn setting when that event is identified.
 */
public class BedListener {

    // Colors for messages
    private static final String COLOR_GOLD = "#FFAA00";
    private static final String COLOR_GREEN = "#55FF55";
    private static final String COLOR_GRAY = "#AAAAAA";
    private static final String COLOR_YELLOW = "#FFFF55";

    private final HyperHomes plugin;

    public BedListener(@NotNull HyperHomes plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a player sets their spawn at a bed.
     * Creates or updates the "bed" home automatically.
     *
     * @param playerRef the player
     * @param worldName the world name
     * @param x         bed x coordinate
     * @param y         bed y coordinate
     * @param z         bed z coordinate
     * @param yaw       player yaw when setting spawn
     * @param pitch     player pitch when setting spawn
     */
    public void onBedSpawnSet(@NotNull PlayerRef playerRef,
                              @NotNull String worldName,
                              double x, double y, double z,
                              float yaw, float pitch) {

        ConfigManager config = ConfigManager.get();

        // Check if bed sync is enabled
        if (!config.isBedSyncEnabled()) {
            return;
        }

        UUID uuid = playerRef.getUuid();
        String homeName = config.getBedSyncHomeName();

        HomeManager homeManager = plugin.getHomeManager();

        // Create the bed home
        Home bedHome = Home.create(homeName, worldName, x, y, z, yaw, pitch);

        // Check if updating or creating
        boolean isUpdate = homeManager.getHome(uuid, homeName) != null;

        // Set the home (this bypasses the home limit check for bed homes)
        homeManager.setHomeBypassing(uuid, bedHome);

        // Send confirmation message
        if (isUpdate) {
            playerRef.sendMessage(prefix()
                .insert(Message.raw("Bed home updated!").color(COLOR_GREEN)));
        } else {
            playerRef.sendMessage(prefix()
                .insert(Message.raw("Bed home created!").color(COLOR_GREEN)));
            playerRef.sendMessage(Message.raw("  Use ").color(COLOR_GRAY)
                .insert(Message.raw("/home " + homeName).color(COLOR_YELLOW))
                .insert(Message.raw(" to return here.").color(COLOR_GRAY)));
        }

        Logger.debug("Bed home set for %s at %s: %.1f, %.1f, %.1f",
            playerRef.getUsername(), worldName, x, y, z);
    }

    /**
     * Alternative method for when we only have block position (no rotation).
     * Uses default rotation values.
     */
    public void onBedSpawnSet(@NotNull PlayerRef playerRef,
                              @NotNull String worldName,
                              double x, double y, double z) {
        onBedSpawnSet(playerRef, worldName, x, y, z, 0, 0);
    }

    private Message prefix() {
        return Message.raw("[").color(COLOR_GRAY)
            .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
            .insert(Message.raw("] ").color(COLOR_GRAY));
    }
}
