package com.hyperhomes.migration;

import com.hyperhomes.data.Home;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for migration sources that read home data from other plugins.
 */
public interface MigrationSource {

    /**
     * Gets the unique identifier for this migration source.
     *
     * @return the source ID (e.g., "essentials", "cmi", "custom-json")
     */
    @NotNull
    String getId();

    /**
     * Gets a human-readable name for this migration source.
     *
     * @return the display name
     */
    @NotNull
    String getDisplayName();

    /**
     * Gets the file extensions this source can read.
     *
     * @return array of supported extensions (e.g., "json", "yml", "db")
     */
    @NotNull
    String[] getSupportedExtensions();

    /**
     * Checks if this source can read the given path.
     *
     * @param path the path to check
     * @return true if this source can read the data
     */
    boolean canRead(@NotNull Path path);

    /**
     * Reads all player homes from the given path.
     *
     * @param path the path to read from (file or directory)
     * @return a future containing a map of player UUID to list of homes
     */
    CompletableFuture<Map<UUID, List<Home>>> readHomes(@NotNull Path path);

    /**
     * Gets instructions for how to use this migration source.
     *
     * @return usage instructions
     */
    @NotNull
    default String getUsageInstructions() {
        return "Place source files in the migration folder and run /homes migrate " + getId();
    }
}
