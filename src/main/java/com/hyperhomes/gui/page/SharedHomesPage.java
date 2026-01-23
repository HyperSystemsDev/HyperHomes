package com.hyperhomes.gui.page;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.platform.HyperHomesPlugin;
import com.hyperhomes.gui.UIHelper;
import com.hyperhomes.gui.data.HomesListData;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.PendingShareManager;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared Homes page - displays homes shared with the player.
 */
public class SharedHomesPage extends InteractiveCustomUIPage<HomesListData> {

    private static final int ENTRIES_PER_PAGE = 5;

    private final PlayerRef playerRef;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final GuiManager guiManager;
    private int currentPage = 0;

    public SharedHomesPage(PlayerRef playerRef, HomeManager homeManager,
                           TeleportManager teleportManager, GuiManager guiManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, HomesListData.CODEC);
        this.playerRef = playerRef;
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.guiManager = guiManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {

        UUID uuid = playerRef.getUuid();

        // Load the main template
        cmd.append("HyperHomes/shared_homes.ui");

        // Check for pending share requests
        PendingShareManager pendingManager = guiManager.getPendingShareManager().get();
        PendingShareManager.PendingShare pendingRequest = pendingManager.getRequest(uuid);
        if (pendingRequest != null) {
            // Show the pending notification
            cmd.append("#PendingNotification", "HyperHomes/pending_notification.ui");
            cmd.set("#PendingNotification #PendingOwnerName.Text", pendingRequest.ownerName());

            // Bind the view button
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#PendingNotification #ViewPendingBtn",
                    EventData.of("Button", "ViewPending"),
                    false
            );
        }

        // Get homes shared with player
        Map<UUID, List<Home>> sharedHomesMap = homeManager.getHomesSharedWithPlayer(uuid);

        // Flatten into a list of entries (owner UUID + home)
        List<SharedEntry> entries = new ArrayList<>();
        for (Map.Entry<UUID, List<Home>> entry : sharedHomesMap.entrySet()) {
            UUID ownerUuid = entry.getKey();
            String ownerName = homeManager.getUsername(ownerUuid);
            for (Home home : entry.getValue()) {
                entries.add(new SharedEntry(ownerUuid, ownerName, home));
            }
        }

        // Set total count
        cmd.set("#SharedCount.Text", String.valueOf(entries.size()));

        // Calculate pagination
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ENTRIES_PER_PAGE));
        currentPage = Math.min(currentPage, totalPages - 1);
        int startIdx = currentPage * ENTRIES_PER_PAGE;

        // Build shared entries
        for (int i = 0; i < ENTRIES_PER_PAGE; i++) {
            String entryId = "#SharedEntry" + i;
            int entryIdx = startIdx + i;

            if (entryIdx < entries.size()) {
                SharedEntry entry = entries.get(entryIdx);

                // Append entry template
                cmd.append(entryId, "HyperHomes/shared_entry.ui");

                // Set entry data
                String prefix = entryId + " ";
                cmd.set(prefix + "#HomeName.Text", entry.home.name());
                cmd.set(prefix + "#OwnerName.Text", entry.ownerName != null ? entry.ownerName : "Unknown");
                cmd.set(prefix + "#WorldName.Text", UIHelper.formatWorldName(entry.home.world()));
                cmd.set(prefix + "#Coords.Text", UIHelper.formatCoords(entry.home));

                // Bind teleport button - include owner info for shared home teleport
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        prefix + "#TeleportBtn",
                        EventData.of("Button", "TeleportShared")
                                .append("HomeName", entry.home.name())
                                .append("Page", entry.ownerUuid.toString()), // Reuse page field for owner UUID
                        false
                );
            }
        }

        // Setup pagination controls
        cmd.set("#PageInfo.Text", (currentPage + 1) + "/" + totalPages);

        // Bind prev button (only if not on first page)
        if (currentPage > 0) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#PrevBtn",
                    EventData.of("Button", "PrevPage"),
                    false
            );
        }

        // Bind next button (only if not on last page)
        if (currentPage < totalPages - 1) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#NextBtn",
                    EventData.of("Button", "NextPage"),
                    false
            );
        }

        // Bind navigation button (to my homes)
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NavHomes",
                EventData.of("Button", "NavHomes"),
                false
        );
        // Note: $C.@BackButton handles close automatically
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                HomesListData data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());

        if (player == null || playerRef == null) {
            return;
        }

        if (data.button == null) {
            return;
        }

        switch (data.button) {
            case "NavHomes" -> guiManager.openHomesList(player, ref, store, playerRef);

            case "ViewPending" -> guiManager.openShareConfirm(player, ref, store, playerRef);

            case "TeleportShared" -> {
                if (data.homeName != null && data.page != 0) {
                    try {
                        // Page field contains owner UUID
                        UUID ownerUuid = UUID.fromString(String.valueOf(data.page));
                        handleTeleportShared(player, ref, store, playerRef, ownerUuid, data.homeName);
                    } catch (IllegalArgumentException ignored) {
                        // Invalid UUID
                    }
                }
            }

            case "PrevPage" -> {
                currentPage = Math.max(0, currentPage - 1);
                guiManager.openSharedHomes(player, ref, store, playerRef);
            }

            case "NextPage" -> {
                currentPage++;
                guiManager.openSharedHomes(player, ref, store, playerRef);
            }
        }
    }

    /**
     * Handles teleportation to a shared home.
     */
    private void handleTeleportShared(Player player, Ref<EntityStore> ref,
                                      Store<EntityStore> store, PlayerRef playerRef,
                                      UUID ownerUuid, String homeName) {
        UUID uuid = playerRef.getUuid();

        // Get the shared home
        Home home = homeManager.getSharedHome(uuid, ownerUuid, homeName);
        if (home == null) {
            player.sendMessage(Message.raw("Shared home not found or no longer accessible!")
                    .color("#FF5555"));
            return;
        }

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

        // Initiate teleport
        teleportManager.teleportToHome(
                uuid,
                home,
                currentLocation,
                (delayTicks, task) -> plugin.scheduleDelayedTask(delayTicks, task),
                plugin::cancelTask,
                destHome -> {
                    // Clean up entity context
                    if (pluginInstance != null) {
                        pluginInstance.removeEntityContext(uuid);
                    }

                    // Get target world
                    World world = Universe.get().getWorld(destHome.world());
                    if (world == null) {
                        return TeleportManager.TeleportResult.WORLD_NOT_FOUND;
                    }

                    // Execute teleport on the world's thread (like HomeCommand does)
                    world.execute(() -> {
                        Vector3d position = new Vector3d(destHome.x(), destHome.y(), destHome.z());
                        Vector3f rotation = new Vector3f(destHome.pitch(), destHome.yaw(), 0);
                        Teleport teleport = new Teleport(world, position, rotation);
                        store.addComponent(ref, Teleport.getComponentType(), teleport);
                    });

                    return TeleportManager.TeleportResult.SUCCESS;
                },
                msg -> playerRef.sendMessage(UIHelper.parseColorCodes(msg))
        );
    }

    /**
     * Helper record for shared home entries.
     */
    private record SharedEntry(UUID ownerUuid, String ownerName, Home home) {
    }
}
