package com.hyperhomes.api;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.data.Home;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Public API for HyperHomes.
 * Other plugins can use this to interact with HyperHomes.
 */
public final class HyperHomesAPI {

    private static HyperHomes instance;

    private HyperHomesAPI() {}

    /**
     * Sets the HyperHomes instance (internal use only).
     *
     * @param hyperHomes the instance
     */
    public static void setInstance(@Nullable HyperHomes hyperHomes) {
        instance = hyperHomes;
    }

    /**
     * Checks if HyperHomes API is available.
     *
     * @return true if available
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    /**
     * Gets a player's home by name.
     *
     * @param playerUuid the player's UUID
     * @param homeName   the home name
     * @return the home, or null if not found
     */
    @Nullable
    public static Home getHome(@NotNull UUID playerUuid, @NotNull String homeName) {
        if (instance == null) return null;
        return instance.getHomeManager().getHome(playerUuid, homeName);
    }

    /**
     * Gets all homes for a player.
     *
     * @param playerUuid the player's UUID
     * @return collection of homes
     */
    @NotNull
    public static Collection<Home> getHomes(@NotNull UUID playerUuid) {
        if (instance == null) return java.util.Collections.emptyList();
        return instance.getHomeManager().getHomes(playerUuid);
    }

    /**
     * Gets the number of homes a player has.
     *
     * @param playerUuid the player's UUID
     * @return the home count
     */
    public static int getHomeCount(@NotNull UUID playerUuid) {
        if (instance == null) return 0;
        var homes = instance.getHomeManager().getPlayerHomes(playerUuid);
        return homes != null ? homes.getHomeCount() : 0;
    }

    /**
     * Gets the home limit for a player.
     *
     * @param playerUuid the player's UUID
     * @return the limit, or -1 for unlimited
     */
    public static int getHomeLimit(@NotNull UUID playerUuid) {
        if (instance == null) return -1;
        return instance.getHomeManager().getHomeLimit(playerUuid);
    }

    /**
     * Checks if a player has a specific home.
     *
     * @param playerUuid the player's UUID
     * @param homeName   the home name
     * @return true if the home exists
     */
    public static boolean hasHome(@NotNull UUID playerUuid, @NotNull String homeName) {
        return getHome(playerUuid, homeName) != null;
    }
}
