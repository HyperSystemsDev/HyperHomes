package com.hyperhomes.gui;

import com.hyperhomes.HyperHomes;
import com.hyperhomes.gui.page.HomeDetailPage;
import com.hyperhomes.gui.page.HomesListPage;
import com.hyperhomes.gui.page.ShareConfirmPage;
import com.hyperhomes.gui.page.ShareManagePage;
import com.hyperhomes.gui.page.SharedHomesPage;
import com.hyperhomes.gui.page.admin.AdminMainPage;
import com.hyperhomes.gui.page.admin.AdminSettingsPage;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.manager.PendingShareManager;
import com.hyperhomes.manager.TeleportManager;
import com.hyperhomes.model.Home;
import com.hyperhomes.util.Logger;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Central manager for HyperHomes GUI pages.
 * Provides methods to open various UI screens.
 */
public class GuiManager {

    private final Supplier<HyperHomes> plugin;
    private final Supplier<HomeManager> homeManager;
    private final Supplier<TeleportManager> teleportManager;
    private final Supplier<Path> dataDir;
    private final Supplier<PendingShareManager> pendingShareManager;

    public GuiManager(Supplier<HyperHomes> plugin,
                      Supplier<HomeManager> homeManager,
                      Supplier<TeleportManager> teleportManager,
                      Supplier<Path> dataDir,
                      Supplier<PendingShareManager> pendingShareManager) {
        this.plugin = plugin;
        this.homeManager = homeManager;
        this.teleportManager = teleportManager;
        this.dataDir = dataDir;
        this.pendingShareManager = pendingShareManager;
    }

    /**
     * Opens the Homes List page for a player.
     *
     * @param player    The Player entity
     * @param ref       The entity reference
     * @param store     The entity store
     * @param playerRef The PlayerRef component
     */
    public void openHomesList(Player player, Ref<EntityStore> ref,
                              Store<EntityStore> store, PlayerRef playerRef) {
        Logger.debug("[GUI] Opening HomesListPage for %s", playerRef.getUsername());
        try {
            PageManager pageManager = player.getPageManager();
            HomesListPage page = new HomesListPage(
                playerRef,
                homeManager.get(),
                teleportManager.get(),
                this
            );
            Logger.debug("[GUI] HomesListPage created, opening custom page...");
            pageManager.openCustomPage(ref, store, page);
            Logger.debug("[GUI] HomesListPage opened successfully");
        } catch (Exception e) {
            Logger.severe("[GUI] Failed to open HomesListPage: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Opens the Home Detail page for a specific home.
     *
     * @param player    The Player entity
     * @param ref       The entity reference
     * @param store     The entity store
     * @param playerRef The PlayerRef component
     * @param home      The home to display
     */
    public void openHomeDetail(Player player, Ref<EntityStore> ref,
                               Store<EntityStore> store, PlayerRef playerRef,
                               Home home) {
        Logger.debug("[GUI] Opening HomeDetailPage for %s, home: %s", playerRef.getUsername(), home.name());
        try {
            PageManager pageManager = player.getPageManager();
            HomeDetailPage page = new HomeDetailPage(
                playerRef,
                homeManager.get(),
                teleportManager.get(),
                this,
                home
            );
            pageManager.openCustomPage(ref, store, page);
            Logger.debug("[GUI] HomeDetailPage opened successfully");
        } catch (Exception e) {
            Logger.severe("[GUI] Failed to open HomeDetailPage: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Opens the Shared Homes page.
     *
     * @param player    The Player entity
     * @param ref       The entity reference
     * @param store     The entity store
     * @param playerRef The PlayerRef component
     */
    public void openSharedHomes(Player player, Ref<EntityStore> ref,
                                Store<EntityStore> store, PlayerRef playerRef) {
        Logger.debug("[GUI] Opening SharedHomesPage for %s", playerRef.getUsername());
        try {
            PageManager pageManager = player.getPageManager();
            SharedHomesPage page = new SharedHomesPage(
                playerRef,
                homeManager.get(),
                teleportManager.get(),
                this
            );
            pageManager.openCustomPage(ref, store, page);
            Logger.debug("[GUI] SharedHomesPage opened successfully");
        } catch (Exception e) {
            Logger.severe("[GUI] Failed to open SharedHomesPage: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Opens the Share Manage page for a specific home.
     *
     * @param player    The Player entity
     * @param ref       The entity reference
     * @param store     The entity store
     * @param playerRef The PlayerRef component
     * @param home      The home to manage sharing for
     */
    public void openShareManage(Player player, Ref<EntityStore> ref,
                                Store<EntityStore> store, PlayerRef playerRef,
                                Home home) {
        Logger.debug("[GUI] Opening ShareManagePage for %s, home: %s", playerRef.getUsername(), home.name());
        try {
            PageManager pageManager = player.getPageManager();
            ShareManagePage page = new ShareManagePage(
                playerRef,
                homeManager.get(),
                pendingShareManager.get(),
                this,
                home
            );
            pageManager.openCustomPage(ref, store, page);
            Logger.debug("[GUI] ShareManagePage opened successfully");
        } catch (Exception e) {
            Logger.severe("[GUI] Failed to open ShareManagePage: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Opens the Share Confirmation page for a player with a pending request.
     *
     * @param player    The Player entity
     * @param ref       The entity reference
     * @param store     The entity store
     * @param playerRef The PlayerRef component
     */
    public void openShareConfirm(Player player, Ref<EntityStore> ref,
                                 Store<EntityStore> store, PlayerRef playerRef) {
        Logger.debug("[GUI] Opening ShareConfirmPage for %s", playerRef.getUsername());
        try {
            PendingShareManager.PendingShare request = pendingShareManager.get().getRequest(playerRef.getUuid());
            if (request == null) {
                Logger.debug("[GUI] No pending share request for %s", playerRef.getUsername());
                return;
            }

            PageManager pageManager = player.getPageManager();
            ShareConfirmPage page = new ShareConfirmPage(
                playerRef,
                homeManager.get(),
                pendingShareManager.get(),
                this,
                request
            );
            pageManager.openCustomPage(ref, store, page);
            Logger.debug("[GUI] ShareConfirmPage opened successfully");
        } catch (Exception e) {
            Logger.severe("[GUI] Failed to open ShareConfirmPage: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Opens the Admin Main page.
     * Requires hyperhomes.admin permission.
     *
     * @param player    The Player entity
     * @param ref       The entity reference
     * @param store     The entity store
     * @param playerRef The PlayerRef component
     */
    public void openAdminMain(Player player, Ref<EntityStore> ref,
                              Store<EntityStore> store, PlayerRef playerRef) {
        Logger.debug("[GUI] Opening AdminMainPage for %s", playerRef.getUsername());
        try {
            PageManager pageManager = player.getPageManager();
            AdminMainPage page = new AdminMainPage(
                playerRef,
                homeManager.get(),
                this
            );
            pageManager.openCustomPage(ref, store, page);
            Logger.debug("[GUI] AdminMainPage opened successfully");
        } catch (Exception e) {
            Logger.severe("[GUI] Failed to open AdminMainPage: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Opens the Admin Settings page.
     * Requires hyperhomes.admin.settings permission.
     *
     * @param player    The Player entity
     * @param ref       The entity reference
     * @param store     The entity store
     * @param playerRef The PlayerRef component
     */
    public void openAdminSettings(Player player, Ref<EntityStore> ref,
                                  Store<EntityStore> store, PlayerRef playerRef) {
        Logger.debug("[GUI] Opening AdminSettingsPage for %s", playerRef.getUsername());
        try {
            PageManager pageManager = player.getPageManager();
            AdminSettingsPage page = new AdminSettingsPage(
                playerRef,
                this,
                dataDir.get()
            );
            pageManager.openCustomPage(ref, store, page);
            Logger.debug("[GUI] AdminSettingsPage opened successfully");
        } catch (Exception e) {
            Logger.severe("[GUI] Failed to open AdminSettingsPage: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Closes the current page.
     *
     * @param player The Player entity
     * @param ref    The entity reference
     * @param store  The entity store
     */
    public void closePage(Player player, Ref<EntityStore> ref, Store<EntityStore> store) {
        player.getPageManager().setPage(ref, store,
                com.hypixel.hytale.protocol.packets.interface_.Page.None);
    }

    /**
     * Gets the HomeManager supplier.
     */
    public Supplier<HomeManager> getHomeManager() {
        return homeManager;
    }

    /**
     * Gets the TeleportManager supplier.
     */
    public Supplier<TeleportManager> getTeleportManager() {
        return teleportManager;
    }

    /**
     * Gets the data directory supplier.
     */
    public Supplier<Path> getDataDir() {
        return dataDir;
    }

    /**
     * Gets the PendingShareManager supplier.
     */
    public Supplier<PendingShareManager> getPendingShareManager() {
        return pendingShareManager;
    }

    /**
     * Gets the HyperHomes plugin supplier.
     */
    public Supplier<HyperHomes> getPlugin() {
        return plugin;
    }
}
