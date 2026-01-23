package com.hyperhomes.gui.page.admin;

import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.gui.data.AdminData;
import com.hyperhomes.manager.HomeManager;
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
 * Admin Main page - dashboard for admin controls.
 * Displays statistics and provides quick actions.
 */
public class AdminMainPage extends InteractiveCustomUIPage<AdminData> {

    private final HomeManager homeManager;
    private final GuiManager guiManager;

    public AdminMainPage(PlayerRef playerRef, HomeManager homeManager, GuiManager guiManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminData.CODEC);
        this.homeManager = homeManager;
        this.guiManager = guiManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {

        // Load the main template
        cmd.append("HyperHomes/admin_main.ui");

        // Calculate statistics
        int cachedPlayers = homeManager.getCacheSize();
        int totalHomes = homeManager.getTotalHomesCount();

        cmd.set("#PlayersCount.Text", String.valueOf(cachedPlayers));
        cmd.set("#HomesCount.Text", String.valueOf(totalHomes));

        // Bind action buttons
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SettingsBtn",
                EventData.of("Button", "Settings"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ReloadBtn",
                EventData.of("Button", "Reload"),
                false
        );

        // Note: $C.@BackButton {} handles close functionality automatically
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store,
                                AdminData data) {
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
            case "Close" -> guiManager.closePage(player, ref, store);

            case "Settings" -> guiManager.openAdminSettings(player, ref, store, playerRef);

            case "Reload" -> {
                // Reload config
                // Note: This would need access to the plugin's reloadConfig method
                // For now, just refresh the page
                player.sendMessage(Message.raw("[")
                        .color("#AAAAAA")
                        .insert(Message.raw("HyperHomes").color("#FFAA00"))
                        .insert(Message.raw("] ").color("#AAAAAA"))
                        .insert(Message.raw("Use ").color("#55FF55"))
                        .insert(Message.raw("/homes reload").color("#FFFF55"))
                        .insert(Message.raw(" to reload the configuration.").color("#55FF55")));

                // Refresh the page
                guiManager.openAdminMain(player, ref, store, playerRef);
            }
        }
    }
}
