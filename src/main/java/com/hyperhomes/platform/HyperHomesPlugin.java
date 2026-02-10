package com.hyperhomes.platform;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.api.HyperHomesAPI;
import com.hyperhomes.command.DelHomeCommand;
import com.hyperhomes.command.HomeCommand;
import com.hyperhomes.command.HomesCommand;
import com.hyperhomes.command.SetHomeCommand;
import com.hyperhomes.config.ConfigManager;
import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.gui.UIHelper;
import com.hyperhomes.listener.BedListener;
import com.hyperhomes.listener.PlayerListener;
import com.hyperhomes.manager.TeleportManager;
import com.hyperhomes.data.Location;
import com.hyperhomes.util.Logger;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Main Hytale plugin class for HyperHomes.
 * Provides personal home location management for players.
 */
public class HyperHomesPlugin extends JavaPlugin {

    private static HyperHomesPlugin instance;

    /**
     * Gets the plugin instance.
     */
    public static HyperHomesPlugin getInstance() {
        return instance;
    }

    private HyperHomes hyperHomes;
    private GuiManager guiManager;
    private PlayerListener playerListener;
    private BedListener bedListener;

    // Task scheduling
    private final AtomicInteger taskIdCounter = new AtomicInteger(0);
    private final Map<Integer, Object> scheduledTasks = new ConcurrentHashMap<>();

    // Player tracking for the adapter
    private final Map<UUID, PlayerRef> trackedPlayers = new ConcurrentHashMap<>();

    // Entity context tracking for movement checking (store/ref needed to access components)
    private final Map<UUID, EntityContext> entityContexts = new ConcurrentHashMap<>();

    // Movement checker for warmup cancellation
    private ScheduledExecutorService movementCheckerExecutor;
    private ScheduledFuture<?> movementCheckerTask;

    /**
     * Stores entity context for movement checking.
     */
    public record EntityContext(Store<EntityStore> store, com.hypixel.hytale.component.Ref<EntityStore> ref) {}

    /**
     * Creates a new HyperHomesPlugin instance.
     *
     * @param init the plugin initialization data
     */
    public HyperHomesPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;

        // Initialize HyperHomes core
        hyperHomes = new HyperHomes(getDataDirectory(), java.util.logging.Logger.getLogger("HyperHomes"));

        // Set API instance
        HyperHomesAPI.setInstance(hyperHomes);

        getLogger().at(Level.INFO).log("HyperHomes setup complete");
    }

    @Override
    protected void start() {
        // Configure platform callbacks
        configurePlatformCallbacks();

        // Enable core
        hyperHomes.enable();

        // Initialize GUI Manager
        guiManager = new GuiManager(
            () -> hyperHomes,
            () -> hyperHomes.getHomeManager(),
            () -> hyperHomes.getTeleportManager(),
            () -> hyperHomes.getDataDir(),
            () -> hyperHomes.getPendingShareManager()
        );

        // Register commands
        registerCommands();

        // Register event listeners
        registerEventListeners();

        // Register ECS systems for damage cancellation
        registerDamageSystem();

        // Start movement checker for warmup cancellation
        startMovementChecker();

        getLogger().at(Level.INFO).log("HyperHomes v%s enabled!", getManifest().getVersion());
    }

    @Override
    protected void shutdown() {
        // Stop movement checker
        stopMovementChecker();

        // Clear instances
        instance = null;
        HyperHomesAPI.setInstance(null);

        // Disable core
        if (hyperHomes != null) {
            hyperHomes.disable();
        }

        // Clear tracked players
        trackedPlayers.clear();

        getLogger().at(Level.INFO).log("HyperHomes disabled");
    }

    /**
     * Configures platform-specific callbacks for HyperHomes core.
     */
    private void configurePlatformCallbacks() {
        // Async executor - use CompletableFuture for async work
        hyperHomes.setAsyncExecutor(task -> {
            java.util.concurrent.CompletableFuture.runAsync(task);
        });

        // Task scheduler - use plugin scheduler
        hyperHomes.setTaskScheduler((delayTicks, task) -> {
            int id = taskIdCounter.incrementAndGet();
            // Schedule a delayed task using Hytale's scheduler
            // Note: Actual implementation depends on Hytale's scheduler API
            // This is a simplified implementation
            java.util.Timer timer = new java.util.Timer();
            long delayMs = delayTicks * 50L; // 1 tick = 50ms
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    scheduledTasks.remove(id);
                    task.run();
                }
            }, delayMs);
            scheduledTasks.put(id, timer);
            return id;
        });

        // Task canceller
        hyperHomes.setTaskCanceller(taskId -> {
            Object task = scheduledTasks.remove(taskId);
            if (task instanceof java.util.Timer timer) {
                timer.cancel();
            }
        });

        // World lookup
        hyperHomes.setWorldLookup(name -> {
            return Universe.get().getWorld(name);
        });

        // Teleport callback
        hyperHomes.setTeleportCallback((playerRef, world, x, y, z, yaw, pitch) -> {
            try {
                var holder = playerRef.getHolder();
                if (holder == null) {
                    return false;
                }

                // Create teleport component with destination
                Vector3d position = new Vector3d(x, y, z);
                Vector3f rotation = new Vector3f(pitch, yaw, 0); // pitch=x, yaw=y, roll=z

                // Add teleport component to initiate teleportation
                Teleport teleport = new Teleport(world, position, rotation);
                holder.addComponent(Teleport.getComponentType(), teleport);

                return true;
            } catch (Exception e) {
                Logger.severe("Failed to teleport player %s", e, playerRef.getUsername());
                return false;
            }
        });
    }

    /**
     * Registers commands with Hytale.
     */
    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new HomeCommand(hyperHomes));
            getCommandRegistry().registerCommand(new SetHomeCommand(hyperHomes));
            getCommandRegistry().registerCommand(new DelHomeCommand(hyperHomes));
            getCommandRegistry().registerCommand(new HomesCommand(hyperHomes, guiManager));

            getLogger().at(Level.INFO).log("Registered commands: /home, /sethome, /delhome, /homes");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).withCause(e).log("Failed to register commands");
        }
    }

    /**
     * Registers event listeners with Hytale.
     */
    private void registerEventListeners() {
        // Player connect event - load data
        getEventRegistry().register(PlayerConnectEvent.class, this::onPlayerConnect);

        // Player disconnect event - save and cleanup
        getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        // Create player listener for movement/damage
        playerListener = new PlayerListener(hyperHomes);

        // Create bed listener for bed sync
        bedListener = new BedListener(hyperHomes);

        // Note: Movement, damage, and bed events would need to be registered
        // depending on how Hytale exposes them. This plugin tracks players
        // and the listeners can be called when relevant events are detected.
        // For bed sync, call bedListener.onBedSpawnSet() when player sets spawn at bed.

        getLogger().at(Level.INFO).log("Registered event listeners");
    }

    /**
     * Handles player connect event.
     *
     * @param event the event
     */
    private void onPlayerConnect(PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();
        String username = playerRef.getUsername();

        Logger.debug("Player connecting: %s (%s)", username, uuid);

        // Track the player
        trackedPlayers.put(uuid, playerRef);

        // Load player homes async
        hyperHomes.getHomeManager().loadPlayer(uuid, username)
            .exceptionally(e -> {
                Logger.severe("Failed to load homes for %s", e, username);
                return null;
            });
    }

    /**
     * Handles player disconnect event.
     *
     * @param event the event
     */
    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        UUID uuid = playerRef.getUuid();
        String username = playerRef.getUsername();

        Logger.debug("Player disconnecting: %s", username);

        // Untrack the player
        trackedPlayers.remove(uuid);

        // Cancel any pending teleport
        hyperHomes.getTeleportManager().cancelPending(uuid, hyperHomes::cancelTask);

        // Save and unload player data
        hyperHomes.getHomeManager().unloadPlayer(uuid)
            .exceptionally(e -> {
                Logger.severe("Failed to save homes for %s", e, username);
                return null;
            });
    }

    /**
     * Gets a tracked player by UUID.
     *
     * @param uuid the player's UUID
     * @return the PlayerRef, or null if not tracked
     */
    public PlayerRef getTrackedPlayer(UUID uuid) {
        return trackedPlayers.get(uuid);
    }

    /**
     * Gets the player listener.
     *
     * @return the player listener
     */
    public PlayerListener getPlayerListener() {
        return playerListener;
    }

    /**
     * Gets the bed listener.
     *
     * @return the bed listener
     */
    public BedListener getBedListener() {
        return bedListener;
    }

    /**
     * Reloads the configuration.
     */
    public void reloadConfig() {
        hyperHomes.reloadConfig();
    }

    /**
     * Gets the HyperHomes instance.
     *
     * @return the HyperHomes instance
     */
    public HyperHomes getHyperHomes() {
        return hyperHomes;
    }

    /**
     * Gets the GUI Manager instance.
     *
     * @return the GUI Manager instance
     */
    public GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Stores entity context for a player (used for movement checking).
     */
    public void storeEntityContext(UUID uuid, Store<EntityStore> store, com.hypixel.hytale.component.Ref<EntityStore> ref) {
        entityContexts.put(uuid, new EntityContext(store, ref));
    }

    /**
     * Removes entity context for a player.
     */
    public void removeEntityContext(UUID uuid) {
        entityContexts.remove(uuid);
    }

    /**
     * Registers the damage event system for teleport cancellation.
     */
    private void registerDamageSystem() {
        try {
            var damageSystem = new TeleportCancelOnDamageSystem(hyperHomes, trackedPlayers);
            getEntityStoreRegistry().registerSystem(damageSystem);
            getLogger().at(Level.INFO).log("Registered damage cancellation system");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).withCause(e).log("Failed to register damage system - cancel on damage will not work");
        }
    }

    /**
     * Starts the movement checker task.
     */
    private void startMovementChecker() {
        movementCheckerExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HyperHomes-MovementChecker");
            t.setDaemon(true);
            return t;
        });

        // Check movement every 100ms (2 ticks)
        movementCheckerTask = movementCheckerExecutor.scheduleAtFixedRate(
            this::checkAllPendingMovement,
            100, 100, TimeUnit.MILLISECONDS
        );

        getLogger().at(Level.INFO).log("Started movement checker");
    }

    /**
     * Stops the movement checker task.
     */
    private void stopMovementChecker() {
        if (movementCheckerTask != null) {
            movementCheckerTask.cancel(false);
        }
        if (movementCheckerExecutor != null) {
            movementCheckerExecutor.shutdown();
        }
    }

    /**
     * Checks all players with pending teleports for movement.
     */
    private void checkAllPendingMovement() {
        if (!ConfigManager.get().isCancelOnMove()) {
            return;
        }

        TeleportManager teleportManager = hyperHomes.getTeleportManager();

        for (Map.Entry<UUID, PlayerRef> entry : trackedPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerRef playerRef = entry.getValue();

            // Get pending teleport to find the world
            TeleportManager.PendingTeleport pending = teleportManager.getPending(uuid);
            if (pending == null) {
                continue;
            }

            try {
                // Get the stored entity context
                EntityContext ctx = entityContexts.get(uuid);
                if (ctx == null) {
                    continue;
                }

                // Get the world from the start location
                String worldName = pending.startLocation().world();
                World world = Universe.get().getWorld(worldName);
                if (world == null) {
                    continue;
                }

                // Execute position check on the world's thread using stored store/ref
                final Store<EntityStore> store = ctx.store();
                final com.hypixel.hytale.component.Ref<EntityStore> ref = ctx.ref();

                world.execute(() -> {
                    try {
                        var transform = store.getComponent(ref, TransformComponent.getComponentType());
                        if (transform == null) {
                            return;
                        }

                        var pos = transform.getPosition();
                        Location currentLocation = new Location(
                            worldName,
                            pos.getX(), pos.getY(), pos.getZ(),
                            0, 0
                        );

                        // Check if player moved too much
                        boolean cancelled = teleportManager.checkMovement(
                            uuid,
                            currentLocation,
                            hyperHomes::cancelTask,
                            msg -> playerRef.sendMessage(UIHelper.parseColorCodes(msg))
                        );

                        if (cancelled) {
                            removeEntityContext(uuid);
                        }
                    } catch (Exception e) {
                        // Ignore errors - player may have disconnected
                    }
                });
            } catch (Exception e) {
                Logger.debug("[MovementCheck] Error: %s", e.getMessage());
            }
        }
    }

    /**
     * ECS system for cancelling teleports when player takes damage.
     */
    private static class TeleportCancelOnDamageSystem extends EntityEventSystem<EntityStore, Damage> {
        private final HyperHomes plugin;
        private final Map<UUID, PlayerRef> trackedPlayers;

        public TeleportCancelOnDamageSystem(HyperHomes plugin, Map<UUID, PlayerRef> trackedPlayers) {
            super(Damage.class);
            this.plugin = plugin;
            this.trackedPlayers = trackedPlayers;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }

        @Override
        public void handle(int entityIndex, ArchetypeChunk<EntityStore> chunk,
                           Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                           Damage damage) {
            try {
                if (!ConfigManager.get().isCancelOnDamage()) {
                    return;
                }

                // The entity at entityIndex is the one receiving damage (victim)
                // Check if the victim is a player
                PlayerRef victimPlayerRef = chunk.getComponent(entityIndex, PlayerRef.getComponentType());
                if (victimPlayerRef == null) {
                    return;
                }

                UUID victimUuid = victimPlayerRef.getUuid();
                if (victimUuid == null) {
                    return;
                }

                // Check if this player has a pending teleport
                TeleportManager teleportManager = plugin.getTeleportManager();
                if (!teleportManager.hasPending(victimUuid)) {
                    return;
                }

                // Cancel the teleport
                teleportManager.cancelOnDamage(
                    victimUuid,
                    plugin::cancelTask,
                    msg -> victimPlayerRef.sendMessage(UIHelper.parseColorCodes(msg))
                );
            } catch (Exception e) {
                Logger.severe("Error processing damage event for teleport cancel: " + e.getMessage(), e);
            }
        }
    }
}
