package com.hyperhomes.gui.page;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.config.HyperHomesConfig;
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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Homes List page - displays all player homes in a paginated grid.
 * Players can teleport to homes, edit them, or navigate to shared homes.
 */
public class HomesListPage extends InteractiveCustomUIPage<HomesListData> {

    private static final int HOMES_PER_PAGE = 6;

    private final PlayerRef playerRef;
    private final HomeManager homeManager;
    private final TeleportManager teleportManager;
    private final GuiManager guiManager;
    private int currentPage = 0;

    public HomesListPage(PlayerRef playerRef, HomeManager homeManager,
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
        cmd.append("HyperHomes/homes_list.ui");

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

        // Get player homes
        Collection<Home> homes = homeManager.getHomes(uuid);
        int limit = homeManager.getHomeLimit(uuid);
        String limitStr = UIHelper.formatLimit(limit);

        // Set home count header
        cmd.set("#HomeCount.Text", homes.size() + "/" + limitStr);

        // Sort homes alphabetically
        List<Home> sortedHomes = homes.stream()
                .sorted(Comparator.comparing(Home::name))
                .toList();

        // Calculate pagination
        int totalPages = Math.max(1, (int) Math.ceil((double) sortedHomes.size() / HOMES_PER_PAGE));
        currentPage = Math.min(currentPage, totalPages - 1);
        int startIdx = currentPage * HOMES_PER_PAGE;

        // Build home cards
        for (int i = 0; i < HOMES_PER_PAGE; i++) {
            String cardId = "#HomeCard" + i;
            int homeIdx = startIdx + i;

            if (homeIdx < sortedHomes.size()) {
                Home home = sortedHomes.get(homeIdx);

                // Append card template
                cmd.append(cardId, "HyperHomes/home_card.ui");

                // Set card data
                String prefix = cardId + " ";
                cmd.set(prefix + "#HomeName.Text", home.name());
                cmd.set(prefix + "#WorldName.Text", UIHelper.formatWorldName(home.world()));
                cmd.set(prefix + "#Coords.Text", UIHelper.formatCoords(home));

                // Bind teleport button
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        prefix + "#TeleportBtn",
                        EventData.of("Button", "Teleport")
                                .append("HomeName", home.name()),
                        false
                );

                // Bind edit button
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        prefix + "#EditBtn",
                        EventData.of("Button", "Edit")
                                .append("HomeName", home.name()),
                        false
                );
            }
            // Empty slots are left blank
        }

        // Setup pagination controls
        cmd.set("#PageInfo.Text", (currentPage + 1) + "/" + totalPages);

        // Bind prev button (only if not on first page)
        if (currentPage > 0) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#PrevBtn",
                    EventData.of("Button", "PrevPage")
                            .append("Page", String.valueOf(currentPage - 1)),
                    false
            );
        }

        // Bind next button (only if not on last page)
        if (currentPage < totalPages - 1) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#NextBtn",
                    EventData.of("Button", "NextPage")
                            .append("Page", String.valueOf(currentPage + 1)),
                    false
            );
        }

        // Bind navigation button (to shared homes)
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#NavShared",
                EventData.of("Button", "NavShared"),
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

        UUID uuid = playerRef.getUuid();

        switch (data.button) {
            case "Teleport" -> {
                if (data.homeName != null) {
                    handleTeleport(player, ref, store, playerRef, data.homeName);
                }
            }

            case "Edit" -> {
                if (data.homeName != null) {
                    Home home = homeManager.getHome(uuid, data.homeName);
                    if (home != null) {
                        guiManager.openHomeDetail(player, ref, store, playerRef, home);
                    }
                }
            }

            case "NavShared" -> guiManager.openSharedHomes(player, ref, store, playerRef);

            case "ViewPending" -> guiManager.openShareConfirm(player, ref, store, playerRef);

            case "PrevPage" -> {
                currentPage = Math.max(0, currentPage - 1);
                guiManager.openHomesList(player, ref, store, playerRef);
            }

            case "NextPage" -> {
                currentPage++;
                guiManager.openHomesList(player, ref, store, playerRef);
            }
        }
    }

    /**
     * Handles teleportation to a home.
     */
    private void handleTeleport(Player player, Ref<EntityStore> ref,
                                Store<EntityStore> store, PlayerRef playerRef,
                                String homeName) {
        UUID uuid = playerRef.getUuid();
        Home home = homeManager.getHome(uuid, homeName);

        if (home == null) {
            player.sendMessage(Message.raw("Home not found!").color("#FF5555"));
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

                    // Execute teleport on the world's thread
                    // Use playerRef.getHolder() to get fresh store reference since GUI might have closed
                    world.execute(() -> {
                        Vector3d position = new Vector3d(destHome.x(), destHome.y(), destHome.z());
                        Vector3f rotation = new Vector3f(destHome.pitch(), destHome.yaw(), 0);
                        Teleport teleport = new Teleport(world, position, rotation);

                        // Get fresh holder from playerRef (store might be stale after GUI close)
                        var holder = playerRef.getHolder();
                        if (holder != null) {
                            holder.addComponent(Teleport.getComponentType(), teleport);
                        }
                    });

                    return TeleportManager.TeleportResult.SUCCESS;
                },
                msg -> playerRef.sendMessage(UIHelper.parseColorCodes(msg))
        );
    }
}
