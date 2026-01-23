package com.hyperhomes.storage;

import com.hyperhomes.model.PlayerHomes;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for storage providers that persist player home data.
 */
public interface StorageProvider {

    /**
     * Initializes the storage provider.
     *
     * @return a future that completes when initialization is done
     */
    CompletableFuture<Void> init();

    /**
     * Shuts down the storage provider.
     *
     * @return a future that completes when shutdown is done
     */
    CompletableFuture<Void> shutdown();

    /**
     * Loads player homes from storage.
     *
     * @param uuid the player's UUID
     * @return a future containing the player homes if found
     */
    CompletableFuture<Optional<PlayerHomes>> loadPlayerHomes(@NotNull UUID uuid);

    /**
     * Saves player homes to storage.
     *
     * @param playerHomes the player homes to save
     * @return a future that completes when saving is done
     */
    CompletableFuture<Void> savePlayerHomes(@NotNull PlayerHomes playerHomes);

    /**
     * Deletes all homes for a player.
     *
     * @param uuid the player's UUID
     * @return a future that completes when deletion is done
     */
    CompletableFuture<Void> deletePlayerHomes(@NotNull UUID uuid);
}
