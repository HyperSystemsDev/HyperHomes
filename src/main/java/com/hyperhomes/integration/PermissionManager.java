package com.hyperhomes.integration;

import com.hyperhomes.Permissions;
import com.hyperhomes.config.ConfigManager;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Unified permission manager with chain-of-responsibility pattern.
 * Tries providers in order: HyperPerms -> OP fallback.
 * <p>
 * Simplified from HyperFactions' version (2 providers instead of 4,
 * no chat prefix/suffix methods).
 * <p>
 * Fallback behavior when no provider can answer:
 * - Admin permissions (hyperhomes.admin.*): Require OP
 * - Bypass permissions: Always deny (requires explicit grant)
 * - Limit permissions: Always deny (config defaults used)
 * - Normal permissions: Allow by default (configurable)
 */
public class PermissionManager {

    private static final PermissionManager INSTANCE = new PermissionManager();

    private final List<PermissionProvider> providers = new ArrayList<>();
    private boolean initialized = false;

    private PermissionManager() {}

    /**
     * Gets the singleton instance.
     *
     * @return the PermissionManager instance
     */
    public static PermissionManager get() {
        return INSTANCE;
    }

    /**
     * Initializes all permission providers.
     * Should be called once during plugin startup.
     */
    public void init() {
        if (initialized) {
            Logger.warn("[PermissionManager] Already initialized, skipping");
            return;
        }

        providers.clear();

        // HyperPerms provider
        HyperPermsProviderAdapter hyperPermsProvider = new HyperPermsProviderAdapter();
        hyperPermsProvider.init();
        if (hyperPermsProvider.isAvailable()) {
            providers.add(hyperPermsProvider);
        }

        initialized = true;

        if (providers.isEmpty()) {
            Logger.info("[PermissionManager] No permission providers found - using fallback mode");
        } else {
            Logger.info("[PermissionManager] Initialized with %d provider(s): %s",
                providers.size(), getProviderNames());
        }
    }

    /**
     * Checks if a player has a permission.
     * <p>
     * Chain behavior:
     * 1. Try each provider in order for the specific permission
     * 2. If any provider returns true/false, use that result
     * 3. Check the category wildcard (e.g., hyperhomes.bypass.* for hyperhomes.bypass.warmup)
     * 4. Check hyperhomes.* wildcard
     * 5. If all providers return empty (undefined), use fallback
     *
     * @param playerUuid the player's UUID
     * @param permission the permission to check
     * @return true if the player has the permission
     */
    public boolean hasPermission(@NotNull UUID playerUuid, @NotNull String permission) {
        boolean isUserLevel = isUserLevelPermission(permission);

        // Try each provider in order for the specific permission
        for (PermissionProvider provider : providers) {
            Optional<Boolean> result = provider.hasPermission(playerUuid, permission);
            if (result.isPresent()) {
                if (result.get()) {
                    Logger.debug("[PermissionManager] %s answered %s for %s: true",
                        provider.getName(), permission, playerUuid);
                    return true;
                } else {
                    if (!isUserLevel) {
                        Logger.debug("[PermissionManager] %s answered %s for %s: false (denied)",
                            provider.getName(), permission, playerUuid);
                        return false;
                    }
                    Logger.debug("[PermissionManager] %s returned false for %s, checking wildcards",
                        provider.getName(), permission);
                    break;
                }
            }
        }

        // Check category wildcard (e.g., hyperhomes.bypass.* for hyperhomes.bypass.warmup)
        String categoryWildcard = getCategoryWildcard(permission);
        if (categoryWildcard != null) {
            for (PermissionProvider provider : providers) {
                Optional<Boolean> result = provider.hasPermission(playerUuid, categoryWildcard);
                if (result.isPresent() && result.get()) {
                    Logger.debug("[PermissionManager] %s granted via category wildcard %s for %s",
                        permission, categoryWildcard, playerUuid);
                    return true;
                }
            }
        }

        // Check root wildcard (hyperhomes.*)
        for (PermissionProvider provider : providers) {
            Optional<Boolean> result = provider.hasPermission(playerUuid, Permissions.WILDCARD);
            if (result.isPresent() && result.get()) {
                Logger.debug("[PermissionManager] %s granted via %s for %s",
                    permission, Permissions.WILDCARD, playerUuid);
                return true;
            }
        }

        // Fallback behavior
        return handleFallback(playerUuid, permission);
    }

    /**
     * Gets a numeric permission value (e.g., hyperhomes.limit.5 → 5).
     *
     * @param playerUuid   the player's UUID
     * @param prefix       the permission prefix
     * @param defaultValue the default value
     * @return the highest numeric value found, or defaultValue
     */
    public int getPermissionValue(@NotNull UUID playerUuid, @NotNull String prefix, int defaultValue) {
        for (PermissionProvider provider : providers) {
            int value = provider.getPermissionValue(playerUuid, prefix, Integer.MIN_VALUE);
            if (value != Integer.MIN_VALUE) {
                return value;
            }
        }
        return defaultValue;
    }

    /**
     * Gets the player's primary group from the first available provider.
     *
     * @param playerUuid the player's UUID
     * @return the primary group name, or "default"
     */
    @NotNull
    public String getPrimaryGroup(@NotNull UUID playerUuid) {
        for (PermissionProvider provider : providers) {
            String group = provider.getPrimaryGroup(playerUuid);
            if (group != null && !group.isEmpty() && !"default".equals(group)) {
                return group;
            }
        }
        return "default";
    }

    /**
     * Gets the category wildcard for a permission.
     * E.g., hyperhomes.bypass.warmup → hyperhomes.bypass.*
     */
    private String getCategoryWildcard(@NotNull String permission) {
        if (!permission.startsWith(Permissions.ROOT + ".")) {
            return null;
        }

        int lastDot = permission.lastIndexOf('.');
        if (lastDot <= Permissions.ROOT.length()) {
            return null;
        }

        return permission.substring(0, lastDot) + ".*";
    }

    /**
     * Checks if a permission is a user-level permission (not admin, bypass, or limit).
     */
    private boolean isUserLevelPermission(@NotNull String permission) {
        if (permission.startsWith("hyperhomes.admin")) return false;
        if (permission.startsWith("hyperhomes.bypass")) return false;
        if (permission.startsWith("hyperhomes.limit")) return false;
        if (permission.equals(Permissions.UNLIMITED)) return false;
        return permission.startsWith("hyperhomes.");
    }

    /**
     * Handles fallback when no provider can answer.
     */
    private boolean handleFallback(@NotNull UUID playerUuid, @NotNull String permission) {
        ConfigManager config = ConfigManager.get();

        // Admin permissions always require OP
        if (permission.startsWith("hyperhomes.admin")) {
            boolean isOp = isPlayerOp(playerUuid);
            Logger.debug("[PermissionManager] Admin fallback for %s: isOp=%s", playerUuid, isOp);
            return isOp;
        }

        // Bypass permissions should always be denied unless explicitly granted
        if (permission.startsWith("hyperhomes.bypass")) {
            Logger.debug("[PermissionManager] Bypass fallback for %s: denied (requires explicit grant)", playerUuid);
            return false;
        }

        // Limit permissions should be denied (defaults are used instead)
        if (permission.startsWith("hyperhomes.limit") || permission.equals(Permissions.UNLIMITED)) {
            Logger.debug("[PermissionManager] Limit fallback for %s: denied (uses config defaults)", playerUuid);
            return false;
        }

        // Normal user permissions: allow if configured (no permission mod installed)
        boolean allow = config.isAllowWithoutPermissionMod();
        Logger.debug("[PermissionManager] Normal fallback for %s: %s (allowWithoutPermissionMod: %s)",
            playerUuid, allow ? "allow" : "deny", allow);
        return allow;
    }

    /**
     * Checks if a player is OP using Hytale's PermissionsModule.
     */
    private boolean isPlayerOp(@NotNull UUID playerUuid) {
        try {
            Class<?> permModuleClass = Class.forName("com.hypixel.hytale.server.core.permissions.PermissionsModule");
            java.lang.reflect.Method getMethod = permModuleClass.getMethod("get");
            Object permModule = getMethod.invoke(null);
            if (permModule == null) return false;

            // Check if player is in the "OP" group
            java.lang.reflect.Method getGroupsMethod = permModuleClass.getMethod("getGroupsForUser", UUID.class);
            Object groupsObj = getGroupsMethod.invoke(permModule, playerUuid);
            if (groupsObj instanceof java.util.Set<?> groups) {
                if (groups.contains("OP")) {
                    return true;
                }
            }

            // Alternative: check if player has "*" permission (OP wildcard)
            java.lang.reflect.Method hasPermMethod = permModuleClass.getMethod("hasPermission", UUID.class, String.class);
            Object result = hasPermMethod.invoke(permModule, playerUuid, "*");
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (ClassNotFoundException e) {
            Logger.debug("[PermissionManager] PermissionsModule not found");
        } catch (Exception e) {
            Logger.debug("[PermissionManager] Error checking OP status: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Gets the list of active provider names.
     */
    @NotNull
    public String getProviderNames() {
        if (providers.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < providers.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(providers.get(i).getName());
        }
        return sb.toString();
    }

    /**
     * Gets the number of active providers.
     */
    public int getProviderCount() {
        return providers.size();
    }

    /**
     * Checks if any permission provider is available.
     */
    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    /**
     * Gets detailed status information for debugging.
     */
    @NotNull
    public String getDetailedStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Permission Manager Status ===\n");
        sb.append("Initialized: ").append(initialized).append("\n");
        sb.append("Active Providers: ").append(providers.size()).append("\n");
        for (PermissionProvider provider : providers) {
            sb.append("  - ").append(provider.getName())
                    .append(" (available: ").append(provider.isAvailable()).append(")\n");
        }
        sb.append("Allow Without Permission Mod: ").append(ConfigManager.get().isAllowWithoutPermissionMod()).append("\n");
        sb.append("Admin Requires OP: always (OP group)\n");
        return sb.toString();
    }
}
