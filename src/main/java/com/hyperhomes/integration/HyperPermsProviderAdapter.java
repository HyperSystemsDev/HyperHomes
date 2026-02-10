package com.hyperhomes.integration;

import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter wrapping HyperPerms as a PermissionProvider.
 * Uses reflection to avoid hard dependency on HyperPerms.
 */
public class HyperPermsProviderAdapter implements PermissionProvider {

    private boolean available = false;
    private Object hyperPermsInstance = null;
    private Method hasPermissionMethod = null;
    private Method getUserManagerMethod = null;

    @Override
    @NotNull
    public String getName() {
        return "HyperPerms";
    }

    /**
     * Initializes the HyperPerms provider via reflection.
     */
    public void init() {
        try {
            Class<?> bootstrapClass = Class.forName("com.hyperperms.HyperPermsBootstrap");
            Method getInstanceMethod = bootstrapClass.getMethod("getInstance");
            hyperPermsInstance = getInstanceMethod.invoke(null);

            if (hyperPermsInstance == null) {
                available = false;
                Logger.debug("[HyperPermsProvider] Bootstrap returned null");
                return;
            }

            Class<?> instanceClass = hyperPermsInstance.getClass();
            hasPermissionMethod = instanceClass.getMethod("hasPermission", UUID.class, String.class);
            getUserManagerMethod = instanceClass.getMethod("getUserManager");

            available = true;
            Logger.info("[PermissionManager] HyperPerms provider initialized");

        } catch (ClassNotFoundException e) {
            available = false;
            Logger.debug("[HyperPermsProvider] HyperPerms not found");
        } catch (Exception e) {
            available = false;
            Logger.debug("[HyperPermsProvider] Failed to initialize: %s", e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    @NotNull
    public Optional<Boolean> hasPermission(@NotNull UUID playerUuid, @NotNull String permission) {
        if (!available || hyperPermsInstance == null || hasPermissionMethod == null) {
            return Optional.empty();
        }

        try {
            Object result = hasPermissionMethod.invoke(hyperPermsInstance, playerUuid, permission);

            if (result instanceof Boolean) {
                return Optional.of((Boolean) result);
            }

            Logger.debug("[HyperPermsProvider] Unexpected result type: %s",
                result != null ? result.getClass().getName() : "null");
            return Optional.empty();

        } catch (Exception e) {
            Logger.debug("[HyperPermsProvider] Exception checking permission: %s", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getPermissionValue(@NotNull UUID playerUuid, @NotNull String prefix, int defaultValue) {
        if (!available || hyperPermsInstance == null || getUserManagerMethod == null) {
            return defaultValue;
        }

        try {
            Object userManager = getUserManagerMethod.invoke(hyperPermsInstance);
            if (userManager == null) return defaultValue;

            Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, playerUuid);
            if (user == null) return defaultValue;

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
                        } catch (NumberFormatException ignored) {}
                    }
                }
                return highestValue;
            }
        } catch (Exception e) {
            Logger.debug("[HyperPermsProvider] Failed to get permission value for %s with prefix %s: %s",
                playerUuid, prefix, e.getMessage());
        }

        return defaultValue;
    }

    @Override
    @NotNull
    public String getPrimaryGroup(@NotNull UUID playerUuid) {
        if (!available || hyperPermsInstance == null || getUserManagerMethod == null) {
            return "default";
        }

        try {
            Object userManager = getUserManagerMethod.invoke(hyperPermsInstance);
            if (userManager == null) return "default";

            Method getUserMethod = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUserMethod.invoke(userManager, playerUuid);
            if (user == null) return "default";

            Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
            Object result = getPrimaryGroupMethod.invoke(user);
            return result != null ? result.toString() : "default";

        } catch (Exception e) {
            Logger.debug("[HyperPermsProvider] Failed to get primary group: %s", e.getMessage());
            return "default";
        }
    }
}
