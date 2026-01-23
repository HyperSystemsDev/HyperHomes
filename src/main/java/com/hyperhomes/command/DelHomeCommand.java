package com.hyperhomes.command;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.integration.HyperPermsIntegration;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.model.Home;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

/**
 * /delhome <name> - Delete a home.
 *
 * Usage:
 *   /delhome base  - Delete home named "base"
 *   /delhome       - Shows error with list of homes
 */
public class DelHomeCommand extends AbstractPlayerCommand {

    // Colors
    private static final String COLOR_GOLD = "#FFAA00";
    private static final String COLOR_GREEN = "#55FF55";
    private static final String COLOR_RED = "#FF5555";
    private static final String COLOR_YELLOW = "#FFFF55";
    private static final String COLOR_GRAY = "#AAAAAA";

    private final HyperHomes plugin;

    public DelHomeCommand(@NotNull HyperHomes plugin) {
        super("delhome", "Delete a home");
        this.plugin = plugin;

        addAliases("deletehome", "removehome", "rmhome");
        setAllowsExtraArguments(true); // Allow positional args
    }

    /**
     * Parse home name from command input.
     * /delhome → null (error)
     * /delhome base → "base"
     */
    private String parseHomeName(CommandContext ctx) {
        String input = ctx.getInputString();
        if (input == null || input.isEmpty()) {
            return null;
        }

        String[] parts = input.trim().split("\\s+");
        // parts[0] is "delhome" or alias
        if (parts.length > 1) {
            return parts[1].toLowerCase();
        }
        return null;
    }

    @Override
    protected void execute(@NotNull CommandContext ctx,
                          @NotNull Store<EntityStore> store,
                          @NotNull Ref<EntityStore> ref,
                          @NotNull PlayerRef playerRef,
                          @NotNull World world) {

        UUID uuid = playerRef.getUuid();

        // Permission check
        if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.delete")) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("You don't have permission to delete homes.").color(COLOR_RED)));
            return;
        }

        String homeName = parseHomeName(ctx);
        HomeManager homeManager = plugin.getHomeManager();

        // Check if name was provided
        if (homeName == null || homeName.isEmpty()) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("Please specify a home name.").color(COLOR_RED)));
            ctx.sendMessage(Message.raw("Usage: ").color(COLOR_GRAY)
                .insert(Message.raw("/delhome <name>").color(COLOR_YELLOW)));

            // Show available homes
            Collection<Home> homes = homeManager.getHomes(uuid);
            if (!homes.isEmpty()) {
                String homeList = String.join(", ", homes.stream().map(Home::name).sorted().toList());
                ctx.sendMessage(Message.raw("Your homes: ").color(COLOR_GRAY)
                    .insert(Message.raw(homeList).color(COLOR_GREEN)));
            }
            return;
        }

        // Try to delete home
        boolean deleted = homeManager.deleteHome(uuid, homeName);
        if (!deleted) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("Home '").color(COLOR_RED))
                .insert(Message.raw(homeName).color(COLOR_YELLOW))
                .insert(Message.raw("' not found!").color(COLOR_RED)));

            // Show available homes
            Collection<Home> homes = homeManager.getHomes(uuid);
            if (!homes.isEmpty()) {
                String homeList = String.join(", ", homes.stream().map(Home::name).sorted().toList());
                ctx.sendMessage(Message.raw("Your homes: ").color(COLOR_GRAY)
                    .insert(Message.raw(homeList).color(COLOR_GREEN)));
            }
            return;
        }

        ctx.sendMessage(prefix()
            .insert(Message.raw("Home '").color(COLOR_GREEN))
            .insert(Message.raw(homeName).color(COLOR_YELLOW))
            .insert(Message.raw("' has been deleted!").color(COLOR_GREEN)));
    }

    private Message prefix() {
        return Message.raw("[").color(COLOR_GRAY)
            .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
            .insert(Message.raw("] ").color(COLOR_GRAY));
    }
}
