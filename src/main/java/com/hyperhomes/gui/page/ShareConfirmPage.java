package com.hyperhomes.gui.page;

import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.gui.data.ShareManageData;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.PendingShareManager;
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

/**
 * Share Confirmation page - allows players to accept or decline share requests.
 */
public class ShareConfirmPage extends InteractiveCustomUIPage<ShareManageData> {

    private final HomeManager homeManager;
    private final PendingShareManager pendingShareManager;
    private final GuiManager guiManager;
    private final PendingShareManager.PendingShare request;

    public ShareConfirmPage(PlayerRef playerRef, HomeManager homeManager,
                            PendingShareManager pendingShareManager, GuiManager guiManager,
                            PendingShareManager.PendingShare request) {
        super(playerRef, CustomPageLifetime.CanDismiss, ShareManageData.CODEC);
        this.homeManager = homeManager;
        this.pendingShareManager = pendingShareManager;
        this.guiManager = guiManager;
        this.request = request;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {

        // Load the main template
        cmd.append("HyperHomes/share_confirm.ui");

        // Set request info
        cmd.set("#OwnerName.Text", request.ownerName());
        cmd.set("#HomeName.Text", request.homeName());

        // Bind accept button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#AcceptBtn",
                EventData.of("Button", "Accept"),
                false
        );

        // Bind decline button
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#DeclineBtn",
                EventData.of("Button", "Decline"),
                false
        );

        // Note: $C.@BackButton {} handles back/close functionality automatically
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

        switch (data.button) {
            case "Accept" -> {
                // Accept the share request
                PendingShareManager.PendingShare accepted = pendingShareManager.acceptRequest(playerRef.getUuid());
                if (accepted != null) {
                    // Actually share the home
                    boolean success = homeManager.shareHome(accepted.ownerUuid(), accepted.homeName(), playerRef.getUuid());
                    if (success) {
                        player.sendMessage(Message.raw("[")
                                .color("#AAAAAA")
                                .insert(Message.raw("HyperHomes").color("#FFAA00"))
                                .insert(Message.raw("] ").color("#AAAAAA"))
                                .insert(Message.raw("You now have access to ").color("#55FF55"))
                                .insert(Message.raw(accepted.ownerName()).color("#4a9eff"))
                                .insert(Message.raw("'s home '").color("#55FF55"))
                                .insert(Message.raw(accepted.homeName()).color("#FFFF55"))
                                .insert(Message.raw("'!").color("#55FF55")));
                    } else {
                        player.sendMessage(Message.raw("[")
                                .color("#AAAAAA")
                                .insert(Message.raw("HyperHomes").color("#FFAA00"))
                                .insert(Message.raw("] ").color("#AAAAAA"))
                                .insert(Message.raw("Failed to accept share request. The home may no longer exist.").color("#FF5555")));
                    }
                }
                guiManager.closePage(player, ref, store);
            }

            case "Decline" -> {
                // Decline the share request
                pendingShareManager.declineRequest(playerRef.getUuid());
                player.sendMessage(Message.raw("[")
                        .color("#AAAAAA")
                        .insert(Message.raw("HyperHomes").color("#FFAA00"))
                        .insert(Message.raw("] ").color("#AAAAAA"))
                        .insert(Message.raw("Share request declined.").color("#FFFF55")));
                guiManager.closePage(player, ref, store);
            }

            case "Close", "Back" -> guiManager.closePage(player, ref, store);
        }
    }
}
