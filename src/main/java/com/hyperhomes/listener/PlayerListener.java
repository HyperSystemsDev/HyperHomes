package com.hyperhomes.listener;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.manager.TeleportManager;
import com.hyperhomes.data.Location;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Listener for player movement and damage events.
 * Used to cancel pending teleports.
 */
public class PlayerListener {

    private final HyperHomes plugin;

    public PlayerListener(@NotNull HyperHomes plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks player movement to potentially cancel warmup.
     * Call this from the plugin's movement tracking.
     *
     * @param playerRef the player
     * @param player    the player entity
     */
    public void checkMovement(@NotNull PlayerRef playerRef, @NotNull Player player) {
        UUID uuid = playerRef.getUuid();
        TeleportManager teleportManager = plugin.getTeleportManager();

        if (!teleportManager.hasPending(uuid)) {
            return;
        }

        // Get current location
        var world = player.getWorld();
        if (world == null) return;

        // Get transform component from the holder
        var holder = playerRef.getHolder();
        if (holder == null) return;

        var transform = holder.getComponent(TransformComponent.getComponentType());
        if (transform == null) return;

        var pos = transform.getPosition();

        Location currentLocation = new Location(
            world.getName(),
            pos.getX(), pos.getY(), pos.getZ(),
            0, 0 // We don't care about rotation for movement check
        );

        teleportManager.checkMovement(
            uuid,
            currentLocation,
            plugin::cancelTask,
            msg -> playerRef.sendMessage(Message.raw(msg))
        );
    }

    /**
     * Cancels teleport on player damage.
     * Call this from the plugin's damage event handling.
     *
     * @param playerRef the player who was damaged
     */
    public void onPlayerDamage(@NotNull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        TeleportManager teleportManager = plugin.getTeleportManager();

        if (!teleportManager.hasPending(uuid)) {
            return;
        }

        teleportManager.cancelOnDamage(
            uuid,
            plugin::cancelTask,
            msg -> playerRef.sendMessage(Message.raw(msg))
        );
    }
}
