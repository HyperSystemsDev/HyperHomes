package com.hyperhomes.integration;

import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reflection-based soft dependency on HyperFactions.
 * Provides territory and faction information without a hard compile-time dependency.
 */
public final class HyperFactionsIntegration {

    private static boolean available = false;
    private static boolean initialized = false;

    // HyperFactionsAPI reflection handles
    private static Method isAvailableMethod;
    private static Method getClaimManagerMethod;
    private static Method getFactionManagerMethod;
    private static Method getRelationManagerMethod;

    // ClaimManager reflection handles
    private static Method getClaimOwnerAtMethod;

    // FactionManager reflection handles
    private static Method getFactionMethod;
    private static Method getPlayerFactionIdMethod;

    // RelationManager reflection handles
    private static Method getRelationMethod;

    // Faction reflection handles (for getName())
    private static Method factionNameMethod;

    // RelationType reflection handles
    private static Method relationTypeNameMethod;

    private HyperFactionsIntegration() {}

    /**
     * Initializes the HyperFactions integration.
     * Safe to call multiple times; only initializes once.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        try {
            Class<?> apiClass = Class.forName("com.hyperfactions.api.HyperFactionsAPI");

            isAvailableMethod = apiClass.getMethod("isAvailable");
            boolean apiAvailable = (boolean) isAvailableMethod.invoke(null);

            if (!apiAvailable) {
                Logger.info("[Integration] HyperFactions detected but not yet available");
                return;
            }

            getClaimManagerMethod = apiClass.getMethod("getClaimManager");
            getFactionManagerMethod = apiClass.getMethod("getFactionManager");
            getRelationManagerMethod = apiClass.getMethod("getRelationManager");

            // Cache method handles for managers
            Object claimManager = getClaimManagerMethod.invoke(null);
            if (claimManager != null) {
                getClaimOwnerAtMethod = claimManager.getClass()
                        .getMethod("getClaimOwnerAt", String.class, double.class, double.class);
            }

            Object factionManager = getFactionManagerMethod.invoke(null);
            if (factionManager != null) {
                getFactionMethod = factionManager.getClass()
                        .getMethod("getFaction", UUID.class);
                getPlayerFactionIdMethod = factionManager.getClass()
                        .getMethod("getPlayerFactionId", UUID.class);
            }

            Object relationManager = getRelationManagerMethod.invoke(null);
            if (relationManager != null) {
                getRelationMethod = relationManager.getClass()
                        .getMethod("getRelation", UUID.class, UUID.class);
            }

            // Cache Faction.name() and RelationType.name()
            Class<?> factionClass = Class.forName("com.hyperfactions.data.Faction");
            factionNameMethod = factionClass.getMethod("name");

            Class<?> relationTypeClass = Class.forName("com.hyperfactions.data.RelationType");
            relationTypeNameMethod = relationTypeClass.getMethod("name");

            available = true;
            Logger.info("[Integration] HyperFactions integration initialized successfully");

        } catch (ClassNotFoundException e) {
            Logger.info("[Integration] HyperFactions not found - territory features disabled");
        } catch (Exception e) {
            Logger.warn("[Integration] Failed to initialize HyperFactions integration: %s", e.getMessage());
        }
    }

    /**
     * Returns whether HyperFactions is available and initialized.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Gets the faction name that owns the territory at the given location.
     *
     * @param world the world name
     * @param x     x coordinate
     * @param z     z coordinate
     * @return the faction name, or null if unclaimed or HyperFactions unavailable
     */
    @Nullable
    public static String getFactionAtLocation(@NotNull String world, double x, double z) {
        if (!available) return null;

        try {
            Object claimManager = getClaimManagerMethod.invoke(null);
            if (claimManager == null) return null;

            UUID factionId = (UUID) getClaimOwnerAtMethod.invoke(claimManager, world, x, z);
            if (factionId == null) return null;

            Object factionManager = getFactionManagerMethod.invoke(null);
            if (factionManager == null) return null;

            Object faction = getFactionMethod.invoke(factionManager, factionId);
            if (faction == null) return null;

            return (String) factionNameMethod.invoke(faction);

        } catch (Exception e) {
            Logger.debug("[Integration] Error getting faction at location: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the faction UUID that owns the territory at the given location.
     *
     * @param world the world name
     * @param x     x coordinate
     * @param z     z coordinate
     * @return the faction UUID, or null if unclaimed
     */
    @Nullable
    public static UUID getFactionIdAtLocation(@NotNull String world, double x, double z) {
        if (!available) return null;

        try {
            Object claimManager = getClaimManagerMethod.invoke(null);
            if (claimManager == null) return null;

            return (UUID) getClaimOwnerAtMethod.invoke(claimManager, world, x, z);

        } catch (Exception e) {
            Logger.debug("[Integration] Error getting faction ID at location: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Gets the relation between a player and the faction owning territory at a location.
     *
     * @param playerUuid the player's UUID
     * @param world      the world name
     * @param x          x coordinate
     * @param z          z coordinate
     * @return relation name ("OWN", "ALLY", "NEUTRAL", "ENEMY"), or null if unclaimed or player not in a faction
     */
    @Nullable
    public static String getRelationAtLocation(@NotNull UUID playerUuid,
                                                @NotNull String world, double x, double z) {
        if (!available) return null;

        try {
            // Get faction at location
            Object claimManager = getClaimManagerMethod.invoke(null);
            if (claimManager == null) return null;

            UUID territoryFactionId = (UUID) getClaimOwnerAtMethod.invoke(claimManager, world, x, z);
            if (territoryFactionId == null) return null;

            // Get player's faction
            Object factionManager = getFactionManagerMethod.invoke(null);
            if (factionManager == null) return null;

            UUID playerFactionId = (UUID) getPlayerFactionIdMethod.invoke(factionManager, playerUuid);
            if (playerFactionId == null) return null;

            // Same faction = OWN
            if (playerFactionId.equals(territoryFactionId)) {
                return "OWN";
            }

            // Get relation between factions
            Object relationManager = getRelationManagerMethod.invoke(null);
            if (relationManager == null) return null;

            Object relationType = getRelationMethod.invoke(relationManager, playerFactionId, territoryFactionId);
            if (relationType == null) return "NEUTRAL";

            return (String) relationTypeNameMethod.invoke(relationType);

        } catch (Exception e) {
            Logger.debug("[Integration] Error getting relation at location: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a player can set a home at a location, considering faction territory restrictions.
     * <p>
     * Respects the config setting {@code restrictHomesInEnemyTerritory}. If enabled,
     * prevents setting homes in enemy territory.
     *
     * @param playerUuid the player's UUID
     * @param world      the world name
     * @param x          x coordinate
     * @param z          z coordinate
     * @return true if the player can set a home here
     */
    public static boolean canSetHomeAtLocation(@NotNull UUID playerUuid,
                                                @NotNull String world, double x, double z) {
        if (!available) return true; // Fail-open

        String relation = getRelationAtLocation(playerUuid, world, x, z);
        if (relation == null) return true; // Unclaimed or player not in faction

        return !"ENEMY".equals(relation);
    }

    /**
     * Gets a display label for the territory at a location.
     *
     * @param world the world name
     * @param x     x coordinate
     * @param z     z coordinate
     * @return territory label like "Wilderness" or faction name
     */
    @NotNull
    public static String getTerritoryLabel(@NotNull String world, double x, double z) {
        String factionName = getFactionAtLocation(world, x, z);
        return factionName != null ? factionName : "Wilderness";
    }
}
