package com.hyperhomes.gui.page;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.gui.UIHelper;
import com.hyperhomes.gui.data.HomeDetailData;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.TeleportManager;
import com.hyperhomes.model.Home;
import com.hyperhomes.model.Location;
import com.hyperhomes.platform.HyperHomesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Home Detail page - displays detailed info about a specific home.
 * Allows teleporting, sharing, and deleting.
 */
public class HomeDetailPage extends InteractiveCustomUIPage<HomeDetailData> {

    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final GuiManager guiManager;
    private final Home home;

    public HomeDetailPage(PlayerRef playerRef, HomeManager homeManager,
                          TeleportManager teleportManager, GuiManager guiManager,
                          Home home) {
        super(playerRef, CustomPageLifetime.CanDismiss, HomeDetailData.CODEC);
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.guiManager = guiManager;
        this.home = home;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {

        // Load the main template
        cmd.append("HyperHomes/home_detail.ui");

        // Set home info
        cmd.set("#HomeName.Text", home.name());
        cmd.set("#WorldName.Text", UIHelper.formatWorldName(home.world()));
        cmd.set("#Coords.Text", UIHelper.formatCoordsDetailed(home));
        cmd.set("#LastUsed.Text", UIHelper.formatRelativeTime(home.lastUsed()));

        // Show share info if home is shared (append the group dynamically instead of using Visible)
        if (home.isShared()) {
            cmd.append("#ShareInfoContainer", "HyperHomes/share_info.ui");
            int sharedCount = home.sharedWith().size();
            cmd.set("#ShareInfoContainer #SharedCount.Text", sharedCount + " player" + (sharedCount != 1 ? "s" : ""));

            // Bind manage share button
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ShareInfoContainer #ManageShareBtn",
                    EventData.of("Button", "ManageShare")
                            .append("HomeName", home.name()),
                    false
            );
        }

        // Bind action buttons
        com.hyperhomes.util.Logger.info("[GUI-DEBUG] Binding #TeleportBtn for home: %s", home.name());
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TeleportBtn",
                EventData.of("Button", "Teleport")
                        .append("HomeName", home.name()),
                false
        );

        com.hyperhomes.util.Logger.info("[GUI-DEBUG] Binding #ShareBtn for home: %s", home.name());
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ShareBtn",
                EventData.of("Button", "Share")
                        .append("HomeName", home.name()),
                false
        );

        com.hyperhomes.util.Logger.info("[GUI-DEBUG] Binding #DeleteBtn for home: %s", home.name());
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DeleteBtn",
                EventData.of("Button", "Delete")
                        .append("HomeName", home.name()),
                false
        );

        com.hyperhomes.util.Logger.info("[GUI-DEBUG] All HomeDetailPage bindings complete");
        // Note: $C.@BackButton {} handles back/close functionality automatically
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                HomeDetailData data) {
        super.handleDataEvent(ref, store, data);

        // Log ALL incoming events
        com.hyperhomes.util.Logger.info("[GUI-DEBUG] HomeDetailPage.handleDataEvent called - button: %s, homeName: %s",
                data.button, data.homeName);

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            com.hyperhomes.util.Logger.warn("[GUI-DEBUG] player or playerRef is null!");
            return;
        }

        if (data.button == null) {
            com.hyperhomes.util.Logger.info("[GUI-DEBUG] data.button is null, ignoring event");
            return;
        }

        UUID uuid = playerRef.getUuid();

        com.hyperhomes.util.Logger.info("[GUI-DEBUG] Processing button: %s for player %s", data.button, uuid);

        switch (data.button) {
            case "Close" -> guiManager.closePage(player, ref, store);

            case "Back" -> guiManager.openHomesList(player, ref, store, playerRef);

            case "Teleport" -> {
                com.hyperhomes.util.Logger.info("[GUI-DEBUG] Teleport button clicked for home: %s", home.name());
                handleTeleport(player, ref, store, playerRef);
            }

            case "Share", "ManageShare" -> {
                // Get fresh home data in case it changed
                Home currentHome = homeManager.getHome(uuid, home.name());
                if (currentHome != null) {
                    guiManager.openShareManage(player, ref, store, playerRef, currentHome);
                }
            }

            case "Delete" -> handleDelete(player, ref, store, playerRef);
        }
    }

    /**
     * Handles teleportation to this home.
     */
    private void handleTeleport(Player player, Ref<EntityStore> ref,
                                Store<EntityStore> store, PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        com.hyperhomes.util.Logger.info("[GUI-DEBUG] handleTeleport called for player %s, home %s", uuid, home.name());

        // Check cooldown
        long cooldown = homeManager.getRemainingCooldown(uuid);
        com.hyperhomes.util.Logger.info("[GUI-DEBUG] Cooldown remaining: %d ms", cooldown);
        if (cooldown > 0) {
            player.sendMessage(Message.raw("You must wait ")
                    .color("#FF5555")
                    .insert(Message.raw(UIHelper.formatDurationMs(cooldown)).color("#FFFF55"))
                    .insert(Message.raw(" before teleporting again.").color("#FF5555")));
            return;
        }

        // Close GUI before teleporting
        com.hyperhomes.util.Logger.info("[GUI-DEBUG] Closing GUI page");
        guiManager.closePage(player, ref, store);

        // Get current location for warmup tracking (use CURRENT world, not destination)
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        World currentWorld = player.getWorld();
        String currentWorldName = currentWorld != null ? currentWorld.getName() : home.world();
        Location currentLocation;
        if (transform != null) {
            var position = transform.getPosition();
            currentLocation = new Location(currentWorldName, position.x, position.y, position.z, 0f, 0f);
        } else {
            currentLocation = Location.fromHome(home);
        }

        // Get plugin for proper server-thread scheduling
        HyperHomes plugin = guiManager.getPlugin().get();

        // Store entity context for movement checking
        HyperHomesPlugin pluginInstance = HyperHomesPlugin.getInstance();
        if (pluginInstance != null) {
            pluginInstance.storeEntityContext(uuid, store, ref);
        }

        // Initiate teleport with proper scheduler
        com.hyperhomes.util.Logger.info("[GUI-DEBUG] Calling teleportManager.teleportToHome");
        boolean started = teleportManager.teleportToHome(
                uuid,
                home,
                currentLocation,
                (delayTicks, task) -> {
                    com.hyperhomes.util.Logger.info("[GUI-DEBUG] Scheduling task with delay %d ticks", delayTicks);
                    return plugin.scheduleDelayedTask(delayTicks, task);
                },
                plugin::cancelTask,
                destHome -> {
                    com.hyperhomes.util.Logger.info("[GUI-DEBUG] Teleport callback executed for home %s", destHome.name());

                    // Clean up entity context
                    if (pluginInstance != null) {
                        pluginInstance.removeEntityContext(uuid);
                    }

                    // Get target world
                    World world = Universe.get().getWorld(destHome.world());
                    com.hyperhomes.util.Logger.info("[GUI-DEBUG] Target world: %s, found: %s", destHome.world(), world != null);
                    if (world == null) {
                        return TeleportManager.TeleportResult.WORLD_NOT_FOUND;
                    }

                    // Execute teleport on the world's thread
                    // Use playerRef.getHolder() to get fresh store reference since GUI might have closed
                    world.execute(() -> {
                        com.hyperhomes.util.Logger.info("[GUI-DEBUG] World.execute running teleport");
                        Vector3d position = new Vector3d(destHome.x(), destHome.y(), destHome.z());
                        Vector3f rotation = new Vector3f(destHome.pitch(), destHome.yaw(), 0);
                        Teleport teleport = new Teleport(world, position, rotation);

                        // Get fresh holder from playerRef (store might be stale after GUI close)
                        var holder = playerRef.getHolder();
                        com.hyperhomes.util.Logger.info("[GUI-DEBUG] playerRef.getHolder() returned: %s", holder != null ? "valid" : "null");
                        if (holder != null) {
                            holder.addComponent(Teleport.getComponentType(), teleport);
                            com.hyperhomes.util.Logger.info("[GUI-DEBUG] Teleport component added");
                        } else {
                            com.hyperhomes.util.Logger.warn("[GUI-DEBUG] holder was null, teleport failed!");
                        }
                    });

                    return TeleportManager.TeleportResult.SUCCESS;
                },
                msg -> playerRef.sendMessage(UIHelper.parseColorCodes(msg))
        );
        com.hyperhomes.util.Logger.info("[GUI-DEBUG] teleportToHome returned: %s", started);
    }

    /**
     * Handles home deletion.
     */
    private void handleDelete(Player player, Ref<EntityStore> ref,
                              Store<EntityStore> store, PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();

        boolean deleted = homeManager.deleteHome(uuid, home.name());
        if (deleted) {
            player.sendMessage(Message.raw("[")
                    .color("#AAAAAA")
                    .insert(Message.raw("HyperHomes").color("#FFAA00"))
                    .insert(Message.raw("] ").color("#AAAAAA"))
                    .insert(Message.raw("Home '").color("#55FF55"))
                    .insert(Message.raw(home.name()).color("#FFFF55"))
                    .insert(Message.raw("' has been deleted.").color("#55FF55")));

            // Go back to homes list
            guiManager.openHomesList(player, ref, store, playerRef);
        } else {
            player.sendMessage(Message.raw("Failed to delete home.").color("#FF5555"));
        }
    }
}
