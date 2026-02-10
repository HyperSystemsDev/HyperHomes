package com.hyperhomes.data;

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

    @NotNull
    public UUID getUuid() {
        return uuid;
    }

    @NotNull
    public String getUsername() {
        return username;
    }

    public void setUsername(@NotNull String username) {
        this.username = username;
    }

    public int getHomeCount() {
        return homes.size();
    }

    @Nullable
    public Home getHome(@NotNull String name) {
        return homes.get(name.toLowerCase());
    }

    @NotNull
    public Collection<Home> getHomes() {
        return Collections.unmodifiableCollection(homes.values());
    }

    @NotNull
    public Set<String> getHomeNames() {
        return Collections.unmodifiableSet(homes.keySet());
    }

    public void setHome(@NotNull Home home) {
        homes.put(home.name().toLowerCase(), home);
    }

    public boolean removeHome(@NotNull String name) {
        return homes.remove(name.toLowerCase()) != null;
    }

    public boolean hasHome(@NotNull String name) {
        return homes.containsKey(name.toLowerCase());
    }

    public long getLastTeleport() {
        return lastTeleport;
    }

    public void setLastTeleport(long timestamp) {
        this.lastTeleport = timestamp;
    }

    @NotNull
    public Map<String, Home> getHomesMap() {
        return new HashMap<>(homes);
    }
}
