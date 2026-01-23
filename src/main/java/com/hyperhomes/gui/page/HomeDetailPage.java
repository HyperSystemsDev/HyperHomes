package com.hyperhomes.gui.page;

import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.gui.UIHelper;
import com.hyperhomes.gui.data.HomeDetailData;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.TeleportManager;
import com.hyperhomes.model.Home;
import com.hyperhomes.model.Location;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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

        // Show share info if home is shared
        if (home.isShared()) {
            cmd.set("#ShareInfo.Visible", "true");
            int sharedCount = home.sharedWith().size();
            cmd.set("#SharedCount.Text", sharedCount + " player" + (sharedCount != 1 ? "s" : ""));

            // Bind manage share button
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#ManageShareBtn",
                    EventData.of("Button", "ManageShare")
                            .append("HomeName", home.name()),
                    false
            );
        }

        // Bind action buttons
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#TeleportBtn",
                EventData.of("Button", "Teleport")
                        .append("HomeName", home.name()),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ShareBtn",
                EventData.of("Button", "Share")
                        .append("HomeName", home.name()),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DeleteBtn",
                EventData.of("Button", "Delete")
                        .append("HomeName", home.name()),
                false
        );

        // Note: $C.@BackButton {} handles back/close functionality automatically
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                HomeDetailData data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        if (data.button == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        switch (data.button) {
            case "Close" -> guiManager.closePage(player, ref, store);

            case "Back" -> guiManager.openHomesList(player, ref, store, playerRef);

            case "Teleport" -> handleTeleport(player, ref, store, playerRef);

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

        // Check cooldown
        long cooldown = homeManager.getRemainingCooldown(uuid);
        if (cooldown > 0) {
            player.sendMessage(Message.raw("You must wait ")
                    .color("#FF5555")
                    .insert(Message.raw(UIHelper.formatDurationMs(cooldown)).color("#FFFF55"))
                    .insert(Message.raw(" before teleporting again.").color("#FF5555")));
            return;
        }

        // Close GUI before teleporting
        guiManager.closePage(player, ref, store);

        // Get current location for warmup tracking
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Location currentLocation;
        if (transform != null) {
            var position = transform.getPosition();
            currentLocation = new Location(home.world(), position.x, position.y, position.z, 0f, 0f);
        } else {
            currentLocation = Location.fromHome(home);
        }

        // Initiate teleport
        teleportManager.teleportToHome(
                uuid,
                home,
                currentLocation,
                (delayTicks, task) -> {
                    java.util.Timer timer = new java.util.Timer();
                    timer.schedule(new java.util.TimerTask() {
                        @Override
                        public void run() {
                            task.run();
                        }
                    }, delayTicks * 50L);
                    return delayTicks;
                },
                taskId -> { },
                destHome -> {
                    var world = com.hypixel.hytale.server.core.universe.Universe.get()
                            .getWorld(destHome.world());
                    if (world == null) {
                        return TeleportManager.TeleportResult.WORLD_NOT_FOUND;
                    }

                    var teleport = new com.hypixel.hytale.server.core.modules.entity.teleport.Teleport(
                            world,
                            new com.hypixel.hytale.math.vector.Vector3d(
                                    destHome.x(), destHome.y(), destHome.z()
                            ),
                            new com.hypixel.hytale.math.vector.Vector3f(
                                    destHome.pitch(), destHome.yaw(), 0
                            )
                    );

                    var holder = playerRef.getHolder();
                    if (holder != null) {
                        holder.addComponent(
                                com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType(),
                                teleport
                        );
                        return TeleportManager.TeleportResult.SUCCESS;
                    }

                    return TeleportManager.TeleportResult.CANCELLED_MANUAL;
                },
                msg -> playerRef.sendMessage(Message.raw(msg))
        );
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
