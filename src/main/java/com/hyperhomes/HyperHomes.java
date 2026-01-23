package com.hyperhomes;

import com.hyperhomes.config.HyperHomesConfig;
import com.hyperhomes.integration.HyperPermsIntegration;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.PendingShareManager;
import com.hyperhomes.manager.TeleportManager;
import com.hyperhomes.migration.MigrationManager;
import com.hyperhomes.model.Home;
import com.hyperhomes.storage.StorageProvider;
import com.hyperhomes.storage.json.JsonStorageProvider;
import com.hyperhomes.update.UpdateChecker;
import com.hyperhomes.util.Logger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Main HyperHomes core class.
 * Manages homes, teleportation, and provides API access.
 */
public class HyperHomes {

    public static final String VERSION = "1.0.0";

    private final Path dataDir;
    private final java.util.logging.Logger javaLogger;
    @Nullable private UpdateChecker updateChecker;

    private StorageProvider storage;
    private HomeManager homeManager;
    private TeleportManager teleportManager;
    private PendingShareManager pendingShareManager;
    private MigrationManager migrationManager;

    // Task management
    private final AtomicInteger taskIdCounter = new AtomicInteger(0);
    private final Map<Integer, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>();

    // Platform callbacks (set by plugin)
    private Consumer<Runnable> asyncExecutor;
    private TaskSchedulerCallback taskScheduler;
    private TaskCancelCallback taskCanceller;
    private WorldLookup worldLookup;
    private TeleportCallback teleportCallback;

    /**
     * Functional interface for scheduling delayed tasks.
     */
    @FunctionalInterface
    public interface TaskSchedulerCallback {
        int schedule(int delayTicks, Runnable task);
    }

    /**
     * Functional interface for cancelling tasks.
     */
    @FunctionalInterface
    public interface TaskCancelCallback {
        void cancel(int taskId);
    }

    /**
     * Functional interface for looking up worlds.
     */
    @FunctionalInterface
    public interface WorldLookup {
        World getWorld(String name);
    }

    /**
     * Functional interface for teleporting players.
     */
    @FunctionalInterface
    public interface TeleportCallback {
        boolean teleport(PlayerRef player, World world, double x, double y, double z, float yaw, float pitch);
    }

    /**
     * Represents a scheduled task.
     */
    private record ScheduledTask(int id, Runnable task) {}

    /**
     * Creates a new HyperHomes instance.
     *
     * @param dataDir    the plugin data directory
     * @param javaLogger the Java logger
     */
    public HyperHomes(@NotNull Path dataDir, @NotNull java.util.logging.Logger javaLogger) {
        this.dataDir = dataDir;
        this.javaLogger = javaLogger;
    }

    /**
     * Enables HyperHomes.
     */
    public void enable() {
        // Initialize logger
        Logger.init(javaLogger);

        // Load configuration
        HyperHomesConfig.get().load(dataDir);

        // Initialize HyperPerms integration
        HyperPermsIntegration.init();

        // Initialize storage
        storage = new JsonStorageProvider(dataDir);
        storage.init().join();

        // Initialize managers
        homeManager = new HomeManager(storage);
        teleportManager = new TeleportManager(homeManager);
        pendingShareManager = new PendingShareManager();
        migrationManager = new MigrationManager(dataDir, homeManager);
        migrationManager.init();

        // Initialize update checker
        if (HyperHomesConfig.get().isUpdateCheckEnabled()) {
            updateChecker = new UpdateChecker(this, VERSION, HyperHomesConfig.get().getUpdateCheckUrl());
            updateChecker.checkForUpdates().thenAccept(info -> {
                if (info != null) {
                    Logger.info("===========================================");
                    Logger.info("A new version of HyperHomes is available!");
                    Logger.info("Current: " + VERSION + " | Latest: " + info.version());
                    Logger.info("===========================================");
                }
            });
        }

        Logger.info("HyperHomes enabled");
    }

    /**
     * Disables HyperHomes.
     */
    public void disable() {
        // Save all data
        if (homeManager != null) {
            homeManager.saveAll().join();
        }

        // Shutdown storage
        if (storage != null) {
            storage.shutdown().join();
        }

        // Cancel all scheduled tasks
        for (int taskId : scheduledTasks.keySet()) {
            cancelTask(taskId);
        }

        Logger.info("HyperHomes disabled");
    }

    /**
     * Reloads the configuration.
     */
    public void reloadConfig() {
        HyperHomesConfig.get().reload(dataDir);
        Logger.info("Configuration reloaded");
    }

    // Platform callbacks

    /**
     * Sets the async executor.
     *
     * @param executor the executor
     */
    public void setAsyncExecutor(@NotNull Consumer<Runnable> executor) {
        this.asyncExecutor = executor;
    }

    /**
     * Sets the task scheduler.
     *
     * @param scheduler the scheduler
     */
    public void setTaskScheduler(@NotNull TaskSchedulerCallback scheduler) {
        this.taskScheduler = scheduler;
    }

    /**
     * Sets the task canceller.
     *
     * @param canceller the canceller
     */
    public void setTaskCanceller(@NotNull TaskCancelCallback canceller) {
        this.taskCanceller = canceller;
    }

    /**
     * Sets the world lookup.
     *
     * @param lookup the lookup
     */
    public void setWorldLookup(@NotNull WorldLookup lookup) {
        this.worldLookup = lookup;
    }

    /**
     * Sets the teleport callback.
     *
     * @param callback the callback
     */
    public void setTeleportCallback(@NotNull TeleportCallback callback) {
        this.teleportCallback = callback;
    }

    /**
     * Schedules a delayed task.
     *
     * @param delayTicks the delay in ticks
     * @param task       the task
     * @return the task ID
     */
    public int scheduleDelayedTask(int delayTicks, @NotNull Runnable task) {
        if (taskScheduler != null) {
            int id = taskIdCounter.incrementAndGet();
            int platformId = taskScheduler.schedule(delayTicks, () -> {
                scheduledTasks.remove(id);
                task.run();
            });
            scheduledTasks.put(id, new ScheduledTask(platformId, task));
            return id;
        }
        // Fallback: run immediately
        task.run();
        return -1;
    }

    /**
     * Cancels a task.
     *
     * @param taskId the task ID
     */
    public void cancelTask(int taskId) {
        ScheduledTask task = scheduledTasks.remove(taskId);
        if (task != null && taskCanceller != null) {
            taskCanceller.cancel(task.id());
        }
    }

    /**
     * Executes a teleport.
     *
     * @param playerRef the player reference
     * @param home      the destination home
     * @return the teleport result
     */
    public TeleportManager.TeleportResult executeTeleport(@NotNull PlayerRef playerRef, @NotNull Home home) {
        if (worldLookup == null || teleportCallback == null) {
            return TeleportManager.TeleportResult.WORLD_NOT_FOUND;
        }

        World world = worldLookup.getWorld(home.world());
        if (world == null) {
            return TeleportManager.TeleportResult.WORLD_NOT_FOUND;
        }

        boolean success = teleportCallback.teleport(
            playerRef, world,
            home.x(), home.y(), home.z(),
            home.yaw(), home.pitch()
        );

        return success ? TeleportManager.TeleportResult.SUCCESS : TeleportManager.TeleportResult.UNSAFE_LOCATION;
    }

    // Getters

    /**
     * Gets the data directory.
     *
     * @return the data directory
     */
    @NotNull
    public Path getDataDir() {
        return dataDir;
    }

    /**
     * Gets the home manager.
     *
     * @return the home manager
     */
    @NotNull
    public HomeManager getHomeManager() {
        return homeManager;
    }

    /**
     * Gets the teleport manager.
     *
     * @return the teleport manager
     */
    @NotNull
    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    /**
     * Gets the storage provider.
     *
     * @return the storage provider
     */
    @NotNull
    public StorageProvider getStorage() {
        return storage;
    }

    /**
     * Gets the pending share manager.
     *
     * @return the pending share manager
     */
    @NotNull
    public PendingShareManager getPendingShareManager() {
        return pendingShareManager;
    }

    /**
     * Gets the update checker.
     *
     * @return the update checker, or null if disabled
     */
    @Nullable
    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    /**
     * Gets the migration manager.
     *
     * @return the migration manager
     */
    @NotNull
    public MigrationManager getMigrationManager() {
        return migrationManager;
    }
}
