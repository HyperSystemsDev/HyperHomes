package com.hyperhomes.manager;

import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages pending home share requests.
 * Players must accept share requests before homes are actually shared.
 */
public class PendingShareManager {

    /**
     * Represents a pending share request.
     */
    public record PendingShare(
            UUID ownerUuid,
            String ownerName,
            String homeName,
            long timestamp
    ) {
        /**
         * Checks if this request has expired (5 minute timeout).
         */
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000;
        }
    }

    // Map of target player UUID to their pending share request
    // Each player can only have one pending request at a time
    private final Map<UUID, PendingShare> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Creates a pending share request.
     *
     * @param ownerUuid   the home owner's UUID
     * @param ownerName   the home owner's username
     * @param homeName    the name of the home being shared
     * @param targetUuid  the target player's UUID
     * @return true if the request was created, false if one already exists
     */
    public boolean createRequest(@NotNull UUID ownerUuid, @NotNull String ownerName,
                                  @NotNull String homeName, @NotNull UUID targetUuid) {
        // Clean up any expired request first
        PendingShare existing = pendingRequests.get(targetUuid);
        if (existing != null && !existing.isExpired()) {
            return false; // Already has a pending request
        }

        PendingShare request = new PendingShare(ownerUuid, ownerName, homeName, System.currentTimeMillis());
        pendingRequests.put(targetUuid, request);
        Logger.debug("Created pending share request: %s wants to share '%s' with %s",
                ownerName, homeName, targetUuid);
        return true;
    }

    /**
     * Gets the pending share request for a player.
     *
     * @param targetUuid the target player's UUID
     * @return the pending request, or null if none exists or it's expired
     */
    @Nullable
    public PendingShare getRequest(@NotNull UUID targetUuid) {
        PendingShare request = pendingRequests.get(targetUuid);
        if (request != null && request.isExpired()) {
            pendingRequests.remove(targetUuid);
            return null;
        }
        return request;
    }

    /**
     * Accepts a pending share request.
     *
     * @param targetUuid the target player's UUID
     * @return the accepted request, or null if none exists
     */
    @Nullable
    public PendingShare acceptRequest(@NotNull UUID targetUuid) {
        PendingShare request = pendingRequests.remove(targetUuid);
        if (request != null && request.isExpired()) {
            return null;
        }
        if (request != null) {
            Logger.debug("Share request accepted: %s accepted share of '%s' from %s",
                    targetUuid, request.homeName(), request.ownerName());
        }
        return request;
    }

    /**
     * Declines a pending share request.
     *
     * @param targetUuid the target player's UUID
     * @return the declined request, or null if none exists
     */
    @Nullable
    public PendingShare declineRequest(@NotNull UUID targetUuid) {
        PendingShare request = pendingRequests.remove(targetUuid);
        if (request != null) {
            Logger.debug("Share request declined: %s declined share of '%s' from %s",
                    targetUuid, request.homeName(), request.ownerName());
        }
        return request;
    }

    /**
     * Checks if a player has a pending share request.
     *
     * @param targetUuid the target player's UUID
     * @return true if they have a valid pending request
     */
    public boolean hasPendingRequest(@NotNull UUID targetUuid) {
        return getRequest(targetUuid) != null;
    }

    /**
     * Cleans up expired requests.
     */
    public void cleanupExpired() {
        pendingRequests.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
}
