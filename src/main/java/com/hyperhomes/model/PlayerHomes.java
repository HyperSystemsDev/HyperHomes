package com.hyperhomes.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents all homes belonging to a player.
 */
public class PlayerHomes {

    private final UUID uuid;
    private String username;
    private final Map<String, Home> homes;
    private long lastTeleport;

    /**
     * Creates a new PlayerHomes instance.
     *
     * @param uuid     the player's UUID
     * @param username the player's username
     */
    public PlayerHomes(@NotNull UUID uuid, @NotNull String username) {
        this.uuid = uuid;
        this.username = username;
        this.homes = new HashMap<>();
        this.lastTeleport = 0;
    }

    /**
     * Creates a PlayerHomes instance with existing data.
     *
     * @param uuid         the player's UUID
     * @param username     the player's username
     * @param homes        map of home name to Home
     * @param lastTeleport last teleport timestamp
     */
    public PlayerHomes(@NotNull UUID uuid, @NotNull String username,
                       @NotNull Map<String, Home> homes, long lastTeleport) {
        this.uuid = uuid;
        this.username = username;
        this.homes = new HashMap<>(homes);
        this.lastTeleport = lastTeleport;
    }

    /**
     * Gets the player's UUID.
     *
     * @return the UUID
     */
    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the player's username.
     *
     * @return the username
     */
    @NotNull
    public String getUsername() {
        return username;
    }

    /**
     * Sets the player's username.
     *
     * @param username the new username
     */
    public void setUsername(@NotNull String username) {
        this.username = username;
    }

    /**
     * Gets the number of homes the player has.
     *
     * @return the home count
     */
    public int getHomeCount() {
        return homes.size();
    }

    /**
     * Gets a home by name.
     *
     * @param name the home name (case-insensitive)
     * @return the home, or null if not found
     */
    @Nullable
    public Home getHome(@NotNull String name) {
        return homes.get(name.toLowerCase());
    }

    /**
     * Gets all homes for this player.
     *
     * @return unmodifiable collection of homes
     */
    @NotNull
    public Collection<Home> getHomes() {
        return Collections.unmodifiableCollection(homes.values());
    }

    /**
     * Gets all home names.
     *
     * @return unmodifiable set of home names
     */
    @NotNull
    public Set<String> getHomeNames() {
        return Collections.unmodifiableSet(homes.keySet());
    }

    /**
     * Adds or updates a home.
     *
     * @param home the home to add
     */
    public void setHome(@NotNull Home home) {
        homes.put(home.name().toLowerCase(), home);
    }

    /**
     * Removes a home.
     *
     * @param name the home name (case-insensitive)
     * @return true if the home was removed
     */
    public boolean removeHome(@NotNull String name) {
        return homes.remove(name.toLowerCase()) != null;
    }

    /**
     * Checks if a home exists.
     *
     * @param name the home name (case-insensitive)
     * @return true if the home exists
     */
    public boolean hasHome(@NotNull String name) {
        return homes.containsKey(name.toLowerCase());
    }

    /**
     * Gets the last teleport timestamp.
     *
     * @return epoch milliseconds of last teleport
     */
    public long getLastTeleport() {
        return lastTeleport;
    }

    /**
     * Sets the last teleport timestamp.
     *
     * @param timestamp the timestamp
     */
    public void setLastTeleport(long timestamp) {
        this.lastTeleport = timestamp;
    }

    /**
     * Gets the homes map for serialization.
     *
     * @return the homes map
     */
    @NotNull
    public Map<String, Home> getHomesMap() {
        return new HashMap<>(homes);
    }
}
