package com.hyperhomes.model;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a home location that a player can teleport to.
 *
 * @param name          the unique name of the home
 * @param world         the world the home is in
 * @param x             the x coordinate
 * @param y             the y coordinate
 * @param z             the z coordinate
 * @param yaw           the yaw rotation
 * @param pitch         the pitch rotation
 * @param createdAt     when the home was created (epoch milliseconds)
 * @param lastUsed      when the home was last teleported to (epoch milliseconds)
 * @param sharedWith    set of player UUIDs this home is shared with
 */
public record Home(
    @NotNull String name,
    @NotNull String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    long createdAt,
    long lastUsed,
    @NotNull Set<UUID> sharedWith
) {
    /**
     * Compact constructor to ensure sharedWith is never null.
     */
    public Home {
        if (sharedWith == null) {
            sharedWith = Collections.emptySet();
        } else {
            sharedWith = Collections.unmodifiableSet(new HashSet<>(sharedWith));
        }
    }

    /**
     * Creates a new home with default timestamps and no sharing.
     *
     * @param name  the home name
     * @param world the world name
     * @param x     x coordinate
     * @param y     y coordinate
     * @param z     z coordinate
     * @param yaw   yaw rotation
     * @param pitch pitch rotation
     * @return a new Home instance
     */
    public static Home create(@NotNull String name, @NotNull String world,
                              double x, double y, double z, float yaw, float pitch) {
        long now = System.currentTimeMillis();
        return new Home(name, world, x, y, z, yaw, pitch, now, now, Collections.emptySet());
    }

    /**
     * Creates a copy of this home with an updated lastUsed timestamp.
     *
     * @param timestamp the new lastUsed timestamp
     * @return a new Home with updated lastUsed
     */
    public Home withLastUsed(long timestamp) {
        return new Home(name, world, x, y, z, yaw, pitch, createdAt, timestamp, sharedWith);
    }

    /**
     * Creates a copy of this home with a new name.
     *
     * @param newName the new name
     * @return a new Home with the updated name
     */
    public Home withName(@NotNull String newName) {
        return new Home(newName, world, x, y, z, yaw, pitch, createdAt, lastUsed, sharedWith);
    }

    /**
     * Creates a copy of this home with a player added to the share list.
     *
     * @param playerUuid the player to share with
     * @return a new Home with the player added
     */
    public Home withSharedPlayer(@NotNull UUID playerUuid) {
        Set<UUID> newShared = new HashSet<>(sharedWith);
        newShared.add(playerUuid);
        return new Home(name, world, x, y, z, yaw, pitch, createdAt, lastUsed, newShared);
    }

    /**
     * Creates a copy of this home with a player removed from the share list.
     *
     * @param playerUuid the player to unshare
     * @return a new Home with the player removed
     */
    public Home withoutSharedPlayer(@NotNull UUID playerUuid) {
        Set<UUID> newShared = new HashSet<>(sharedWith);
        newShared.remove(playerUuid);
        return new Home(name, world, x, y, z, yaw, pitch, createdAt, lastUsed, newShared);
    }

    /**
     * Checks if this home is shared with a specific player.
     *
     * @param playerUuid the player to check
     * @return true if shared with the player
     */
    public boolean isSharedWith(@NotNull UUID playerUuid) {
        return sharedWith.contains(playerUuid);
    }

    /**
     * Checks if this home is shared with anyone.
     *
     * @return true if shared with at least one player
     */
    public boolean isShared() {
        return !sharedWith.isEmpty();
    }
}
