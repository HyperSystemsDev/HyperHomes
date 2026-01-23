package com.hyperhomes.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hyperhomes.manager.HomeManager;
import com.hyperhomes.model.Home;
import com.hyperhomes.model.PlayerHomes;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages the migration of home data from other plugins to HyperHomes.
 */
public class MigrationManager {

    private final Path dataDir;
    private final Path migrationDir;
    private final Path backupDir;
    private final HomeManager homeManager;
    private final Map<String, MigrationSource> sources;
    private final Gson gson;

    /**
     * Creates a new MigrationManager.
     *
     * @param dataDir     the plugin data directory
     * @param homeManager the home manager
     */
    public MigrationManager(@NotNull Path dataDir, @NotNull HomeManager homeManager) {
        this.dataDir = dataDir;
        this.migrationDir = dataDir.resolve("migration");
        this.backupDir = dataDir.resolve("backups");
        this.homeManager = homeManager;
        this.sources = new HashMap<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();

        // Register built-in migration sources
        registerSource(new GenericJsonMigrationSource());
        registerSource(new GenericYamlMigrationSource());
        registerSource(new GenericSqliteMigrationSource());
    }

    /**
     * Initializes the migration system.
     */
    public void init() {
        try {
            Files.createDirectories(migrationDir);
            Files.createDirectories(backupDir);
            Logger.info("Migration system initialized");
        } catch (IOException e) {
            Logger.severe("Failed to create migration directories", e);
        }
    }

    /**
     * Registers a migration source.
     *
     * @param source the source to register
     */
    public void registerSource(@NotNull MigrationSource source) {
        sources.put(source.getId().toLowerCase(), source);
        Logger.debug("Registered migration source: %s", source.getId());
    }

    /**
     * Gets all registered migration sources.
     *
     * @return list of sources
     */
    @NotNull
    public List<MigrationSource> getSources() {
        return new ArrayList<>(sources.values());
    }

    /**
     * Gets a migration source by ID.
     *
     * @param id the source ID
     * @return the source, or null if not found
     */
    @Nullable
    public MigrationSource getSource(@NotNull String id) {
        return sources.get(id.toLowerCase());
    }

    /**
     * Gets the migration directory where source files should be placed.
     *
     * @return the migration directory path
     */
    @NotNull
    public Path getMigrationDir() {
        return migrationDir;
    }

    /**
     * Creates a backup of existing HyperHomes data.
     *
     * @return the backup directory path, or null if backup failed
     */
    @Nullable
    public Path createBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path backupPath = backupDir.resolve("backup_" + timestamp);

        try {
            Files.createDirectories(backupPath);

            // Copy players directory
            Path playersDir = dataDir.resolve("players");
            if (Files.exists(playersDir)) {
                Path backupPlayers = backupPath.resolve("players");
                Files.createDirectories(backupPlayers);

                try (var stream = Files.list(playersDir)) {
                    stream.forEach(file -> {
                        try {
                            Files.copy(file, backupPlayers.resolve(file.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            Logger.warn("Failed to backup file: %s", file.getFileName());
                        }
                    });
                }
            }

            Logger.info("Created backup at: %s", backupPath);
            return backupPath;
        } catch (IOException e) {
            Logger.severe("Failed to create backup", e);
            return null;
        }
    }

    /**
     * Performs a migration from the specified source.
     *
     * @param sourceId        the migration source ID
     * @param duplicatePolicy how to handle duplicate home names
     * @param progressCallback callback for progress updates
     * @return a future containing the migration result
     */
    public CompletableFuture<MigrationResult> migrate(
            @NotNull String sourceId,
            @NotNull DuplicatePolicy duplicatePolicy,
            @Nullable Consumer<String> progressCallback) {

        return CompletableFuture.supplyAsync(() -> {
            MigrationResult.Builder result = new MigrationResult.Builder();

            // Find the source
            MigrationSource source = getSource(sourceId);
            if (source == null) {
                return MigrationResult.failure("Unknown migration source: " + sourceId);
            }

            // Report progress
            if (progressCallback != null) {
                progressCallback.accept("Starting migration from " + source.getDisplayName() + "...");
            }

            // Find source files
            Path sourcePath = findSourcePath(source);
            if (sourcePath == null) {
                return MigrationResult.failure(
                    "No compatible files found in migration folder. " +
                    "Place files in: config/hyperhomes/migration/");
            }

            // Check if source can read the path
            if (!source.canRead(sourcePath)) {
                return MigrationResult.failure("Migration source cannot read the provided files.");
            }

            // Create backup
            if (progressCallback != null) {
                progressCallback.accept("Creating backup of existing data...");
            }
            Path backup = createBackup();
            if (backup == null) {
                result.addWarning("Failed to create backup - proceeding anyway");
            }

            // Read homes from source
            if (progressCallback != null) {
                progressCallback.accept("Reading homes from " + source.getDisplayName() + "...");
            }

            Map<UUID, List<Home>> importedHomes;
            try {
                importedHomes = source.readHomes(sourcePath).join();
            } catch (Exception e) {
                Logger.severe("Failed to read homes from source", e);
                return MigrationResult.failure("Failed to read homes: " + e.getMessage());
            }

            if (importedHomes.isEmpty()) {
                return MigrationResult.failure("No homes found in source data.");
            }

            // Process each player
            if (progressCallback != null) {
                progressCallback.accept("Importing homes for " + importedHomes.size() + " players...");
            }

            for (Map.Entry<UUID, List<Home>> entry : importedHomes.entrySet()) {
                UUID playerUuid = entry.getKey();
                List<Home> homes = entry.getValue();

                result.incrementPlayers();

                for (Home home : homes) {
                    try {
                        boolean imported = importHome(playerUuid, home, duplicatePolicy, result);
                        if (imported) {
                            result.incrementImported();
                        } else {
                            result.incrementSkipped();
                        }
                    } catch (Exception e) {
                        result.incrementFailed();
                        result.addError("Failed to import home '" + home.name() +
                            "' for player " + playerUuid + ": " + e.getMessage());
                    }
                }
            }

            // Save all data
            if (progressCallback != null) {
                progressCallback.accept("Saving imported data...");
            }
            homeManager.saveAll().join();

            if (progressCallback != null) {
                progressCallback.accept("Migration complete!");
            }

            return result.build();
        });
    }

    /**
     * Imports a single home for a player.
     *
     * @param playerUuid      the player's UUID
     * @param home            the home to import
     * @param duplicatePolicy how to handle duplicates
     * @param result          the result builder for tracking
     * @return true if the home was imported
     */
    private boolean importHome(UUID playerUuid, Home home, DuplicatePolicy duplicatePolicy,
                               MigrationResult.Builder result) {
        // Get or create player homes (we'll need to handle this differently since
        // the player may not be online/cached)
        PlayerHomes playerHomes = homeManager.getPlayerHomes(playerUuid);

        // If player not cached, we need to create a temporary entry
        // The home will be saved and can be loaded when the player joins
        if (playerHomes == null) {
            // For uncached players, we save directly to storage
            return importHomeToStorage(playerUuid, home, duplicatePolicy, result);
        }

        // Check for duplicate
        Home existing = playerHomes.getHome(home.name());
        if (existing != null) {
            switch (duplicatePolicy) {
                case SKIP:
                    result.addWarning("Skipped duplicate home '" + home.name() +
                        "' for player " + playerUuid);
                    return false;

                case RENAME:
                    String newName = findUniqueName(playerHomes, home.name());
                    home = home.withName(newName);
                    result.addWarning("Renamed home to '" + newName +
                        "' for player " + playerUuid);
                    break;

                case OVERWRITE:
                    // Will overwrite below
                    result.addWarning("Overwrote existing home '" + home.name() +
                        "' for player " + playerUuid);
                    break;
            }
        }

        // Import the home (bypass limit for migration)
        homeManager.setHomeBypassing(playerUuid, home);
        return true;
    }

    /**
     * Imports a home directly to storage for uncached players.
     */
    private boolean importHomeToStorage(UUID playerUuid, Home home, DuplicatePolicy duplicatePolicy,
                                        MigrationResult.Builder result) {
        Path playerFile = dataDir.resolve("players").resolve(playerUuid.toString() + ".json");

        try {
            PlayerHomes playerHomes;
            if (Files.exists(playerFile)) {
                // Load existing data
                String json = Files.readString(playerFile);
                playerHomes = parsePlayerHomes(playerUuid, json);
            } else {
                // Create new player homes
                playerHomes = new PlayerHomes(playerUuid, "MigratedPlayer");
            }

            // Check for duplicate
            Home existing = playerHomes.getHome(home.name());
            if (existing != null) {
                switch (duplicatePolicy) {
                    case SKIP:
                        result.addWarning("Skipped duplicate home '" + home.name() +
                            "' for player " + playerUuid);
                        return false;

                    case RENAME:
                        String newName = findUniqueName(playerHomes, home.name());
                        home = home.withName(newName);
                        result.addWarning("Renamed home to '" + newName +
                            "' for player " + playerUuid);
                        break;

                    case OVERWRITE:
                        result.addWarning("Overwrote existing home '" + home.name() +
                            "' for player " + playerUuid);
                        break;
                }
            }

            // Add home and save
            playerHomes.setHome(home);
            savePlayerHomes(playerHomes);
            return true;

        } catch (Exception e) {
            Logger.severe("Failed to import home to storage for player %s", e, playerUuid);
            return false;
        }
    }

    /**
     * Finds a unique name for a home by appending numbers.
     */
    private String findUniqueName(PlayerHomes playerHomes, String baseName) {
        String newName = baseName;
        int counter = 1;
        while (playerHomes.hasHome(newName)) {
            newName = baseName + "_" + counter++;
            if (counter > 100) {
                // Safety limit
                return baseName + "_" + System.currentTimeMillis();
            }
        }
        return newName;
    }

    /**
     * Finds the appropriate source path in the migration directory.
     */
    @Nullable
    private Path findSourcePath(MigrationSource source) {
        try {
            if (!Files.exists(migrationDir)) {
                return null;
            }

            // Check if migration directory itself can be read
            if (source.canRead(migrationDir)) {
                return migrationDir;
            }

            // Look for files with supported extensions
            for (String ext : source.getSupportedExtensions()) {
                try (var stream = Files.list(migrationDir)) {
                    var match = stream
                        .filter(p -> p.toString().toLowerCase().endsWith("." + ext))
                        .findFirst();
                    if (match.isPresent()) {
                        return migrationDir;
                    }
                }
            }

            return null;
        } catch (IOException e) {
            Logger.warn("Error scanning migration directory", e);
            return null;
        }
    }

    /**
     * Parses player homes from JSON.
     */
    private PlayerHomes parsePlayerHomes(UUID uuid, String json) {
        // Use Gson to parse - similar to JsonStorageProvider
        var root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        String username = root.has("username") ? root.get("username").getAsString() : "Unknown";
        long lastTeleport = root.has("lastTeleport") ? root.get("lastTeleport").getAsLong() : 0;

        Map<String, Home> homes = new HashMap<>();
        if (root.has("homes") && root.get("homes").isJsonObject()) {
            var homesObj = root.getAsJsonObject("homes");
            for (String homeName : homesObj.keySet()) {
                var homeObj = homesObj.getAsJsonObject(homeName);
                homes.put(homeName.toLowerCase(), parseHome(homeObj));
            }
        }

        return new PlayerHomes(uuid, username, homes, lastTeleport);
    }

    /**
     * Parses a single home from JSON.
     */
    private Home parseHome(com.google.gson.JsonObject obj) {
        java.util.Set<UUID> sharedWith = new java.util.HashSet<>();
        if (obj.has("sharedWith") && obj.get("sharedWith").isJsonArray()) {
            for (var element : obj.getAsJsonArray("sharedWith")) {
                try {
                    sharedWith.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        return new Home(
            obj.get("name").getAsString(),
            obj.get("world").getAsString(),
            obj.get("x").getAsDouble(),
            obj.get("y").getAsDouble(),
            obj.get("z").getAsDouble(),
            obj.get("yaw").getAsFloat(),
            obj.get("pitch").getAsFloat(),
            obj.has("createdAt") ? obj.get("createdAt").getAsLong() : System.currentTimeMillis(),
            obj.has("lastUsed") ? obj.get("lastUsed").getAsLong() : System.currentTimeMillis(),
            sharedWith
        );
    }

    /**
     * Saves player homes to JSON file.
     */
    private void savePlayerHomes(PlayerHomes playerHomes) throws IOException {
        Path playersDir = dataDir.resolve("players");
        Files.createDirectories(playersDir);

        Path playerFile = playersDir.resolve(playerHomes.getUuid().toString() + ".json");

        var root = new com.google.gson.JsonObject();
        root.addProperty("uuid", playerHomes.getUuid().toString());
        root.addProperty("username", playerHomes.getUsername());
        root.addProperty("lastTeleport", playerHomes.getLastTeleport());

        var homesObj = new com.google.gson.JsonObject();
        for (Home home : playerHomes.getHomes()) {
            homesObj.add(home.name(), serializeHome(home));
        }
        root.add("homes", homesObj);

        Files.writeString(playerFile, gson.toJson(root));
    }

    /**
     * Serializes a home to JSON.
     */
    private com.google.gson.JsonObject serializeHome(Home home) {
        var obj = new com.google.gson.JsonObject();
        obj.addProperty("name", home.name());
        obj.addProperty("world", home.world());
        obj.addProperty("x", home.x());
        obj.addProperty("y", home.y());
        obj.addProperty("z", home.z());
        obj.addProperty("yaw", home.yaw());
        obj.addProperty("pitch", home.pitch());
        obj.addProperty("createdAt", home.createdAt());
        obj.addProperty("lastUsed", home.lastUsed());

        if (!home.sharedWith().isEmpty()) {
            var sharedArray = new com.google.gson.JsonArray();
            for (UUID uuid : home.sharedWith()) {
                sharedArray.add(uuid.toString());
            }
            obj.add("sharedWith", sharedArray);
        }

        return obj;
    }

    /**
     * Policy for handling duplicate home names during migration.
     */
    public enum DuplicatePolicy {
        /**
         * Skip homes that already exist (keep existing).
         */
        SKIP,

        /**
         * Rename imported homes if they conflict.
         */
        RENAME,

        /**
         * Overwrite existing homes with imported data.
         */
        OVERWRITE
    }
}
