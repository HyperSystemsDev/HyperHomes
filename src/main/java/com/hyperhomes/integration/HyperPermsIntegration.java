package com.hyperhomes.integration;

import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Integration with HyperPerms for permission checking.
 * Uses reflection to avoid hard dependency on HyperPerms.
 *
 * IMPORTANT: When HyperPerms is not available OR permission check fails,
 * we default to ALLOWING access. This ensures the plugin works standalone
 * and OPs/admins aren't blocked by integration issues.
 */
public final class HyperPermsIntegration {

    private static boolean available = false;
    private static Object hyperPermsInstance = null;
    private static Method hasPermissionMethod = null;
    private static Method getUserManagerMethod = null;
    private static String initError = null;

    private HyperPermsIntegration() {}

    /**
     * Initializes the HyperPerms integration.
     */
    public static void init() {
        try {
            // Try to load HyperPerms via HyperPermsBootstrap
            Class<?> bootstrapClass = Class.forName("com.hyperperms.HyperPermsBootstrap");
            Method getInstanceMethod = bootstrapClass.getMethod("getInstance");
            hyperPermsInstance = getInstanceMethod.invoke(null);

            if (hyperPermsInstance == null) {
                initError = "HyperPermsBootstrap.getInstance() returned null";
                available = false;
                Logger.warn("HyperPerms bootstrap returned null - permissions disabled");
                return;
            }

            Class<?> instanceClass = hyperPermsInstance.getClass();
            Logger.info("HyperPerms instance class: %s", instanceClass.getName());

            // Get the hasPermission method on HyperPerms itself (not User)
            // Method signature: hasPermission(UUID uuid, String permission)
            hasPermissionMethod = instanceClass.getMethod("hasPermission", UUID.class, String.class);
            getUserManagerMethod = instanceClass.getMethod("getUserManager");

            available = true;
            Logger.info("HyperPerms integration enabled successfully");

        } catch (ClassNotFoundException e) {
            available = false;
            initError = "HyperPerms not found";
            Logger.info("HyperPerms not found - all players will have full access");
        } catch (NoSuchMethodException e) {
            available = false;
            initError = "Method not found: " + e.getMessage();
            Logger.warn("HyperPerms API mismatch: %s - defaulting to allow all", e.getMessage());
        } catch (Exception e) {
            available = false;
            initError = e.getClass().getSimpleName() + ": " + e.getMessage();
            Logger.warn("Failed to initialize HyperPerms integration: %s - defaulting to allow all", e.getMessage());
        }
    }

    /**
     * Checks if HyperPerms is available.
     *
     * @return true if available
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Gets initialization error message if any.
     *
     * @return error message or null
     */
    public static String getInitError() {
        return initError;
    }

    /**
     * Gets detailed status of the integration for debugging.
     *
     * @return status string with all relevant info
     */
    public static String getDetailedStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HyperPerms Integration Status ===\n");
        sb.append("Available: ").append(available).append("\n");
        sb.append("Instance: ").append(hyperPermsInstance != null ? hyperPermsInstance.getClass().getName() : "null").append("\n");
        sb.append("hasPermission method: ").append(hasPermissionMethod != null ? "found" : "null").append("\n");
        sb.append("getUserManager method: ").append(getUserManagerMethod != null ? "found" : "null").append("\n");
        if (initError != null) {
            sb.append("Init error: ").append(initError).append("\n");
        }
        return sb.toString();
    }

    /**
     * Tests permission check and returns detailed result for debugging.
     *
     * @param playerUuid the player's UUID
     * @param permission the permission to test
     * @return detailed test result
     */
    public static String testPermission(@NotNull UUID playerUuid, @NotNull String permission) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Permission Test ===\n");
        sb.append("Player UUID: ").append(playerUuid).append("\n");
        sb.append("Permission: ").append(permission).append("\n");
        sb.append("Integration available: ").append(available).append("\n");

        if (!available || hyperPermsInstance == null || hasPermissionMethod == null) {
            sb.append("Result: ALLOWED (integration not available, fail-open)\n");
            sb.append("Reason: ");
            if (!available) sb.append("available=false ");
            if (hyperPermsInstance == null) sb.append("instance=null ");
            if (hasPermissionMethod == null) sb.append("method=null ");
            return sb.toString();
        }

        try {
            Logger.info("[PERM-TEST] Invoking hasPermission(%s, %s) on %s",
                playerUuid, permission, hyperPermsInstance.getClass().getName());

            Object result = hasPermissionMethod.invoke(hyperPermsInstance, playerUuid, permission);
            sb.append("Raw result: ").append(result).append(" (").append(result != null ? result.getClass().getName() : "null").append(")\n");

            if (result instanceof Boolean) {
                boolean hasPerm = (Boolean) result;
                sb.append("Final result: ").append(hasPerm ? "ALLOWED" : "DENIED").append("\n");
            } else {
                sb.append("Final result: ALLOWED (unexpected result type, fail-open)\n");
            }
        } catch (Exception e) {
            sb.append("Exception: ").append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
            sb.append("Final result: ALLOWED (exception, fail-open)\n");
        }

        return sb.toString();
    }

    /**
     * Checks if a player has a permission.
     *
     * IMPORTANT: Returns TRUE if:
     * - HyperPerms is not available (standalone mode)
     * - Permission check fails for any reason (fail-open for safety)
     * - Player actually has the permission
     *
     * @param playerUuid the player's UUID
     * @param permission the permission to check
     * @return true if has permission or check cannot be performed
     */
    public static boolean hasPermission(@NotNull UUID playerUuid, @NotNull String permission) {
        Logger.info("[PERM-DEBUG] === HyperHomes permission check START ===");
        Logger.info("[PERM-DEBUG] Checking permission '%s' for player %s", permission, playerUuid);

        // If HyperPerms not available, allow by default
        if (!available || hyperPermsInstance == null || hasPermissionMethod == null) {
            Logger.info("[PERM] HyperPerms not available (available=%s, instance=%s, method=%s), ALLOWING %s for %s",
                available, hyperPermsInstance != null, hasPermissionMethod != null, permission, playerUuid);
            return true;
        }

        Logger.info("[PERM-DEBUG] HyperPerms instance: %s", hyperPermsInstance.getClass().getName());
        Logger.info("[PERM-DEBUG] Method: %s", hasPermissionMethod);

        try {
            // Call hasPermission(UUID, String) on the HyperPerms instance directly
            Logger.info("[PERM-DEBUG] Invoking HyperPerms.hasPermission(%s, %s)", playerUuid, permission);
            Object result = hasPermissionMethod.invoke(hyperPermsInstance, playerUuid, permission);

            Logger.info("[PERM-DEBUG] Raw result from HyperPerms: %s (type: %s)",
                result, result != null ? result.getClass().getName() : "null");

            if (result instanceof Boolean) {
                boolean hasPerm = (Boolean) result;
                Logger.info("[PERM] Check %s for %s = %s", permission, playerUuid, hasPerm);
                Logger.info("[PERM-DEBUG] === HyperHomes permission check END (result: %s) ===", hasPerm);
                return hasPerm;
            }

            // Unexpected result type, allow by default
            Logger.warn("[PERM] Unexpected result type: %s, ALLOWING %s for %s",
                result != null ? result.getClass().getName() : "null", permission, playerUuid);
            return true;

        } catch (Exception e) {
            // Any error in permission check = allow (fail-open)
            Logger.warn("[PERM] Exception checking %s for %s: %s, ALLOWING",
                permission, playerUuid, e.getMessage());
            Logger.warn("[PERM-DEBUG] Full exception:", e);
            return true;
        }
    }

    /**
     * Gets a numeric permission value from a permission pattern.
     * For example, "hyperhomes.limit.5" with prefix "hyperhomes.limit." returns 5.
     *
     * @param playerUuid   the player's UUID
     * @param prefix       the permission prefix
     * @param defaultValue the default value if not found
     * @return the numeric value, or defaultValue if not found
     */
    public static int getPermissionValue(@NotNull UUID playerUuid, @NotNull String prefix, int defaultValue) {
        if (!available || hyperPermsInstance == null || getUserManagerMethod == null) {
            return defaultValue;
        }

        try {
            // Get UserManager
            Object userManager = getUserManagerMethod.invoke(hyperPermsInstance);
            if (userManager == null) {
                return defaultValue;
            }

            // Get User
            Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, playerUuid);
            if (user == null) {
                return defaultValue;
            }

            // Try to get effective permissions
            Method getPermissionsMethod = user.getClass().getMethod("getEffectivePermissions");
            Object permissions = getPermissionsMethod.invoke(user);

            if (permissions instanceof Iterable<?> iterable) {
                int highestValue = defaultValue;
                for (Object perm : iterable) {
                    String permStr = perm.toString();
                    if (permStr.startsWith(prefix)) {
                        String valueStr = permStr.substring(prefix.length());
                        try {
                            int value = Integer.parseInt(valueStr);
                            if (value > highestValue) {
                                highestValue = value;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a numeric permission
                        }
                    }
                }
                return highestValue;
            }
        } catch (Exception e) {
            Logger.debug("Failed to get permission value for %s with prefix %s: %s",
                playerUuid, prefix, e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Gets the player's primary group name.
     *
     * @param playerUuid the player's UUID
     * @return the group name, or "default" if not found
     */
    @NotNull
    public static String getPrimaryGroup(@NotNull UUID playerUuid) {
        if (!available || hyperPermsInstance == null || getUserManagerMethod == null) {
            return "default";
        }

        try {
            // Get UserManager
            Object userManager = getUserManagerMethod.invoke(hyperPermsInstance);
            if (userManager == null) {
                return "default";
            }

            // Get User
            Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, playerUuid);
            if (user == null) {
                return "default";
            }

            // Get primary group
            Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
            Object result = getPrimaryGroupMethod.invoke(user);
            return result != null ? result.toString() : "default";
        } catch (Exception e) {
            Logger.debug("Failed to get primary group for %s: %s", playerUuid, e.getMessage());
            return "default";
        }
    }
}
