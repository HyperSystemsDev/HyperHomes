package com.hyperhomes.manager;

import com.hyperhomes.config.HyperHomesConfig;
import com.hyperhomes.integration.HyperPermsIntegration;
import com.hyperhomes.model.Home;
import com.hyperhomes.model.PlayerHomes;
import com.hyperhomes.storage.StorageProvider;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player homes - loading, saving, and CRUD operations.
 */
public class HomeManager {

    private final StorageProvider storage;
    private final Map<UUID, PlayerHomes> cache;

    /**
     * Creates a new HomeManager.
     *
     * @param storage the storage provider
     */
    public HomeManager(@NotNull StorageProvider storage) {
        this.storage = storage;
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Loads player homes into cache.
     *
     * @param uuid     the player's UUID
     * @param username the player's username
     * @return a future containing the player homes
     */
    public CompletableFuture<PlayerHomes> loadPlayer(@NotNull UUID uuid, @NotNull String username) {
        return storage.loadPlayerHomes(uuid).thenApply(opt -> {
            PlayerHomes homes = opt.orElseGet(() -> new PlayerHomes(uuid, username));
            homes.setUsername(username); // Update username in case it changed
            cache.put(uuid, homes);
            Logger.debug("Loaded %d homes for %s", homes.getHomeCount(), username);
            return homes;
        });
    }

    /**
     * Saves player homes to storage.
     *
     * @param uuid the player's UUID
     * @return a future that completes when saving is done
     */
    public CompletableFuture<Void> savePlayer(@NotNull UUID uuid) {
        PlayerHomes homes = cache.get(uuid);
        if (homes == null) {
            return CompletableFuture.completedFuture(null);
        }
        return storage.savePlayerHomes(homes);
    }

    /**
     * Unloads a player from cache, saving first.
     *
     * @param uuid the player's UUID
     * @return a future that completes when unloaded
     */
    public CompletableFuture<Void> unloadPlayer(@NotNull UUID uuid) {
        return savePlayer(uuid).thenRun(() -> {
            cache.remove(uuid);
            Logger.debug("Unloaded player %s from cache", uuid);
        });
    }

    /**
     * Gets cached player homes.
     *
     * @param uuid the player's UUID
     * @return the player homes, or null if not cached
     */
    @Nullable
    public PlayerHomes getPlayerHomes(@NotNull UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Sets a home for a player.
     *
     * @param uuid the player's UUID
     * @param home the home to set
     * @return true if successful, false if limit reached
     */
    public boolean setHome(@NotNull UUID uuid, @NotNull Home home) {
        PlayerHomes playerHomes = cache.get(uuid);
        if (playerHomes == null) {
            Logger.warn("Cannot set home for uncached player %s", uuid);
            return false;
        }

        // Check if updating existing home
        if (!playerHomes.hasHome(home.name())) {
            // Check home limit
            int limit = getHomeLimit(uuid);
            if (limit >= 0 && playerHomes.getHomeCount() >= limit) {
                return false;
            }
        }

        playerHomes.setHome(home);
        savePlayer(uuid); // Async save
        return true;
    }

    /**
     * Gets a home for a player.
     *
     * @param uuid     the player's UUID
     * @param homeName the home name
     * @return the home, or null if not found
     */
    @Nullable
    public Home getHome(@NotNull UUID uuid, @NotNull String homeName) {
        PlayerHomes playerHomes = cache.get(uuid);
        if (playerHomes == null) {
            return null;
        }
        return playerHomes.getHome(homeName);
    }

    /**
     * Gets all homes for a player.
     *
     * @param uuid the player's UUID
     * @return collection of homes, or empty if not cached
     */
    @NotNull
    public Collection<Home> getHomes(@NotNull UUID uuid) {
        PlayerHomes playerHomes = cache.get(uuid);
        if (playerHomes == null) {
            return Collections.emptyList();
        }
        return playerHomes.getHomes();
    }

    /**
     * Sets a home, bypassing the home limit.
     * Used for special homes like bed sync.
     *
     * @param playerUuid the player's UUID
     * @param home       the home to set
     */
    public void setHomeBypassing(@NotNull UUID playerUuid, @NotNull Home home) {
        PlayerHomes playerHomes = cache.get(playerUuid);
        if (playerHomes == null) {
            return;
        }

        playerHomes.setHome(home);
        savePlayer(playerUuid);
        Logger.debug("Home '%s' set (bypassing limit) for %s", home.name(), playerUuid);
    }

    /**
     * Deletes a home for a player.
     *
     * @param uuid     the player's UUID
     * @param homeName the home name
     * @return true if deleted
     */
    public boolean deleteHome(@NotNull UUID uuid, @NotNull String homeName) {
        PlayerHomes playerHomes = cache.get(uuid);
        if (playerHomes == null) {
            return false;
        }

        boolean deleted = playerHomes.removeHome(homeName);
        if (deleted) {
            savePlayer(uuid); // Async save
        }
        return deleted;
    }

    /**
     * Gets the home limit for a player.
     *
     * @param uuid the player's UUID
     * @return the home limit, or -1 for unlimited
     */
    public int getHomeLimit(@NotNull UUID uuid) {
        // Check for unlimited permission
        if (HyperPermsIntegration.hasPermission(uuid, "hyperhomes.unlimited")) {
            return -1;
        }

        // Check for specific limit permission
        int limit = HyperPermsIntegration.getPermissionValue(uuid, "hyperhomes.limit.", -1);
        if (limit >= 0) {
            return limit;
        }

        // Fall back to config default
        return HyperHomesConfig.get().getDefaultHomeLimit();
    }

    /**
     * Checks if a player can teleport (cooldown check).
     *
     * @param uuid the player's UUID
     * @return remaining cooldown in milliseconds, or 0 if can teleport
     */
    public long getRemainingCooldown(@NotNull UUID uuid) {
        // Check for cooldown bypass
        if (HyperPermsIntegration.hasPermission(uuid, "hyperhomes.bypass.cooldown")) {
            return 0;
        }

        PlayerHomes playerHomes = cache.get(uuid);
        if (playerHomes == null) {
            return 0;
        }

        long lastTeleport = playerHomes.getLastTeleport();
        if (lastTeleport == 0) {
            return 0;
        }

        long cooldownMs = HyperHomesConfig.get().getCooldownSeconds() * 1000L;
        long elapsed = System.currentTimeMillis() - lastTeleport;
        return Math.max(0, cooldownMs - elapsed);
    }

    /**
     * Records that a player teleported.
     *
     * @param uuid the player's UUID
     */
    public void recordTeleport(@NotNull UUID uuid) {
        PlayerHomes playerHomes = cache.get(uuid);
        if (playerHomes != null) {
            playerHomes.setLastTeleport(System.currentTimeMillis());
            savePlayer(uuid);
        }
    }

    /**
     * Gets the number of cached players.
     *
     * @return the cache size
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Gets the total number of homes across all cached players.
     *
     * @return the total home count
     */
    public int getTotalHomesCount() {
        int total = 0;
        for (PlayerHomes homes : cache.values()) {
            total += homes.getHomeCount();
        }
        return total;
    }

    /**
     * Saves all cached player homes.
     *
     * @return a future that completes when all saves are done
     */
    public CompletableFuture<Void> saveAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (UUID uuid : cache.keySet()) {
            futures.add(savePlayer(uuid));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    // ========== HOME SHARING ==========

    /**
     * Shares a home with another player.
     *
     * @param ownerUuid  the home owner's UUID
     * @param homeName   the home name
     * @param targetUuid the player to share with
     * @return true if successfully shared
     */
    public boolean shareHome(@NotNull UUID ownerUuid, @NotNull String homeName, @NotNull UUID targetUuid) {
        Logger.info("[SHARE-DEBUG] shareHome(owner=%s, home='%s', target=%s)", ownerUuid, homeName, targetUuid);

        PlayerHomes ownerHomes = cache.get(ownerUuid);
        if (ownerHomes == null) {
            Logger.info("[SHARE-DEBUG]   Owner not in cache!");
            return false;
        }

        Home home = ownerHomes.getHome(homeName);
        if (home == null) {
            Logger.info("[SHARE-DEBUG]   Home not found!");
            return false;
        }

        // Can't share with yourself
        if (ownerUuid.equals(targetUuid)) {
            Logger.info("[SHARE-DEBUG]   Can't share with yourself!");
            return false;
        }

        Logger.info("[SHARE-DEBUG]   Before: sharedWith=%s", home.sharedWith());

        // Add the target to the share list
        Home updatedHome = home.withSharedPlayer(targetUuid);
        ownerHomes.setHome(updatedHome);

        Logger.info("[SHARE-DEBUG]   After: sharedWith=%s", updatedHome.sharedWith());

        // Verify the update
        Home verifyHome = ownerHomes.getHome(homeName);
        Logger.info("[SHARE-DEBUG]   Verify: sharedWith=%s", verifyHome != null ? verifyHome.sharedWith() : "null");

        savePlayer(ownerUuid);

        Logger.info("[SHARE-DEBUG] Share successful!");
        return true;
    }

    /**
     * Unshares a home from another player.
     *
     * @param ownerUuid  the home owner's UUID
     * @param homeName   the home name
     * @param targetUuid the player to unshare from
     * @return true if successfully unshared
     */
    public boolean unshareHome(@NotNull UUID ownerUuid, @NotNull String homeName, @NotNull UUID targetUuid) {
        PlayerHomes ownerHomes = cache.get(ownerUuid);
        if (ownerHomes == null) {
            return false;
        }

        Home home = ownerHomes.getHome(homeName);
        if (home == null) {
            return false;
        }

        if (!home.isSharedWith(targetUuid)) {
            return false;
        }

        // Remove the target from the share list
        Home updatedHome = home.withoutSharedPlayer(targetUuid);
        ownerHomes.setHome(updatedHome);
        savePlayer(ownerUuid);

        Logger.debug("Player %s unshared home '%s' from %s", ownerUuid, homeName, targetUuid);
        return true;
    }

    /**
     * Gets a shared home from another player.
     *
     * @param requesterUuid the player requesting access
     * @param ownerUuid     the home owner's UUID
     * @param homeName      the home name
     * @return the home if shared, null otherwise
     */
    @Nullable
    public Home getSharedHome(@NotNull UUID requesterUuid, @NotNull UUID ownerUuid, @NotNull String homeName) {
        Logger.info("[SHARE-DEBUG] getSharedHome(requester=%s, owner=%s, home='%s')", requesterUuid, ownerUuid, homeName);

        PlayerHomes ownerHomes = cache.get(ownerUuid);
        if (ownerHomes == null) {
            Logger.info("[SHARE-DEBUG]   Owner not in cache!");
            return null;
        }
        Logger.info("[SHARE-DEBUG]   Owner found in cache: %s", ownerHomes.getUsername());

        Home home = ownerHomes.getHome(homeName);
        if (home == null) {
            Logger.info("[SHARE-DEBUG]   Home '%s' not found! Available homes: %s", homeName, ownerHomes.getHomeNames());
            return null;
        }
        Logger.info("[SHARE-DEBUG]   Home found: %s, sharedWith: %s", home.name(), home.sharedWith());

        // Check if the requester has access via sharing or admin bypass
        boolean isShared = home.isSharedWith(requesterUuid);
        boolean hasAdminBypass = HyperPermsIntegration.hasPermission(requesterUuid, "hyperhomes.admin.teleport.others");
        boolean hasAccess = isShared || hasAdminBypass;

        Logger.info("[SHARE-DEBUG]   isSharedWith=%s, hasAdminBypass=%s, hasAccess=%s", isShared, hasAdminBypass, hasAccess);

        if (!hasAccess) {
            Logger.info("[SHARE-DEBUG]   ACCESS DENIED");
            return null;
        }

        Logger.info("[SHARE-DEBUG]   ACCESS GRANTED");
        return home;
    }

    /**
     * Gets all homes shared with a player.
     *
     * @param playerUuid the player's UUID
     * @return map of owner UUID to list of shared homes
     */
    @NotNull
    public Map<UUID, List<Home>> getHomesSharedWithPlayer(@NotNull UUID playerUuid) {
        Map<UUID, List<Home>> sharedHomes = new HashMap<>();

        for (PlayerHomes ownerHomes : cache.values()) {
            // Skip self
            if (ownerHomes.getUuid().equals(playerUuid)) {
                continue;
            }

            List<Home> shared = new ArrayList<>();
            for (Home home : ownerHomes.getHomes()) {
                if (home.isSharedWith(playerUuid)) {
                    shared.add(home);
                }
            }

            if (!shared.isEmpty()) {
                sharedHomes.put(ownerHomes.getUuid(), shared);
            }
        }

        return sharedHomes;
    }

    /**
     * Gets the username for a cached player.
     *
     * @param uuid the player's UUID
     * @return the username, or null if not cached
     */
    @Nullable
    public String getUsername(@NotNull UUID uuid) {
        PlayerHomes homes = cache.get(uuid);
        return homes != null ? homes.getUsername() : null;
    }

    /**
     * Finds a player UUID by username (case-insensitive).
     *
     * @param username the username to search for
     * @return the UUID, or null if not found
     */
    @Nullable
    public UUID findPlayerByUsername(@NotNull String username) {
        String lowerUsername = username.toLowerCase();
        Logger.info("[SHARE-DEBUG] findPlayerByUsername('%s') - cache size: %d", username, cache.size());
        for (PlayerHomes homes : cache.values()) {
            Logger.info("[SHARE-DEBUG]   Checking cached player: %s (UUID: %s)", homes.getUsername(), homes.getUuid());
            if (homes.getUsername().toLowerCase().equals(lowerUsername)) {
                Logger.info("[SHARE-DEBUG]   MATCH FOUND!");
                return homes.getUuid();
            }
        }
        Logger.info("[SHARE-DEBUG]   No match found for '%s'", username);
        return null;
    }
}
