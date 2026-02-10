package com.hyperhomes.integration;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface for permission providers.
 * Allows HyperHomes to integrate with multiple permission systems
 * (HyperPerms, etc.) with a unified API.
 * <p>
 * Simplified from HyperFactions' version — no prefix/suffix/primaryGroup
 * methods since HyperHomes doesn't need chat metadata.
 */
public interface PermissionProvider {

    /**
     * Gets the name of this permission provider.
     *
     * @return the provider name (e.g., "HyperPerms")
     */
    @NotNull
    String getName();

    /**
     * Checks if this provider is available and initialized.
     *
     * @return true if the provider is available and can handle permission checks
     */
    boolean isAvailable();

    /**
     * Checks if a player has a specific permission.
     *
     * @param playerUuid the player's UUID
     * @param permission the permission node to check
     * @return Optional containing true/false if the provider can answer,
     *         or empty if the provider cannot determine (e.g., player not found)
     */
    @NotNull
    Optional<Boolean> hasPermission(@NotNull UUID playerUuid, @NotNull String permission);

    /**
     * Gets a numeric permission value from a permission pattern.
     * For example, "hyperhomes.limit.5" with prefix "hyperhomes.limit." returns 5.
     *
     * @param playerUuid   the player's UUID
     * @param prefix       the permission prefix
     * @param defaultValue the default value if not found
     * @return the numeric value, or defaultValue if not found
     */
    int getPermissionValue(@NotNull UUID playerUuid, @NotNull String prefix, int defaultValue);

    /**
     * Gets the player's primary group name.
     *
     * @param playerUuid the player's UUID
     * @return the primary group name, or "default" if not found
     */
    @NotNull
    String getPrimaryGroup(@NotNull UUID playerUuid);
}
