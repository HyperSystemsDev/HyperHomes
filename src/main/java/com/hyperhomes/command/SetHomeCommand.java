package com.hyperhomes.command;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.integration.HyperPermsIntegration;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.model.Home;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * /sethome [name] - Set a home at your current location.
 *
 * Usage:
 *   /sethome       - Set default "home"
 *   /sethome base  - Set home named "base"
 */
public class SetHomeCommand extends AbstractPlayerCommand {

    private static final Pattern VALID_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,32}$");

    // Colors
    private static final String COLOR_GOLD = "#FFAA00";
    private static final String COLOR_GREEN = "#55FF55";
    private static final String COLOR_RED = "#FF5555";
    private static final String COLOR_YELLOW = "#FFFF55";
    private static final String COLOR_GRAY = "#AAAAAA";
    private static final String COLOR_WHITE = "#FFFFFF";

    private final HyperHomes plugin;

    public SetHomeCommand(@NotNull HyperHomes plugin) {
        super("sethome", "Set a home at your current location");
        this.plugin = plugin;

        addAliases("sh", "createhome");
        setAllowsExtraArguments(true); // Allow positional args
    }

    /**
     * Parse home name from command input.
     * /sethome → "home"
     * /sethome base → "base"
     */
    private String parseHomeName(CommandContext ctx) {
        String input = ctx.getInputString();
        if (input == null || input.isEmpty()) {
            return "home";
        }

        String[] parts = input.trim().split("\\s+");
        // parts[0] is "sethome" or "sh"
        if (parts.length > 1) {
            return parts[1].toLowerCase();
        }
        return "home";
    }

    @Override
    protected void execute(@NotNull CommandContext ctx,
                          @NotNull Store<EntityStore> store,
                          @NotNull Ref<EntityStore> ref,
                          @NotNull PlayerRef playerRef,
                          @NotNull World world) {

        UUID uuid = playerRef.getUuid();

        // Permission check
        if (!HyperPermsIntegration.hasPermission(uuid, "hyperhomes.set")) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("You don't have permission to set homes.").color(COLOR_RED)));
            return;
        }

        String homeName = parseHomeName(ctx);

        HomeManager homeManager = plugin.getHomeManager();

        // Validate home name
        if (!VALID_NAME.matcher(homeName).matches()) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("Invalid home name!").color(COLOR_RED)));
            ctx.sendMessage(Message.raw("  Use only letters, numbers, underscores, and dashes (1-32 chars).").color(COLOR_GRAY));
            return;
        }

        // Get transform component using Store and Ref
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("Could not determine your location.").color(COLOR_RED)));
            return;
        }

        // Get head rotation for yaw/pitch (optional)
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);

        var pos = transform.getPosition();

        // Check if this would be an update or new home
        boolean isUpdate = homeManager.getHome(uuid, homeName) != null;
        int currentCount = homeManager.getHomes(uuid).size();
        int limit = homeManager.getHomeLimit(uuid);

        // Create home
        Home home = Home.create(
            homeName,
            world.getName(),
            pos.getX(), pos.getY(), pos.getZ(),
            rotation.getYaw(),
            rotation.getPitch()
        );

        // Try to set home (checks limit)
        boolean success = homeManager.setHome(uuid, home);
        if (!success) {
            String limitStr = limit < 0 ? "unlimited" : String.valueOf(limit);
            ctx.sendMessage(prefix()
                .insert(Message.raw("Home limit reached!").color(COLOR_RED)));
            ctx.sendMessage(Message.raw("  You have ").color(COLOR_GRAY)
                .insert(Message.raw(String.valueOf(currentCount)).color(COLOR_WHITE))
                .insert(Message.raw("/").color(COLOR_GRAY))
                .insert(Message.raw(limitStr).color(COLOR_WHITE))
                .insert(Message.raw(" homes.").color(COLOR_GRAY)));
            ctx.sendMessage(Message.raw("  Use ").color(COLOR_GRAY)
                .insert(Message.raw("/delhome <name>").color(COLOR_YELLOW))
                .insert(Message.raw(" to remove a home first.").color(COLOR_GRAY)));
            return;
        }

        // Success message
        String coords = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());

        if (isUpdate) {
            ctx.sendMessage(prefix()
                .insert(Message.raw("Home '").color(COLOR_GREEN))
                .insert(Message.raw(homeName).color(COLOR_YELLOW))
                .insert(Message.raw("' updated!").color(COLOR_GREEN)));
        } else {
            ctx.sendMessage(prefix()
                .insert(Message.raw("Home '").color(COLOR_GREEN))
                .insert(Message.raw(homeName).color(COLOR_YELLOW))
                .insert(Message.raw("' created!").color(COLOR_GREEN)));
        }

        ctx.sendMessage(Message.raw("  Location: ").color(COLOR_GRAY)
            .insert(Message.raw(coords).color(COLOR_WHITE))
            .insert(Message.raw(" in ").color(COLOR_GRAY))
            .insert(Message.raw(world.getName()).color(COLOR_YELLOW)));
    }

    private Message prefix() {
        return Message.raw("[").color(COLOR_GRAY)
            .insert(Message.raw("HyperHomes").color(COLOR_GOLD))
            .insert(Message.raw("] ").color(COLOR_GRAY));
    }
}
