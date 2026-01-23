package com.hyperhomes.gui.page;

import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.gui.data.ShareManageData;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.PendingShareManager;
import com.hyperhomes.model.Home;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Share Manage page - manage sharing for a specific home.
 * Shows who the home is shared with and allows adding/removing players.
 */
public class ShareManagePage extends InteractiveCustomUIPage<ShareManageData> {

    private static final int MAX_DISPLAYED_PLAYERS = 4;

    private final HomeManager homeManager;
    private final PendingShareManager pendingShareManager;
    private final GuiManager guiManager;
    private final Home home;

    public ShareManagePage(PlayerRef playerRef, HomeManager homeManager,
                           PendingShareManager pendingShareManager,
                           GuiManager guiManager, Home home) {
        super(playerRef, CustomPageLifetime.CanDismiss, ShareManageData.CODEC);
        this.homeManager = homeManager;
        this.pendingShareManager = pendingShareManager;
        this.guiManager = guiManager;
        this.home = home;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {

        // Load the main template
        cmd.append("HyperHomes/share_manage.ui");

        // Set home name
        cmd.set("#HomeName.Text", home.name());

        // Get shared players
        Set<UUID> sharedWith = home.sharedWith();
        List<UUID> sharedList = new ArrayList<>(sharedWith);

        // Show/hide no shared label by setting text
        if (!sharedList.isEmpty()) {
            // Hide the label by clearing its text when there are shared players
            cmd.set("#NoSharedLabel.Text", "");
        }
        // Otherwise the default "Not shared with anyone" text shows

        // Display shared players (up to MAX_DISPLAYED_PLAYERS)
        for (int i = 0; i < MAX_DISPLAYED_PLAYERS; i++) {
            String entryId = "#SharedPlayer" + i;

            if (i < sharedList.size()) {
                UUID playerUuid = sharedList.get(i);
                String playerName = homeManager.getUsername(playerUuid);
                if (playerName == null) {
                    playerName = playerUuid.toString().substring(0, 8) + "...";
                }

                // Append player entry template
                cmd.append(entryId, "HyperHomes/shared_player_entry.ui");

                // Set player name
                cmd.set(entryId + " #PlayerName.Text", playerName);

                // Bind remove button
                events.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        entryId + " #RemoveBtn",
                        EventData.of("Button", "RemoveShare")
                                .append("HomeName", home.name())
                                .append("TargetPlayer", playerUuid.toString()),
                        false
                );
            }
        }

        // Bind add share button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AddShareBtn",
                EventData.of("Button", "AddShare")
                        .append("HomeName", home.name()),
                false
        );

        // Note: $C.@BackButton {} handles back/close functionality automatically
        // Note: Text field value should be captured via PlayerNameInput field
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                ShareManageData data) {
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

            case "Back" -> {
                // Get fresh home data and go back to detail
                Home currentHome = homeManager.getHome(uuid, home.name());
                if (currentHome != null) {
                    guiManager.openHomeDetail(player, ref, store, playerRef, currentHome);
                } else {
                    guiManager.openHomesList(player, ref, store, playerRef);
                }
            }

            case "AddShare" -> {
                // Get the player name from the text input
                String targetName = data.inputText;
                if (targetName == null || targetName.trim().isEmpty()) {
                    player.sendMessage(Message.raw("[")
                            .color("#AAAAAA")
                            .insert(Message.raw("HyperHomes").color("#FFAA00"))
                            .insert(Message.raw("] ").color("#AAAAAA"))
                            .insert(Message.raw("Please enter a player name.").color("#FF5555")));
                    return;
                }

                targetName = targetName.trim();

                // Find the target player
                UUID targetUuid = homeManager.findPlayerByUsername(targetName);
                if (targetUuid == null) {
                    player.sendMessage(Message.raw("[")
                            .color("#AAAAAA")
                            .insert(Message.raw("HyperHomes").color("#FFAA00"))
                            .insert(Message.raw("] ").color("#AAAAAA"))
                            .insert(Message.raw("Player '").color("#FF5555"))
                            .insert(Message.raw(targetName).color("#FFFF55"))
                            .insert(Message.raw("' is not online or doesn't exist.").color("#FF5555")));
                    return;
                }

                // Can't share with yourself
                if (targetUuid.equals(uuid)) {
                    player.sendMessage(Message.raw("[")
                            .color("#AAAAAA")
                            .insert(Message.raw("HyperHomes").color("#FFAA00"))
                            .insert(Message.raw("] ").color("#AAAAAA"))
                            .insert(Message.raw("You can't share a home with yourself!").color("#FF5555")));
                    return;
                }

                // Check if already shared
                Home currentHome = homeManager.getHome(uuid, home.name());
                if (currentHome != null && currentHome.isSharedWith(targetUuid)) {
                    player.sendMessage(Message.raw("[")
                            .color("#AAAAAA")
                            .insert(Message.raw("HyperHomes").color("#FFAA00"))
                            .insert(Message.raw("] ").color("#AAAAAA"))
                            .insert(Message.raw("This home is already shared with ").color("#FF5555"))
                            .insert(Message.raw(targetName).color("#FFFF55"))
                            .insert(Message.raw(".").color("#FF5555")));
                    return;
                }

                // Create pending share request
                boolean created = pendingShareManager.createRequest(
                        uuid,
                        playerRef.getUsername(),
                        home.name(),
                        targetUuid
                );

                if (created) {
                    player.sendMessage(Message.raw("[")
                            .color("#AAAAAA")
                            .insert(Message.raw("HyperHomes").color("#FFAA00"))
                            .insert(Message.raw("] ").color("#AAAAAA"))
                            .insert(Message.raw("Share request sent to ").color("#55FF55"))
                            .insert(Message.raw(targetName).color("#FFFF55"))
                            .insert(Message.raw("! They must accept it.").color("#55FF55")));

                    // Notify the target player via chat that they have a pending request
                    // They can use /homes pending to see it or it will pop up
                    // Note: We'd need access to the target player's Player entity to send them a message
                    // For now, the request is stored and they can check via /homes pending
                } else {
                    player.sendMessage(Message.raw("[")
                            .color("#AAAAAA")
                            .insert(Message.raw("HyperHomes").color("#FFAA00"))
                            .insert(Message.raw("] ").color("#AAAAAA"))
                            .insert(Message.raw(targetName).color("#FFFF55"))
                            .insert(Message.raw(" already has a pending share request. Please wait.").color("#FF5555")));
                }

                // Refresh the page
                Home updatedHome = homeManager.getHome(uuid, home.name());
                if (updatedHome != null) {
                    guiManager.openShareManage(player, ref, store, playerRef, updatedHome);
                }
            }

            case "RemoveShare" -> {
                if (data.targetPlayer != null) {
                    try {
                        UUID targetUuid = UUID.fromString(data.targetPlayer);
                        boolean success = homeManager.unshareHome(uuid, home.name(), targetUuid);

                        if (success) {
                            String targetName = homeManager.getUsername(targetUuid);
                            player.sendMessage(Message.raw("[")
                                    .color("#AAAAAA")
                                    .insert(Message.raw("HyperHomes").color("#FFAA00"))
                                    .insert(Message.raw("] ").color("#AAAAAA"))
                                    .insert(Message.raw("Home '").color("#55FF55"))
                                    .insert(Message.raw(home.name()).color("#FFFF55"))
                                    .insert(Message.raw("' is no longer shared with ").color("#55FF55"))
                                    .insert(Message.raw(targetName != null ? targetName : "that player").color("#FFFF55"))
                                    .insert(Message.raw(".").color("#55FF55")));

                            // Refresh the page with updated home data
                            Home updatedHome = homeManager.getHome(uuid, home.name());
                            if (updatedHome != null) {
                                guiManager.openShareManage(player, ref, store, playerRef, updatedHome);
                            }
                        } else {
                            player.sendMessage(Message.raw("Failed to unshare home.").color("#FF5555"));
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Invalid UUID
                    }
                }
            }
        }
    }
}
