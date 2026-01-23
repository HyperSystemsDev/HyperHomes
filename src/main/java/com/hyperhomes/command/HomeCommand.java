package com.hyperhomes.command;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.config.HyperHomesConfig;
import com.hyperhomes.gui.UIHelper;
import com.hyperhomes.integration.HyperPermsIntegration;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.TeleportManager;
import com.hyperhomes.model.Home;
import com.hyperhomes.model.Location;
import com.hyperhomes.platform.HyperHomesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

/**
 * /home [name] - Teleport to your home.
 * /home <player>:<name> - Teleport to a shared home.
 *
 * Usage:
 *   /home              - Teleport to default "home"
 *   /home base         - Teleport to home named "base"
 *   /home Steve:base   - Teleport to Steve's shared home "base"
 */
public class HomeCommand extends AbstractPlayerCommand {

    // Colors
    private static final String COLOR_GOLD = "#FFAA00";
    private static final String COLOR_GREEN = "#55FF55";
    private static final String COLOR_RED = "#FF5555";
    private static final String COLOR_YELLOW = "#FFFF55";
    private static final String COLOR_GRAY = "#AAAAAA";
    private static final String COLOR_WHITE = "#FFFFFF";

    private final HyperHomes plugin;

    public HomeCommand(@NotNull HyperHomes plugin) {
        super("home", "Teleport to your home");
        this.plugin = plugin;

        addAliases("h");
        setAllowsExtraArguments(true); // Allow positional args
    }

    /**
     * Parsed home target - either own home, shared home, or special command.
     */
    private record HomeTarget(String ownerName, UUID ownerUuid, String homeName,
                              boolean isShared, boolean isHelp, boolean isShareHint, boolean isNoArg) {
        static HomeTarget own(String homeName) {
            return new HomeTarget(null, null, homeName, false, false, false, false);
        }
        static HomeTarget shared(String ownerName, UUID ownerUuid, String homeName) {
            return new HomeTarget(ownerName, ownerUuid, homeName, true, false, false, false);
        }
        static HomeTarget help() {
            return new HomeTarget(null, null, null, false, true, false, false);
        }
        static HomeTarget shareHint() {
            return new HomeTarget(null, null, null, false, false, true, false);
        }
        static HomeTarget noArg() {
            return new HomeTarget(null, null, null, false, false, false, true);
        }
    }

    /**
     * Parse home target from command input.
     * /home → smart selection (no-arg)
     * /home help or /home ? → show help
     * /home share → hint about /homes share
     * /home base → own "base"
     * /home Steve:base → shared from Steve
     */
    private HomeTarget parseHomeTarget(CommandContext ctx, HomeManager homeManager) {
        String input = ctx.getInputString();
        if (input == null || input.isEmpty()) {
            return HomeTarget.noArg();
        }

        String[] parts = input.trim().split("\\s+");
        // parts[0] is "home" or "h"
        if (parts.length <= 1) {
            return HomeTarget.noArg();
        }

        String arg = parts[1];

        // Check for help argument
        if (arg.equalsIgnoreCase("help") || arg.equals("?")) {
            return HomeTarget.help();
        }

        // Check for share hint
        if (arg.equalsIgnoreCase("share")) {
            return HomeTarget.shareHint();
        }

        // Check for player:home syntax
        if (arg.contains(":")) {
            String[] targetParts = arg.split(":", 2);
            String playerName = targetParts[0];
            String homeName = targetParts.length > 1 ? targetParts[1].toLowerCase() : "home";

            com.hyperhomes.util.Logger.info("[SHARE-DEBUG] Parsing shared home: player='%s', home='%s'", playerName, homeName);

            // Find the owner player
            UUID ownerUuid = homeManager.findPlayerByUsername(playerName);
            com.hyperhomes.util.Logger.info("[SHARE-DEBUG] Owner UUID lookup result: %s", ownerUuid);

            if (ownerUuid != null) {
                return HomeTarget.shared(playerName, ownerUuid, homeName);
            }
            // If player not found, treat as invalid shared home (will error later)
            return HomeTarget.shared(playerName, null, homeName);
        }

        return HomeTarget.own(arg.toLowerCase());
    }

    @Override
    protected void execute(@NotNull CommandContext ctx,
                          @NotNull Store<EntityStore> store,
                          @NotNull Ref<EntityStore> ref,
                          @NotNull PlayerRef playerRef,
                          @NotNull World currentWorld) {

        UUID uuid = playerRef.getUuid();

        // Permission check
        if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.use")) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("You don't have permission to use this command.").color(COLOR_RED)));
            return;
        }

        HomeManager homeManager = plugin.getHomeManager();
        TeleportManager teleportManager = plugin.getTeleportManager();
        HyperHomesConfig config = HyperHomesConfig.get();

        // Parse home target (own or shared)
        HomeTarget target = parseHomeTarget(ctx, homeManager);

        // Handle help
        if (target.isHelp()) {
            showHelp(ctx);
            return;
        }

        // Handle share hint
        if (target.isShareHint()) {
            ctx.sendMessage(prefix().insert(Message.raw("To share a home, use:").color(COLOR_YELLOW)));
            ctx.sendMessage(Message.raw("/homes share <home> <player>").color(COLOR_GREEN));
            return;
        }

        // Handle no-argument case (smart home selection)
        if (target.isNoArg()) {
            Collection<Home> homes = homeManager.getHomes(uuid);
            if (homes.isEmpty()) {
                // No homes - show helpful message
                ctx.sendMessage(prefix().insert(Message.raw("You don't have any homes set.").color(COLOR_RED)));
                ctx.sendMessage(Message.raw("Use /sethome to create your first home.").color(COLOR_GRAY));
                return;
            } else if (homes.size() == 1) {
                // Single home - teleport directly (replace target)
                target = HomeTarget.own(homes.iterator().next().name());
            } else {
                // Multiple homes - prompt user
                ctx.sendMessage(prefix().insert(Message.raw("Which home would you like to go to?").color(COLOR_YELLOW)));
                String homeList = String.join(", ", homes.stream().map(Home::name).sorted().toList());
                ctx.sendMessage(Message.raw("Your homes: ").color(COLOR_GRAY).insert(Message.raw(homeList).color(COLOR_GREEN)));
                ctx.sendMessage(Message.raw("Use /home <name> to teleport.").color(COLOR_GRAY));
                return;
            }
        }

        // Check cooldown
        long remainingCooldown = homeManager.getRemainingCooldown(uuid);
        if (remainingCooldown > 0) {
            String timeStr = formatTime(remainingCooldown);
            ctx.sendMessage(prefix()
                .insert(Message.raw("You must wait ").color(COLOR_RED))
                .insert(Message.raw(timeStr).color(COLOR_YELLOW))
                .insert(Message.raw(" before teleporting again.").color(COLOR_RED)));
            return;
        }

        // Get home (own or shared)
        Home home;
        if (target.isShared()) {
            // Accessing a shared home
            if (target.ownerUuid() == null) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Player '").color(COLOR_RED))
                    .insert(Message.raw(target.ownerName()).color(COLOR_YELLOW))
                    .insert(Message.raw("' not found or not online.").color(COLOR_RED)));
                return;
            }

            home = homeManager.getSharedHome(uuid, target.ownerUuid(), target.homeName());
            if (home == null) {
                // Check if the home exists but isn't shared
                Home ownerHome = homeManager.getHome(target.ownerUuid(), target.homeName());
                if (ownerHome != null) {
                    ctx.sendMessage(prefix()
                        .insert(Message.raw("You don't have access to ").color(COLOR_RED))
                        .insert(Message.raw(target.ownerName() + ":" + target.homeName()).color(COLOR_YELLOW))
                        .insert(Message.raw(".").color(COLOR_RED)));
                } else {
                    ctx.sendMessage(prefix()
                        .insert(Message.raw("Home '").color(COLOR_RED))
                        .insert(Message.raw(target.ownerName() + ":" + target.homeName()).color(COLOR_YELLOW))
                        .insert(Message.raw("' not found!").color(COLOR_RED)));
                }
                return;
            }
        } else {
            // Accessing own home
            home = homeManager.getHome(uuid, target.homeName());
            if (home == null) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Home '").color(COLOR_RED))
                    .insert(Message.raw(target.homeName()).color(COLOR_YELLOW))
                    .insert(Message.raw("' not found!").color(COLOR_RED)));

                // Suggest available homes
                Collection<Home> homes = homeManager.getHomes(uuid);
                if (!homes.isEmpty()) {
                    String homeList = String.join(", ", homes.stream().map(Home::name).sorted().toList());
                    ctx.sendMessage(Message.raw("Your homes: ").color(COLOR_GRAY)
                        .insert(Message.raw(homeList).color(COLOR_GREEN)));
                } else {
                    ctx.sendMessage(Message.raw("Use ").color(COLOR_GRAY)
                        .insert(Message.raw("/sethome").color(COLOR_GREEN))
                        .insert(Message.raw(" to create your first home.").color(COLOR_GRAY)));
                }
                return;
            }
        }

        // Get current position for warmup tracking
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Location currentLocation = null;
        if (transform != null) {
            Vector3d pos = transform.getPosition();
            currentLocation = new Location(currentWorld.getName(), pos.getX(), pos.getY(), pos.getZ(), 0, 0);
        }

        // Use TeleportManager for warmup handling
        final Location finalCurrentLocation = currentLocation != null ? currentLocation :
            new Location(currentWorld.getName(), 0, 0, 0, 0, 0);

        // Store entity context for movement checking
        HyperHomesPlugin pluginInstance = HyperHomesPlugin.getInstance();
        if (pluginInstance != null) {
            pluginInstance.storeEntityContext(uuid, store, ref);
        }

        boolean started = teleportManager.teleportToHome(
            uuid,
            home,
            finalCurrentLocation,
            // Task scheduler
            (delayTicks, task) -> plugin.scheduleDelayedTask(delayTicks, task),
            // Task canceller
            plugin::cancelTask,
            // Teleport executor
            (h) -> {
                // Clean up entity context
                if (pluginInstance != null) {
                    pluginInstance.removeEntityContext(uuid);
                }
                return executeTeleport(store, ref, playerRef, currentWorld, h);
            },
            // Message sender
            (msg) -> ctx.sendMessage(UIHelper.parseColorCodes(msg))
        );

        if (!started) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("Failed to start teleport.").color(COLOR_RED)));
        }
    }

    /**
     * Actually perform the teleport.
     */
    private TeleportManager.TeleportResult executeTeleport(
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            PlayerRef playerRef,
            World currentWorld,
            Home home) {

        // Get target world
        World targetWorld = Universe.get().getWorld(home.world());
        if (targetWorld == null) {
            // Fall back to current world if home world doesn't exist
            if (!HyperHomesConfig.get().isAllowCrossWorld()) {
                return TeleportManager.TeleportResult.WORLD_NOT_FOUND;
            }
            targetWorld = currentWorld;
        }

        // Execute teleport on the target world's thread
        final World finalTargetWorld = targetWorld;

        targetWorld.execute(() -> {
            Vector3d position = new Vector3d(home.x(), home.y(), home.z());
            Vector3f rotation = new Vector3f(home.pitch(), home.yaw(), 0);

            Teleport teleport = new Teleport(finalTargetWorld, position, rotation);
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        });

        return TeleportManager.TeleportResult.SUCCESS;
    }

    /**
     * Format milliseconds to human-readable time.
     */
    private String formatTime(long ms) {
        long seconds = (ms + 999) / 1000; // Round up
        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (seconds == 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        return minutes + "m " + seconds + "s";
    }

    private Message prefix() {
        return Message.raw("[").color(COLOR_GRAY)
            .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
            .insert(Message.raw("] ").color(COLOR_GRAY));
    }

    /**
     * Shows help information for the /home command.
     */
    private void showHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Home Command Help ===").color(COLOR_GOLD).bold(true));
        ctx.sendMessage(Message.raw("/home").color(COLOR_GREEN).insert(Message.raw(" - Teleport to your home").color(COLOR_GRAY)));
        ctx.sendMessage(Message.raw("/home <name>").color(COLOR_GREEN).insert(Message.raw(" - Teleport to specific home").color(COLOR_GRAY)));
        ctx.sendMessage(Message.raw("/home <player>:<name>").color(COLOR_GREEN).insert(Message.raw(" - Teleport to shared home").color(COLOR_GRAY)));
        ctx.sendMessage(Message.raw("See /homes help for all commands.").color(COLOR_YELLOW));
    }
}
