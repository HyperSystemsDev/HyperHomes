package com.hyperhomes.command;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.config.HyperHomesConfig;
import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.integration.HyperPermsIntegration;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.migration.MigrationManager;
import com.hyperhomes.migration.MigrationResult;
import com.hyperhomes.migration.MigrationSource;
import com.hyperhomes.model.Home;
import com.hyperhomes.update.UpdateChecker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * /homes - List all your homes.
 * /homes help - Show help.
 * /homes share <home> <player> - Share a home.
 * /homes unshare <home> <player> - Unshare a home.
 * /homes shared - List homes shared with you.
 * /homes reload - Reload config (admin).
 */
public class HomesCommand extends AbstractPlayerCommand {

    // Colors
    private static final String COLOR_GOLD = "#FFAA00";
    private static final String COLOR_GREEN = "#55FF55";
    private static final String COLOR_RED = "#FF5555";
    private static final String COLOR_YELLOW = "#FFFF55";
    private static final String COLOR_GRAY = "#AAAAAA";
    private static final String COLOR_WHITE = "#FFFFFF";
    private static final String COLOR_AQUA = "#55FFFF";

    private final HyperHomes plugin;
    @Nullable
    private final GuiManager guiManager;

    public HomesCommand(@NotNull HyperHomes plugin) {
        this(plugin, null);
    }

    public HomesCommand(@NotNull HyperHomes plugin, @Nullable GuiManager guiManager) {
        super("homes", "List all your homes");
        this.plugin = plugin;
        this.guiManager = guiManager;

        // Add subcommands
        addSubCommand(new HelpSubCommand());
        addSubCommand(new ShareSubCommand(plugin));
        addSubCommand(new UnshareSubCommand(plugin));
        addSubCommand(new SharedSubCommand(plugin));
        addSubCommand(new ReloadSubCommand(plugin));
        addSubCommand(new UpdateSubCommand(plugin));

        // Add GUI subcommand if GuiManager is available
        if (guiManager != null) {
            addSubCommand(new GuiSubCommand(guiManager));
            addSubCommand(new AdminSubCommand(guiManager));
            addSubCommand(new PendingSubCommand(guiManager));
        }

        // Add debug command for diagnosing permission issues
        addSubCommand(new DebugPermsSubCommand());

        // Add migration command
        addSubCommand(new MigrateSubCommand(plugin));
    }

    @Override
    protected void execute(@NotNull CommandContext ctx,
                          @NotNull Store<EntityStore> store,
                          @NotNull Ref<EntityStore> ref,
                          @NotNull PlayerRef playerRef,
                          @NotNull World world) {

        UUID uuid = playerRef.getUuid();

        // Permission check
        if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.list")) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("You don't have permission to list homes.").color(COLOR_RED)));
            return;
        }

        // Check if user is trying to use shared home syntax (e.g., /homes player:home)
        String input = ctx.getInputString();
        if (input != null && !input.isEmpty()) {
            String[] parts = input.trim().split("\\s+");
            if (parts.length >= 2 && parts[1].contains(":")) {
                // User is trying to teleport to shared home via /homes
                ctx.sendMessage(prefix()
                    .insert(Message.raw("To teleport to a shared home, use: ").color(COLOR_YELLOW))
                    .insert(Message.raw("/home " + parts[1]).color(COLOR_GREEN)));
                return;
            }
        }

        // Check if GUI should be used by default
        if (guiManager != null && HyperHomesConfig.get().isGuiEnabled() &&
            HyperPermsIntegration.hasPermission(uuid, "hyperhomes.gui")) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                guiManager.openHomesList(player, ref, store, playerRef);
                return;
            }
        }

        HomeManager homeManager = plugin.getHomeManager();

        Collection<Home> homes = homeManager.getHomes(uuid);
        if (homes.isEmpty()) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("You don't have any homes set.").color(COLOR_YELLOW)));
            ctx.sendMessage(Message.raw("Use ").color(COLOR_GRAY)
                .insert(Message.raw("/sethome").color(COLOR_GREEN))
                .insert(Message.raw(" to create your first home!").color(COLOR_GRAY)));
            return;
        }

        int limit = homeManager.getHomeLimit(uuid);
        String limitStr = limit < 0 ? "∞" : String.valueOf(limit);

        // Header
        ctx.sendMessage(prefix()
            .insert(Message.raw("Your homes (").color(COLOR_YELLOW))
            .insert(Message.raw(String.valueOf(homes.size())).color(COLOR_WHITE))
            .insert(Message.raw("/").color(COLOR_GRAY))
            .insert(Message.raw(limitStr).color(COLOR_WHITE))
            .insert(Message.raw("):").color(COLOR_YELLOW)));

        List<Home> sortedHomes = homes.stream()
            .sorted(Comparator.comparing(Home::name))
            .toList();

        for (Home home : sortedHomes) {
            String coords = String.format("%.1f, %.1f, %.1f", home.x(), home.y(), home.z());
            Message msg = Message.raw(" - ").color(COLOR_GRAY)
                .insert(Message.raw(home.name()).color(COLOR_GREEN))
                .insert(Message.raw(" (").color(COLOR_GRAY))
                .insert(Message.raw(home.world()).color(COLOR_YELLOW))
                .insert(Message.raw(": ").color(COLOR_GRAY))
                .insert(Message.raw(coords).color(COLOR_WHITE))
                .insert(Message.raw(")").color(COLOR_GRAY));

            // Show if shared
            if (home.isShared()) {
                msg = msg.insert(Message.raw(" [shared]").color(COLOR_AQUA));
            }

            ctx.sendMessage(msg);
        }

        // Footer hint
        ctx.sendMessage(Message.raw("Tip: Use ").color(COLOR_GRAY)
            .insert(Message.raw("/homes help").color(COLOR_YELLOW))
            .insert(Message.raw(" for all commands.").color(COLOR_GRAY)));
    }

    private Message prefix() {
        return Message.raw("[").color(COLOR_GRAY)
            .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
            .insert(Message.raw("] ").color(COLOR_GRAY));
    }

    /**
     * /homes share <home> <player> - Share a home with another player.
     */
    private static class ShareSubCommand extends AbstractPlayerCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";

        private final HyperHomes plugin;

        public ShareSubCommand(@NotNull HyperHomes plugin) {
            super("share", "Share a home with another player");
            this.plugin = plugin;
            setAllowsExtraArguments(true);
        }

        @Override
        protected void execute(@NotNull CommandContext ctx,
                              @NotNull Store<EntityStore> store,
                              @NotNull Ref<EntityStore> ref,
                              @NotNull PlayerRef playerRef,
                              @NotNull World world) {

            UUID uuid = playerRef.getUuid();

            // Permission check
            if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.share")) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("You don't have permission to share homes.").color(COLOR_RED)));
                return;
            }

            // Parse args: /homes share <home> <player>
            String[] args = parseArgs(ctx, "share");
            if (args.length < 2) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Usage: ").color(COLOR_RED))
                    .insert(Message.raw("/homes share <home> <player>").color(COLOR_YELLOW)));
                return;
            }

            String homeName = args[0].toLowerCase();
            String targetName = args[1];

            HomeManager homeManager = plugin.getHomeManager();

            // Check if home exists
            Home home = homeManager.getHome(uuid, homeName);
            if (home == null) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Home '").color(COLOR_RED))
                    .insert(Message.raw(homeName).color(COLOR_YELLOW))
                    .insert(Message.raw("' not found!").color(COLOR_RED)));
                return;
            }

            // Find target player
            UUID targetUuid = homeManager.findPlayerByUsername(targetName);
            if (targetUuid == null) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Player '").color(COLOR_RED))
                    .insert(Message.raw(targetName).color(COLOR_YELLOW))
                    .insert(Message.raw("' not found or not online.").color(COLOR_RED)));
                return;
            }

            // Can't share with yourself
            if (targetUuid.equals(uuid)) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("You can't share a home with yourself!").color(COLOR_RED)));
                return;
            }

            // Share the home
            com.hyperhomes.util.Logger.info("[SHARE-DEBUG] ShareSubCommand: Calling shareHome(owner=%s, home='%s', target=%s)", uuid, homeName, targetUuid);
            boolean success = homeManager.shareHome(uuid, homeName, targetUuid);
            com.hyperhomes.util.Logger.info("[SHARE-DEBUG] ShareSubCommand: shareHome returned %s", success);
            if (success) {
                String targetUsername = homeManager.getUsername(targetUuid);
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Home '").color(COLOR_GREEN))
                    .insert(Message.raw(homeName).color(COLOR_YELLOW))
                    .insert(Message.raw("' shared with ").color(COLOR_GREEN))
                    .insert(Message.raw(targetUsername != null ? targetUsername : targetName).color(COLOR_YELLOW))
                    .insert(Message.raw("!").color(COLOR_GREEN)));
                ctx.sendMessage(Message.raw("  They can teleport with: ").color(COLOR_GRAY)
                    .insert(Message.raw("/home " + playerRef.getUsername() + ":" + homeName).color(COLOR_YELLOW)));
            } else {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Failed to share home.").color(COLOR_RED)));
            }
        }

        private String[] parseArgs(CommandContext ctx, String subcommand) {
            String input = ctx.getInputString();
            if (input == null || input.isEmpty()) return new String[0];
            String[] parts = input.trim().split("\\s+");
            // Skip "homes" and "share"
            if (parts.length <= 2) return new String[0];
            String[] args = new String[parts.length - 2];
            System.arraycopy(parts, 2, args, 0, args.length);
            return args;
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes unshare <home> <player> - Unshare a home from another player.
     */
    private static class UnshareSubCommand extends AbstractPlayerCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";

        private final HyperHomes plugin;

        public UnshareSubCommand(@NotNull HyperHomes plugin) {
            super("unshare", "Unshare a home from another player");
            this.plugin = plugin;
            setAllowsExtraArguments(true);
        }

        @Override
        protected void execute(@NotNull CommandContext ctx,
                              @NotNull Store<EntityStore> store,
                              @NotNull Ref<EntityStore> ref,
                              @NotNull PlayerRef playerRef,
                              @NotNull World world) {

            UUID uuid = playerRef.getUuid();

            // Permission check
            if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.share")) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("You don't have permission to manage shared homes.").color(COLOR_RED)));
                return;
            }

            // Parse args: /homes unshare <home> <player>
            String[] args = parseArgs(ctx, "unshare");
            if (args.length < 2) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Usage: ").color(COLOR_RED))
                    .insert(Message.raw("/homes unshare <home> <player>").color(COLOR_YELLOW)));
                return;
            }

            String homeName = args[0].toLowerCase();
            String targetName = args[1];

            HomeManager homeManager = plugin.getHomeManager();

            // Check if home exists
            Home home = homeManager.getHome(uuid, homeName);
            if (home == null) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Home '").color(COLOR_RED))
                    .insert(Message.raw(homeName).color(COLOR_YELLOW))
                    .insert(Message.raw("' not found!").color(COLOR_RED)));
                return;
            }

            // Find target player
            UUID targetUuid = homeManager.findPlayerByUsername(targetName);
            if (targetUuid == null) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Player '").color(COLOR_RED))
                    .insert(Message.raw(targetName).color(COLOR_YELLOW))
                    .insert(Message.raw("' not found.").color(COLOR_RED)));
                return;
            }

            // Unshare the home
            boolean success = homeManager.unshareHome(uuid, homeName, targetUuid);
            if (success) {
                String targetUsername = homeManager.getUsername(targetUuid);
                ctx.sendMessage(prefix()
                    .insert(Message.raw("Home '").color(COLOR_GREEN))
                    .insert(Message.raw(homeName).color(COLOR_YELLOW))
                    .insert(Message.raw("' is no longer shared with ").color(COLOR_GREEN))
                    .insert(Message.raw(targetUsername != null ? targetUsername : targetName).color(COLOR_YELLOW))
                    .insert(Message.raw(".").color(COLOR_GREEN)));
            } else {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("That home isn't shared with that player.").color(COLOR_RED)));
            }
        }

        private String[] parseArgs(CommandContext ctx, String subcommand) {
            String input = ctx.getInputString();
            if (input == null || input.isEmpty()) return new String[0];
            String[] parts = input.trim().split("\\s+");
            if (parts.length <= 2) return new String[0];
            String[] args = new String[parts.length - 2];
            System.arraycopy(parts, 2, args, 0, args.length);
            return args;
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes shared - List homes shared with you.
     */
    private static class SharedSubCommand extends AbstractPlayerCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";
        private static final String COLOR_WHITE = "#FFFFFF";
        private static final String COLOR_AQUA = "#55FFFF";

        private final HyperHomes plugin;

        public SharedSubCommand(@NotNull HyperHomes plugin) {
            super("shared", "List homes shared with you");
            this.plugin = plugin;
        }

        @Override
        protected void execute(@NotNull CommandContext ctx,
                              @NotNull Store<EntityStore> store,
                              @NotNull Ref<EntityStore> ref,
                              @NotNull PlayerRef playerRef,
                              @NotNull World world) {

            UUID uuid = playerRef.getUuid();

            // Permission check
            if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.use")) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("You don't have permission to use homes.").color(COLOR_RED)));
                return;
            }

            HomeManager homeManager = plugin.getHomeManager();
            Map<UUID, List<Home>> sharedHomes = homeManager.getHomesSharedWithPlayer(uuid);

            if (sharedHomes.isEmpty()) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("No homes are shared with you.").color(COLOR_YELLOW)));
                return;
            }

            // Count total
            int total = sharedHomes.values().stream().mapToInt(List::size).sum();

            ctx.sendMessage(prefix()
                .insert(Message.raw("Homes shared with you (").color(COLOR_AQUA))
                .insert(Message.raw(String.valueOf(total)).color(COLOR_WHITE))
                .insert(Message.raw("):").color(COLOR_AQUA)));

            for (Map.Entry<UUID, List<Home>> entry : sharedHomes.entrySet()) {
                UUID ownerUuid = entry.getKey();
                List<Home> homes = entry.getValue();
                String ownerName = homeManager.getUsername(ownerUuid);
                if (ownerName == null) ownerName = ownerUuid.toString().substring(0, 8);

                ctx.sendMessage(Message.raw("  From ").color(COLOR_GRAY)
                    .insert(Message.raw(ownerName).color(COLOR_YELLOW))
                    .insert(Message.raw(":").color(COLOR_GRAY)));

                for (Home home : homes) {
                    ctx.sendMessage(Message.raw("   - ").color(COLOR_GRAY)
                        .insert(Message.raw(home.name()).color(COLOR_GREEN))
                        .insert(Message.raw(" → ").color(COLOR_GRAY))
                        .insert(Message.raw("/home " + ownerName + ":" + home.name()).color(COLOR_WHITE)));
                }
            }
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes help - Shows all HyperHomes commands.
     */
    private static class HelpSubCommand extends AbstractCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";
        private static final String COLOR_WHITE = "#FFFFFF";
        private static final String COLOR_AQUA = "#55FFFF";

        public HelpSubCommand() {
            super("help", "Show all HyperHomes commands");
            addAliases("?");
        }

        @Override
        protected CompletableFuture<Void> execute(@NotNull CommandContext ctx) {
            // Header
            ctx.sender().sendMessage(
                Message.raw("=== HyperHomes Commands ===").color(COLOR_GOLD).bold(true)
            );

            ctx.sender().sendMessage(Message.empty());

            // Main commands
            ctx.sender().sendMessage(
                Message.raw("/home [name]").color(COLOR_GREEN)
                    .insert(Message.raw(" - Teleport to your home").color(COLOR_GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("/home <player>:<name>").color(COLOR_GREEN)
                    .insert(Message.raw(" - Teleport to a shared home").color(COLOR_GRAY))
            );

            ctx.sender().sendMessage(Message.empty());

            ctx.sender().sendMessage(
                Message.raw("/sethome [name]").color(COLOR_GREEN)
                    .insert(Message.raw(" - Set a home at your location").color(COLOR_GRAY))
            );

            ctx.sender().sendMessage(
                Message.raw("/delhome <name>").color(COLOR_GREEN)
                    .insert(Message.raw(" - Delete a home").color(COLOR_GRAY))
            );

            ctx.sender().sendMessage(
                Message.raw("/homes").color(COLOR_GREEN)
                    .insert(Message.raw(" - List all your homes").color(COLOR_GRAY))
            );

            ctx.sender().sendMessage(Message.empty());

            // Sharing commands
            ctx.sender().sendMessage(
                Message.raw("Sharing:").color(COLOR_AQUA)
            );
            ctx.sender().sendMessage(
                Message.raw("/homes share <home> <player>").color(COLOR_GREEN)
                    .insert(Message.raw(" - Share a home").color(COLOR_GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("/homes unshare <home> <player>").color(COLOR_GREEN)
                    .insert(Message.raw(" - Unshare a home").color(COLOR_GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("/homes shared").color(COLOR_GREEN)
                    .insert(Message.raw(" - List homes shared with you").color(COLOR_GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("/homes pending").color(COLOR_GREEN)
                    .insert(Message.raw(" - View pending share requests").color(COLOR_GRAY))
            );

            ctx.sender().sendMessage(Message.empty());

            // Admin commands
            ctx.sender().sendMessage(
                Message.raw("Admin:").color(COLOR_AQUA)
            );
            ctx.sender().sendMessage(
                Message.raw("/homes reload").color(COLOR_GREEN)
                    .insert(Message.raw(" - Reload configuration").color(COLOR_GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("/homes update").color(COLOR_GREEN)
                    .insert(Message.raw(" - Check for updates").color(COLOR_GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("/homes update download").color(COLOR_GREEN)
                    .insert(Message.raw(" - Download latest update").color(COLOR_GRAY))
            );
            ctx.sender().sendMessage(
                Message.raw("/homes migrate <source>").color(COLOR_GREEN)
                    .insert(Message.raw(" - Import homes from other plugins").color(COLOR_GRAY))
            );

            ctx.sender().sendMessage(Message.empty());

            // Aliases
            ctx.sender().sendMessage(
                Message.raw("Aliases: ").color(COLOR_YELLOW)
                    .insert(Message.raw("/h").color(COLOR_WHITE))
                    .insert(Message.raw(" = /home, ").color(COLOR_GRAY))
                    .insert(Message.raw("/sh").color(COLOR_WHITE))
                    .insert(Message.raw(" = /sethome").color(COLOR_GRAY))
            );

            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * /homes reload - Reload configuration (admin command).
     */
    private static class ReloadSubCommand extends AbstractCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_GRAY = "#AAAAAA";

        private final HyperHomes plugin;

        public ReloadSubCommand(@NotNull HyperHomes plugin) {
            super("reload", "Reload HyperHomes configuration");
            this.plugin = plugin;
        }

        @Override
        protected CompletableFuture<Void> execute(@NotNull CommandContext ctx) {
            // Permission check - try to get UUID from sender
            try {
                var method = ctx.sender().getClass().getMethod("getUuid");
                Object result = method.invoke(ctx.sender());
                if (result instanceof java.util.UUID uuid) {
                    if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.admin.reload")) {
                        ctx.sender().sendMessage(prefix()
                            .insert(Message.raw("You don't have permission to reload the config.").color("#FF5555")));
                        return CompletableFuture.completedFuture(null);
                    }
                }
            } catch (Exception ignored) {
                // If we can't get UUID, allow console to reload
            }

            plugin.reloadConfig();

            ctx.sender().sendMessage(prefix()
                .insert(Message.raw("Configuration reloaded!").color(COLOR_GREEN)));

            // Show current settings
            HyperHomesConfig config = HyperHomesConfig.get();
            ctx.sender().sendMessage(
                Message.raw("  Warmup: ").color(COLOR_GRAY)
                    .insert(Message.raw(config.getWarmupSeconds() + "s").color("#FFFFFF"))
                    .insert(Message.raw(" | Cooldown: ").color(COLOR_GRAY))
                    .insert(Message.raw(config.getCooldownSeconds() + "s").color("#FFFFFF"))
                    .insert(Message.raw(" | Default limit: ").color(COLOR_GRAY))
                    .insert(Message.raw(String.valueOf(config.getDefaultHomeLimit())).color("#FFFFFF"))
            );

            return CompletableFuture.completedFuture(null);
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes update - Check for updates and optionally download (admin command).
     */
    private static class UpdateSubCommand extends AbstractCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";
        private static final String COLOR_WHITE = "#FFFFFF";
        private static final String COLOR_AQUA = "#55FFFF";

        private final HyperHomes plugin;

        public UpdateSubCommand(@NotNull HyperHomes plugin) {
            super("update", "Check for HyperHomes updates");
            this.plugin = plugin;
            setAllowsExtraArguments(true);
        }

        @Override
        protected CompletableFuture<Void> execute(@NotNull CommandContext ctx) {
            // Permission check - try to get UUID from sender
            try {
                var method = ctx.sender().getClass().getMethod("getUuid");
                Object result = method.invoke(ctx.sender());
                if (result instanceof java.util.UUID uuid) {
                    if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.admin.update")) {
                        ctx.sender().sendMessage(prefix()
                            .insert(Message.raw("You don't have permission to check for updates.").color(COLOR_RED)));
                        return CompletableFuture.completedFuture(null);
                    }
                }
            } catch (Exception ignored) {
                // If we can't get UUID, allow console to check updates
            }

            UpdateChecker updateChecker = plugin.getUpdateChecker();
            if (updateChecker == null) {
                ctx.sender().sendMessage(prefix()
                    .insert(Message.raw("Update checking is disabled in config.").color(COLOR_YELLOW)));
                return CompletableFuture.completedFuture(null);
            }

            // Check for "download" argument
            String input = ctx.getInputString();
            boolean shouldDownload = input != null && input.toLowerCase().contains("download");

            ctx.sender().sendMessage(prefix()
                .insert(Message.raw("Checking for updates...").color(COLOR_YELLOW)));

            updateChecker.checkForUpdates(true).thenAccept(info -> {
                if (info == null) {
                    ctx.sender().sendMessage(prefix()
                        .insert(Message.raw("You are running the latest version (v").color(COLOR_GREEN))
                        .insert(Message.raw(HyperHomes.VERSION).color(COLOR_WHITE))
                        .insert(Message.raw(")!").color(COLOR_GREEN)));
                    return;
                }

                // Update available
                ctx.sender().sendMessage(Message.empty());
                ctx.sender().sendMessage(Message.raw("=== Update Available ===").color(COLOR_GOLD).bold(true));
                ctx.sender().sendMessage(
                    Message.raw("  Current: ").color(COLOR_GRAY)
                        .insert(Message.raw("v" + HyperHomes.VERSION).color(COLOR_RED))
                );
                ctx.sender().sendMessage(
                    Message.raw("  Latest:  ").color(COLOR_GRAY)
                        .insert(Message.raw("v" + info.version()).color(COLOR_GREEN))
                );

                // Show changelog if available
                if (info.changelog() != null && !info.changelog().isEmpty()) {
                    ctx.sender().sendMessage(Message.empty());
                    ctx.sender().sendMessage(Message.raw("Changelog:").color(COLOR_AQUA));
                    // Truncate long changelogs
                    String changelog = info.changelog();
                    if (changelog.length() > 300) {
                        changelog = changelog.substring(0, 297) + "...";
                    }
                    // Split by newlines and send each line
                    for (String line : changelog.split("\n")) {
                        if (!line.trim().isEmpty()) {
                            ctx.sender().sendMessage(Message.raw("  " + line.trim()).color(COLOR_GRAY));
                        }
                    }
                }

                ctx.sender().sendMessage(Message.empty());

                if (shouldDownload && info.downloadUrl() != null) {
                    // Download the update
                    ctx.sender().sendMessage(prefix()
                        .insert(Message.raw("Downloading update...").color(COLOR_YELLOW)));

                    updateChecker.downloadUpdate(info).thenAccept(path -> {
                        if (path != null) {
                            ctx.sender().sendMessage(prefix()
                                .insert(Message.raw("Update downloaded! ").color(COLOR_GREEN))
                                .insert(Message.raw("Restart the server to apply.").color(COLOR_YELLOW)));
                            ctx.sender().sendMessage(
                                Message.raw("  File: ").color(COLOR_GRAY)
                                    .insert(Message.raw(path.getFileName().toString()).color(COLOR_WHITE))
                            );
                        } else {
                            ctx.sender().sendMessage(prefix()
                                .insert(Message.raw("Failed to download update. Check console for details.").color(COLOR_RED)));
                        }
                    });
                } else if (info.downloadUrl() != null) {
                    ctx.sender().sendMessage(
                        Message.raw("Run ").color(COLOR_GRAY)
                            .insert(Message.raw("/homes update download").color(COLOR_YELLOW))
                            .insert(Message.raw(" to download the update.").color(COLOR_GRAY))
                    );
                } else {
                    ctx.sender().sendMessage(
                        Message.raw("No download URL available. Please update manually.").color(COLOR_YELLOW)
                    );
                }
            });

            return CompletableFuture.completedFuture(null);
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes gui - Open the HyperHomes GUI.
     */
    private static class GuiSubCommand extends AbstractPlayerCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_GRAY = "#AAAAAA";

        private final GuiManager guiManager;

        public GuiSubCommand(@NotNull GuiManager guiManager) {
            super("gui", "Open the HyperHomes GUI");
            this.guiManager = guiManager;
        }

        @Override
        protected void execute(@NotNull CommandContext ctx,
                              @NotNull Store<EntityStore> store,
                              @NotNull Ref<EntityStore> ref,
                              @NotNull PlayerRef playerRef,
                              @NotNull World world) {

            UUID uuid = playerRef.getUuid();

            // Permission check
            if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.gui")) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("You don't have permission to use the GUI.").color(COLOR_RED)));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                guiManager.openHomesList(player, ref, store, playerRef);
            }
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes admin - Open the HyperHomes Admin panel.
     */
    private static class AdminSubCommand extends AbstractPlayerCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_GRAY = "#AAAAAA";

        private final GuiManager guiManager;

        public AdminSubCommand(@NotNull GuiManager guiManager) {
            super("admin", "Open the HyperHomes Admin panel");
            this.guiManager = guiManager;
        }

        @Override
        protected void execute(@NotNull CommandContext ctx,
                              @NotNull Store<EntityStore> store,
                              @NotNull Ref<EntityStore> ref,
                              @NotNull PlayerRef playerRef,
                              @NotNull World world) {

            UUID uuid = playerRef.getUuid();

            // Permission check
            if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.admin")) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("You don't have permission to access admin panel.").color(COLOR_RED)));
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                guiManager.openAdminMain(player, ref, store, playerRef);
            }
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes pending - View and accept pending share requests.
     */
    private static class PendingSubCommand extends AbstractPlayerCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";

        private final GuiManager guiManager;

        public PendingSubCommand(@NotNull GuiManager guiManager) {
            super("pending", "View pending share requests");
            this.guiManager = guiManager;
        }

        @Override
        protected void execute(@NotNull CommandContext ctx,
                              @NotNull Store<EntityStore> store,
                              @NotNull Ref<EntityStore> ref,
                              @NotNull PlayerRef playerRef,
                              @NotNull World world) {

            UUID uuid = playerRef.getUuid();

            // Check if there's a pending request
            var pendingManager = guiManager.getPendingShareManager().get();
            if (!pendingManager.hasPendingRequest(uuid)) {
                ctx.sendMessage(prefix()
                    .insert(Message.raw("You don't have any pending share requests.").color(COLOR_YELLOW)));
                return;
            }

            // Open the share confirmation UI
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                guiManager.openShareConfirm(player, ref, store, playerRef);
            }
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }

    /**
     * /homes debugperms - Debug HyperPerms integration.
     * This helps diagnose permission issues between HyperHomes and HyperPerms.
     */
    private static class DebugPermsSubCommand extends AbstractPlayerCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";
        private static final String COLOR_WHITE = "#FFFFFF";
        private static final String COLOR_AQUA = "#55FFFF";

        public DebugPermsSubCommand() {
            super("debugperms", "Debug HyperPerms integration");
            addAliases("dp");
        }

        @Override
        protected void execute(@NotNull CommandContext ctx,
                              @NotNull Store<EntityStore> store,
                              @NotNull Ref<EntityStore> ref,
                              @NotNull PlayerRef playerRef,
                              @NotNull World world) {

            UUID uuid = playerRef.getUuid();

            ctx.sendMessage(Message.raw("=== HyperHomes Permission Debug ===").color(COLOR_GOLD).bold(true));
            ctx.sendMessage(Message.empty());

            // Show integration status
            ctx.sendMessage(Message.raw("Integration Status:").color(COLOR_AQUA));
            ctx.sendMessage(Message.raw("  Available: ").color(COLOR_GRAY)
                .insert(Message.raw(String.valueOf(HyperPermsIntegration.isAvailable()))
                    .color(HyperPermsIntegration.isAvailable() ? COLOR_GREEN : COLOR_RED)));

            String initError = HyperPermsIntegration.getInitError();
            if (initError != null) {
                ctx.sendMessage(Message.raw("  Init Error: ").color(COLOR_GRAY)
                    .insert(Message.raw(initError).color(COLOR_RED)));
            }

            ctx.sendMessage(Message.empty());

            // Test key permissions
            ctx.sendMessage(Message.raw("Permission Tests for ").color(COLOR_AQUA)
                .insert(Message.raw(playerRef.getUsername()).color(COLOR_YELLOW))
                .insert(Message.raw(":").color(COLOR_AQUA)));

            String[] permsToTest = {
                "hyperhomes.use",
                "hyperhomes.gui",
                "hyperhomes.list",
                "hyperhomes.set",
                "hyperhomes.*"
            };

            for (String perm : permsToTest) {
                boolean hasPerm = HyperPermsIntegration.hasPermission(uuid, perm);
                ctx.sendMessage(Message.raw("  " + perm + ": ").color(COLOR_GRAY)
                    .insert(Message.raw(hasPerm ? "ALLOWED" : "DENIED")
                        .color(hasPerm ? COLOR_GREEN : COLOR_RED)));
            }

            ctx.sendMessage(Message.empty());

            // Print detailed status to console
            ctx.sendMessage(Message.raw("Detailed status printed to server console.").color(COLOR_YELLOW));
            com.hyperhomes.util.Logger.info(HyperPermsIntegration.getDetailedStatus());
            com.hyperhomes.util.Logger.info(HyperPermsIntegration.testPermission(uuid, "hyperhomes.gui"));
        }
    }

    /**
     * /homes migrate <source> [policy] - Migrate homes from other plugins.
     * Sources: json, yaml, sqlite
     * Policies: skip (default), rename, overwrite
     */
    private static class MigrateSubCommand extends AbstractCommand {

        private static final String COLOR_GOLD = "#FFAA00";
        private static final String COLOR_GREEN = "#55FF55";
        private static final String COLOR_RED = "#FF5555";
        private static final String COLOR_YELLOW = "#FFFF55";
        private static final String COLOR_GRAY = "#AAAAAA";
        private static final String COLOR_WHITE = "#FFFFFF";
        private static final String COLOR_AQUA = "#55FFFF";

        private final HyperHomes plugin;

        public MigrateSubCommand(@NotNull HyperHomes plugin) {
            super("migrate", "Migrate homes from other plugins");
            this.plugin = plugin;
            setAllowsExtraArguments(true);
        }

        @Override
        protected CompletableFuture<Void> execute(@NotNull CommandContext ctx) {
            // Permission check - try to get UUID from sender
            try {
                var method = ctx.sender().getClass().getMethod("getUuid");
                Object result = method.invoke(ctx.sender());
                if (result instanceof java.util.UUID uuid) {
                    if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.admin.migrate")) {
                        ctx.sender().sendMessage(prefix()
                            .insert(Message.raw("You don't have permission to migrate data.").color(COLOR_RED)));
                        return CompletableFuture.completedFuture(null);
                    }
                }
            } catch (Exception ignored) {
                // Allow console to migrate
            }

            MigrationManager migrationManager = plugin.getMigrationManager();

            // Parse arguments
            String input = ctx.getInputString();
            String[] args = parseArgs(input);

            // No args - show help
            if (args.length == 0) {
                showMigrationHelp(ctx.sender(), migrationManager);
                return CompletableFuture.completedFuture(null);
            }

            String sourceId = args[0].toLowerCase();

            // Special command: list sources
            if (sourceId.equals("list")) {
                showMigrationSources(ctx.sender(), migrationManager);
                return CompletableFuture.completedFuture(null);
            }

            // Get the source
            MigrationSource source = migrationManager.getSource(sourceId);
            if (source == null) {
                ctx.sender().sendMessage(prefix()
                    .insert(Message.raw("Unknown migration source: ").color(COLOR_RED))
                    .insert(Message.raw(sourceId).color(COLOR_YELLOW)));
                ctx.sender().sendMessage(Message.raw("Use ").color(COLOR_GRAY)
                    .insert(Message.raw("/homes migrate list").color(COLOR_YELLOW))
                    .insert(Message.raw(" to see available sources.").color(COLOR_GRAY)));
                return CompletableFuture.completedFuture(null);
            }

            // Parse duplicate policy
            MigrationManager.DuplicatePolicy policy = MigrationManager.DuplicatePolicy.SKIP;
            if (args.length > 1) {
                try {
                    policy = MigrationManager.DuplicatePolicy.valueOf(args[1].toUpperCase());
                } catch (IllegalArgumentException e) {
                    ctx.sender().sendMessage(prefix()
                        .insert(Message.raw("Invalid policy. Use: skip, rename, or overwrite").color(COLOR_RED)));
                    return CompletableFuture.completedFuture(null);
                }
            }

            // Confirm migration
            ctx.sender().sendMessage(prefix()
                .insert(Message.raw("Starting migration from ").color(COLOR_YELLOW))
                .insert(Message.raw(source.getDisplayName()).color(COLOR_WHITE))
                .insert(Message.raw("...").color(COLOR_YELLOW)));
            ctx.sender().sendMessage(Message.raw("  Duplicate policy: ").color(COLOR_GRAY)
                .insert(Message.raw(policy.name().toLowerCase()).color(COLOR_AQUA)));
            ctx.sender().sendMessage(Message.raw("  Source folder: ").color(COLOR_GRAY)
                .insert(Message.raw("config/hyperhomes/migration/").color(COLOR_WHITE)));

            // Perform migration
            final MigrationManager.DuplicatePolicy finalPolicy = policy;
            migrationManager.migrate(sourceId, finalPolicy, progress -> {
                ctx.sender().sendMessage(Message.raw("  " + progress).color(COLOR_GRAY));
            }).thenAccept(result -> {
                showMigrationResult(ctx.sender(), result);
            }).exceptionally(e -> {
                ctx.sender().sendMessage(prefix()
                    .insert(Message.raw("Migration failed: ").color(COLOR_RED))
                    .insert(Message.raw(e.getMessage()).color(COLOR_YELLOW)));
                return null;
            });

            return CompletableFuture.completedFuture(null);
        }

        private void showMigrationHelp(com.hypixel.hytale.server.core.command.system.CommandSender sender,
                                       MigrationManager migrationManager) {
            sender.sendMessage(Message.raw("=== HyperHomes Migration ===").color(COLOR_GOLD).bold(true));
            sender.sendMessage(Message.empty());
            sender.sendMessage(Message.raw("Usage: ").color(COLOR_GRAY)
                .insert(Message.raw("/homes migrate <source> [policy]").color(COLOR_YELLOW)));
            sender.sendMessage(Message.empty());
            sender.sendMessage(Message.raw("Sources:").color(COLOR_AQUA));
            for (MigrationSource source : migrationManager.getSources()) {
                sender.sendMessage(Message.raw("  - ").color(COLOR_GRAY)
                    .insert(Message.raw(source.getId()).color(COLOR_GREEN))
                    .insert(Message.raw(" - " + source.getDisplayName()).color(COLOR_GRAY)));
            }
            sender.sendMessage(Message.empty());
            sender.sendMessage(Message.raw("Policies:").color(COLOR_AQUA));
            sender.sendMessage(Message.raw("  - ").color(COLOR_GRAY)
                .insert(Message.raw("skip").color(COLOR_GREEN))
                .insert(Message.raw(" - Skip duplicates (default)").color(COLOR_GRAY)));
            sender.sendMessage(Message.raw("  - ").color(COLOR_GRAY)
                .insert(Message.raw("rename").color(COLOR_GREEN))
                .insert(Message.raw(" - Rename imported homes").color(COLOR_GRAY)));
            sender.sendMessage(Message.raw("  - ").color(COLOR_GRAY)
                .insert(Message.raw("overwrite").color(COLOR_GREEN))
                .insert(Message.raw(" - Overwrite existing homes").color(COLOR_GRAY)));
            sender.sendMessage(Message.empty());
            sender.sendMessage(Message.raw("Example: ").color(COLOR_GRAY)
                .insert(Message.raw("/homes migrate json rename").color(COLOR_YELLOW)));
            sender.sendMessage(Message.empty());
            sender.sendMessage(Message.raw("Place source files in: ").color(COLOR_GRAY)
                .insert(Message.raw("config/hyperhomes/migration/").color(COLOR_WHITE)));
        }

        private void showMigrationSources(com.hypixel.hytale.server.core.command.system.CommandSender sender,
                                          MigrationManager migrationManager) {
            sender.sendMessage(Message.raw("=== Available Migration Sources ===").color(COLOR_GOLD).bold(true));
            sender.sendMessage(Message.empty());

            for (MigrationSource source : migrationManager.getSources()) {
                sender.sendMessage(Message.raw(source.getId()).color(COLOR_GREEN).bold(true)
                    .insert(Message.raw(" - " + source.getDisplayName()).color(COLOR_WHITE)));
                sender.sendMessage(Message.raw("  Extensions: ").color(COLOR_GRAY)
                    .insert(Message.raw(String.join(", ", source.getSupportedExtensions())).color(COLOR_YELLOW)));
                sender.sendMessage(Message.empty());
            }
        }

        private void showMigrationResult(com.hypixel.hytale.server.core.command.system.CommandSender sender,
                                         MigrationResult result) {
            sender.sendMessage(Message.empty());

            if (result.isSuccess()) {
                sender.sendMessage(prefix()
                    .insert(Message.raw("Migration completed successfully!").color(COLOR_GREEN)));
            } else {
                sender.sendMessage(prefix()
                    .insert(Message.raw("Migration completed with errors.").color(COLOR_RED)));
            }

            sender.sendMessage(Message.empty());
            sender.sendMessage(Message.raw("=== Migration Results ===").color(COLOR_GOLD));
            sender.sendMessage(Message.raw("  Players processed: ").color(COLOR_GRAY)
                .insert(Message.raw(String.valueOf(result.playersProcessed())).color(COLOR_WHITE)));
            sender.sendMessage(Message.raw("  Homes imported: ").color(COLOR_GRAY)
                .insert(Message.raw(String.valueOf(result.homesImported())).color(COLOR_GREEN)));
            sender.sendMessage(Message.raw("  Homes skipped: ").color(COLOR_GRAY)
                .insert(Message.raw(String.valueOf(result.homesSkipped())).color(COLOR_YELLOW)));
            sender.sendMessage(Message.raw("  Homes failed: ").color(COLOR_GRAY)
                .insert(Message.raw(String.valueOf(result.homesFailed())).color(COLOR_RED)));
            sender.sendMessage(Message.raw("  Duration: ").color(COLOR_GRAY)
                .insert(Message.raw(result.durationMs() + "ms").color(COLOR_WHITE)));

            // Show warnings
            if (result.hasWarnings()) {
                sender.sendMessage(Message.empty());
                sender.sendMessage(Message.raw("Warnings:").color(COLOR_YELLOW));
                int shown = 0;
                for (String warning : result.warnings()) {
                    if (shown >= 5) {
                        sender.sendMessage(Message.raw("  ... and " + (result.warnings().size() - 5) + " more")
                            .color(COLOR_GRAY));
                        break;
                    }
                    sender.sendMessage(Message.raw("  - " + warning).color(COLOR_GRAY));
                    shown++;
                }
            }

            // Show errors
            if (!result.errors().isEmpty()) {
                sender.sendMessage(Message.empty());
                sender.sendMessage(Message.raw("Errors:").color(COLOR_RED));
                int shown = 0;
                for (String error : result.errors()) {
                    if (shown >= 5) {
                        sender.sendMessage(Message.raw("  ... and " + (result.errors().size() - 5) + " more")
                            .color(COLOR_GRAY));
                        break;
                    }
                    sender.sendMessage(Message.raw("  - " + error).color(COLOR_GRAY));
                    shown++;
                }
            }
        }

        private String[] parseArgs(String input) {
            if (input == null || input.isEmpty()) return new String[0];
            String[] parts = input.trim().split("\\s+");
            // Skip "homes" and "migrate"
            if (parts.length <= 2) return new String[0];
            String[] args = new String[parts.length - 2];
            System.arraycopy(parts, 2, args, 0, args.length);
            return args;
        }

        private Message prefix() {
            return Message.raw("[").color(COLOR_GRAY)
                .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
                .insert(Message.raw("] ").color(COLOR_GRAY));
        }
    }
}
