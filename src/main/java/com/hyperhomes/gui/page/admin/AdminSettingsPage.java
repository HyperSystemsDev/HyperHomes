package com.hyperhomes.gui.page.admin;

import com.hyperhomes.config.HyperHomesConfig;
import com.hyperhomes.gui.GuiManager;
import com.hyperhomes.gui.data.AdminData;
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

import java.nio.file.Path;

/**
 * Admin Settings page - configure server settings with live editing.
 */
public class AdminSettingsPage extends InteractiveCustomUIPage<AdminData> {

    private final GuiManager guiManager;
    private final Path dataDir;

    public AdminSettingsPage(PlayerRef playerRef, GuiManager guiManager, Path dataDir) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminData.CODEC);
        this.guiManager = guiManager;
        this.dataDir = dataDir;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {

        // Load the main template
        cmd.append("HyperHomes/admin_settings.ui");

        // Pre-fill current values
        HyperHomesConfig config = HyperHomesConfig.get();
        cmd.set("#WarmupValue.Text", config.getWarmupSeconds() + "s");
        cmd.set("#CooldownValue.Text", config.getCooldownSeconds() + "s");
        cmd.set("#LimitValue.Text", String.valueOf(config.getDefaultHomeLimit()));

        // Set toggle button states
        cmd.set("#CancelMoveToggle.Text", config.isCancelOnMove() ? "ON" : "OFF");
        cmd.set("#CancelDamageToggle.Text", config.isCancelOnDamage() ? "ON" : "OFF");

        // Bind +/- buttons for warmup
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#WarmupMinus",
                EventData.of("Button", "WarmupMinus"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#WarmupPlus",
                EventData.of("Button", "WarmupPlus"),
                false
        );

        // Bind +/- buttons for cooldown
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CooldownMinus",
                EventData.of("Button", "CooldownMinus"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CooldownPlus",
                EventData.of("Button", "CooldownPlus"),
                false
        );

        // Bind +/- buttons for limit
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#LimitMinus",
                EventData.of("Button", "LimitMinus"),
                false
        );
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#LimitPlus",
                EventData.of("Button", "LimitPlus"),
                false
        );

        // Bind toggle buttons
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelMoveToggle",
                EventData.of("Button", "ToggleCancelMove"),
                false
        );

        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#CancelDamageToggle",
                EventData.of("Button", "ToggleCancelDamage"),
                false
        );

        // Bind action buttons
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#SaveBtn",
                EventData.of("Button", "Save"),
                false
        );

        // Note: $C.@BackButton {} handles back/close functionality automatically
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

        HyperHomesConfig config = HyperHomesConfig.get();
        boolean configChanged = false;

        switch (data.button) {
            case "Close" -> guiManager.closePage(player, ref, store);

            case "Back" -> guiManager.openAdminMain(player, ref, store, playerRef);

            case "WarmupMinus" -> {
                config.setWarmupSeconds(config.getWarmupSeconds() - 1);
                configChanged = true;
            }

            case "WarmupPlus" -> {
                config.setWarmupSeconds(config.getWarmupSeconds() + 1);
                configChanged = true;
            }

            case "CooldownMinus" -> {
                config.setCooldownSeconds(config.getCooldownSeconds() - 1);
                configChanged = true;
            }

            case "CooldownPlus" -> {
                config.setCooldownSeconds(config.getCooldownSeconds() + 1);
                configChanged = true;
            }

            case "LimitMinus" -> {
                config.setDefaultHomeLimit(config.getDefaultHomeLimit() - 1);
                configChanged = true;
            }

            case "LimitPlus" -> {
                config.setDefaultHomeLimit(config.getDefaultHomeLimit() + 1);
                configChanged = true;
            }

            case "ToggleCancelMove" -> {
                config.setCancelOnMove(!config.isCancelOnMove());
                configChanged = true;
            }

            case "ToggleCancelDamage" -> {
                config.setCancelOnDamage(!config.isCancelOnDamage());
                configChanged = true;
            }

            case "Save" -> {
                config.save(dataDir);
                player.sendMessage(Message.raw("[")
                        .color("#AAAAAA")
                        .insert(Message.raw("HyperHomes").color("#FFAA00"))
                        .insert(Message.raw("] ").color("#AAAAAA"))
                        .insert(Message.raw("Settings saved to config.json!").color("#55FF55")));
            }
        }

        // Refresh page to show updated values
        if (configChanged) {
            guiManager.openAdminSettings(player, ref, store, playerRef);
        }
    }
}
