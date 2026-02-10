package com.hyperhomes;

/**
 * Centralized permission node definitions for HyperHomes.
 *
 * Follows the Hytale permission best practices format:
 * {@code <namespace>.<category>.<action>}
 *
 * Permission hierarchy:
 * - hyperhomes.* - All permissions (wildcard)
 * - hyperhomes.use - Basic plugin access
 * - hyperhomes.home.* - Home management
 * - hyperhomes.share.* - Sharing
 * - hyperhomes.bypass.* - Bypass restrictions
 * - hyperhomes.admin.* - Administration
 * - hyperhomes.limit.* - Numeric limits
 *
 * IMPORTANT: All existing permission strings are preserved for backwards
 * compatibility. HyperHomes is a public plugin and changing permission
 * strings would break existing server configurations.
 */
public final class Permissions {

    private Permissions() {}

    // === Root ===
    public static final String ROOT = "hyperhomes";
    public static final String WILDCARD = "hyperhomes.*";

    // === Basic Access ===
    /** Basic plugin access - required for /home command */
    public static final String USE = "hyperhomes.use";
    /** Open the homes GUI */
    public static final String GUI = "hyperhomes.gui";

    // === Home Management ===
    /** Teleport to a home (/home) */
    public static final String HOME = "hyperhomes.use";
    /** Set a home (/sethome) */
    public static final String SET = "hyperhomes.set";
    /** Delete a home (/delhome) */
    public static final String DELETE = "hyperhomes.delete";
    /** List homes (/homes) */
    public static final String LIST = "hyperhomes.list";

    // === Sharing (hyperhomes.share.*) ===
    public static final String SHARE_WILDCARD = "hyperhomes.share.*";
    /** Share a home with another player */
    public static final String SHARE = "hyperhomes.share";
    /** Accept a share request (new granular permission) */
    public static final String SHARE_ACCEPT = "hyperhomes.share.accept";

    // === Bypass (hyperhomes.bypass.*) ===
    public static final String BYPASS_WILDCARD = "hyperhomes.bypass.*";
    /** Bypass teleport warmup delay */
    public static final String BYPASS_WARMUP = "hyperhomes.bypass.warmup";
    /** Bypass teleport cooldown timer */
    public static final String BYPASS_COOLDOWN = "hyperhomes.bypass.cooldown";
    /** Bypass home limit */
    public static final String BYPASS_LIMIT = "hyperhomes.bypass.limit";

    // === Admin (hyperhomes.admin.*) ===
    public static final String ADMIN_WILDCARD = "hyperhomes.admin.*";
    /** Base admin access (opens admin GUI) */
    public static final String ADMIN = "hyperhomes.admin";
    /** Teleport to other players' homes */
    public static final String ADMIN_TELEPORT_OTHERS = "hyperhomes.admin.teleport.others";
    /** Access admin settings */
    public static final String ADMIN_SETTINGS = "hyperhomes.admin.settings";
    /** Reload configuration */
    public static final String ADMIN_RELOAD = "hyperhomes.admin.reload";
    /** Check for updates */
    public static final String ADMIN_UPDATE = "hyperhomes.admin.update";
    /** Migrate data from other plugins */
    public static final String ADMIN_MIGRATE = "hyperhomes.admin.migrate";

    // === Limits (hyperhomes.limit.*) ===
    /** Unlimited homes */
    public static final String UNLIMITED = "hyperhomes.unlimited";
    /** Home limit prefix (e.g., hyperhomes.limit.5) */
    public static final String LIMIT_PREFIX = "hyperhomes.limit.";

    /**
     * Gets all defined permissions for registration.
     *
     * @return array of all permission nodes
     */
    public static String[] getAllPermissions() {
        return new String[] {
            // Basic
            USE, GUI,
            // Home management
            SET, DELETE, LIST,
            // Sharing
            SHARE, SHARE_ACCEPT,
            // Bypass
            BYPASS_WARMUP, BYPASS_COOLDOWN, BYPASS_LIMIT,
            // Admin
            ADMIN, ADMIN_TELEPORT_OTHERS, ADMIN_SETTINGS,
            ADMIN_RELOAD, ADMIN_UPDATE, ADMIN_MIGRATE,
            // Limits
            UNLIMITED
        };
    }

    /**
     * Gets all category wildcards for registration.
     *
     * @return array of wildcard permission nodes
     */
    public static String[] getWildcards() {
        return new String[] {
            WILDCARD,
            SHARE_WILDCARD,
            BYPASS_WILDCARD,
            ADMIN_WILDCARD
        };
    }

    /**
     * Gets all user-level permissions (non-admin, non-bypass).
     *
     * @return array of user permission nodes
     */
    public static String[] getUserPermissions() {
        return new String[] {
            USE, GUI, SET, DELETE, LIST,
            SHARE, SHARE_ACCEPT
        };
    }

    /**
     * Gets all bypass permissions.
     *
     * @return array of bypass permission nodes
     */
    public static String[] getBypassPermissions() {
        return new String[] {
            BYPASS_WARMUP, BYPASS_COOLDOWN, BYPASS_LIMIT
        };
    }

    /**
     * Gets all admin permissions.
     *
     * @return array of admin permission nodes
     */
    public static String[] getAdminPermissions() {
        return new String[] {
            ADMIN, ADMIN_TELEPORT_OTHERS, ADMIN_SETTINGS,
            ADMIN_RELOAD, ADMIN_UPDATE, ADMIN_MIGRATE
        };
    }
}
