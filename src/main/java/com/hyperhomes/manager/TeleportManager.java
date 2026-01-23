package com.hyperhomes.manager;

import com.hyperhomes.config.HyperHomesConfig;
import com.hyperhomes.integration.HyperPermsIntegration;
import com.hyperhomes.model.Home;
import com.hyperhomes.model.Location;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages teleportation warmup, cancellation, and execution.
 */
public class TeleportManager {

    private final HomeManager homeManager;
    private final Map<UUID, PendingTeleport> pendingTeleports;

    /**
     * Represents a pending teleport with warmup.
     */
    public record PendingTeleport(
        UUID playerUuid,
        Home destination,
        Location startLocation,
        long startTime,
        int warmupSeconds,
        int taskId,
        Consumer<TeleportResult> callback
    ) {}

    /**
     * Result of a teleport attempt.
     */
    public enum TeleportResult {
        SUCCESS,
        CANCELLED_MOVED,
        CANCELLED_DAMAGE,
        CANCELLED_MANUAL,
        WORLD_NOT_FOUND,
        UNSAFE_LOCATION
    }

    /**
     * Creates a new TeleportManager.
     *
     * @param homeManager the home manager
     */
    public TeleportManager(@NotNull HomeManager homeManager) {
        this.homeManager = homeManager;
        this.pendingTeleports = new ConcurrentHashMap<>();
    }

    /**
     * Initiates a teleport to a home.
     *
     * @param playerUuid      the player's UUID
     * @param home            the destination home
     * @param currentLocation the player's current location
     * @param scheduleTask    function to schedule a delayed task, returns task ID
     * @param cancelTask      function to cancel a task by ID
     * @param doTeleport      function to actually perform the teleport
     * @param sendMessage     function to send a message to the player
     * @return true if teleport started/executed
     */
    public boolean teleportToHome(
        @NotNull UUID playerUuid,
        @NotNull Home home,
        @NotNull Location currentLocation,
        @NotNull TaskScheduler scheduleTask,
        @NotNull Consumer<Integer> cancelTask,
        @NotNull TeleportExecutor doTeleport,
        @NotNull Consumer<String> sendMessage
    ) {
        HyperHomesConfig config = HyperHomesConfig.get();

        // Cancel any existing pending teleport
        cancelPending(playerUuid, cancelTask);

        // Check if warmup is needed
        int warmup = getWarmupSeconds(playerUuid);
        if (warmup <= 0) {
            // Instant teleport
            TeleportResult result = doTeleport.execute(home);
            handleResult(playerUuid, home.name(), result, sendMessage);
            if (result == TeleportResult.SUCCESS) {
                homeManager.recordTeleport(playerUuid);
            }
            return result == TeleportResult.SUCCESS;
        }

        // Send warmup message
        sendMessage.accept(config.getTeleportingMessage(home.name(), warmup));

        // Schedule the teleport
        int taskId = scheduleTask.schedule(warmup * 20, () -> {
            PendingTeleport pending = pendingTeleports.remove(playerUuid);
            if (pending != null) {
                TeleportResult result = doTeleport.execute(home);
                handleResult(playerUuid, home.name(), result, sendMessage);
                if (result == TeleportResult.SUCCESS) {
                    homeManager.recordTeleport(playerUuid);
                }
            }
        });

        // Store pending teleport
        PendingTeleport pending = new PendingTeleport(
            playerUuid, home, currentLocation,
            System.currentTimeMillis(), warmup, taskId, null
        );
        pendingTeleports.put(playerUuid, pending);

        return true;
    }

    /**
     * Checks if a player moved and should cancel teleport.
     *
     * @param playerUuid      the player's UUID
     * @param currentLocation the player's current location
     * @param cancelTask      function to cancel a task
     * @param sendMessage     function to send a message
     * @return true if teleport was cancelled
     */
    public boolean checkMovement(
        @NotNull UUID playerUuid,
        @NotNull Location currentLocation,
        @NotNull Consumer<Integer> cancelTask,
        @NotNull Consumer<String> sendMessage
    ) {
        if (!HyperHomesConfig.get().isCancelOnMove()) {
            return false;
        }

        PendingTeleport pending = pendingTeleports.get(playerUuid);
        if (pending == null) {
            return false;
        }

        Location start = pending.startLocation();
        // Check if player moved more than 0.5 blocks
        double dx = currentLocation.x() - start.x();
        double dy = currentLocation.y() - start.y();
        double dz = currentLocation.z() - start.z();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > 0.25) { // 0.5 blocks squared
            cancelPending(playerUuid, cancelTask);
            sendMessage.accept(HyperHomesConfig.get().getTeleportCancelledMessage());
            return true;
        }

        return false;
    }

    /**
     * Cancels teleport due to damage.
     *
     * @param playerUuid  the player's UUID
     * @param cancelTask  function to cancel a task
     * @param sendMessage function to send a message
     * @return true if teleport was cancelled
     */
    public boolean cancelOnDamage(
        @NotNull UUID playerUuid,
        @NotNull Consumer<Integer> cancelTask,
        @NotNull Consumer<String> sendMessage
    ) {
        if (!HyperHomesConfig.get().isCancelOnDamage()) {
            return false;
        }

        if (pendingTeleports.containsKey(playerUuid)) {
            cancelPending(playerUuid, cancelTask);
            sendMessage.accept(HyperHomesConfig.get().getTeleportCancelledMessage());
            return true;
        }

        return false;
    }

    /**
     * Checks if a player has a pending teleport.
     *
     * @param playerUuid the player's UUID
     * @return true if pending
     */
    public boolean hasPending(@NotNull UUID playerUuid) {
        return pendingTeleports.containsKey(playerUuid);
    }

    /**
     * Gets the pending teleport for a player.
     *
     * @param playerUuid the player's UUID
     * @return the pending teleport, or null if none
     */
    @Nullable
    public PendingTeleport getPending(@NotNull UUID playerUuid) {
        return pendingTeleports.get(playerUuid);
    }

    /**
     * Cancels a pending teleport.
     *
     * @param playerUuid the player's UUID
     * @param cancelTask function to cancel a task
     */
    public void cancelPending(@NotNull UUID playerUuid, @NotNull Consumer<Integer> cancelTask) {
        PendingTeleport pending = pendingTeleports.remove(playerUuid);
        if (pending != null) {
            cancelTask.accept(pending.taskId());
            Logger.debug("Cancelled pending teleport for %s", playerUuid);
        }
    }

    /**
     * Gets the warmup seconds for a player.
     *
     * @param playerUuid the player's UUID
     * @return warmup seconds, 0 if bypassed
     */
    private int getWarmupSeconds(@NotNull UUID playerUuid) {
        if (HyperPermsIntegration.hasPermission(playerUuid, "hyperhomes.bypass.warmup")) {
            return 0;
        }
        return HyperHomesConfig.get().getWarmupSeconds();
    }

    /**
     * Handles the teleport result.
     */
    private void handleResult(
        @NotNull UUID playerUuid,
        @NotNull String homeName,
        @NotNull TeleportResult result,
        @NotNull Consumer<String> sendMessage
    ) {
        HyperHomesConfig config = HyperHomesConfig.get();
        switch (result) {
            case SUCCESS -> sendMessage.accept(config.getTeleportSuccessMessage(homeName));
            case CANCELLED_MOVED, CANCELLED_DAMAGE, CANCELLED_MANUAL ->
                sendMessage.accept(config.getTeleportCancelledMessage());
            case WORLD_NOT_FOUND ->
                sendMessage.accept(config.getPrefix() + "\u00A7cWorld not found.");
            case UNSAFE_LOCATION ->
                sendMessage.accept(config.getPrefix() + "\u00A7cDestination is not safe.");
        }
    }

    /**
     * Functional interface for scheduling tasks.
     */
    @FunctionalInterface
    public interface TaskScheduler {
        /**
         * Schedules a task.
         *
         * @param delayTicks the delay in ticks
         * @param task       the task to run
         * @return the task ID
         */
        int schedule(int delayTicks, Runnable task);
    }

    /**
     * Functional interface for executing teleports.
     */
    @FunctionalInterface
    public interface TeleportExecutor {
        /**
         * Executes a teleport.
         *
         * @param home the destination
         * @return the result
         */
        TeleportResult execute(Home home);
    }
}
