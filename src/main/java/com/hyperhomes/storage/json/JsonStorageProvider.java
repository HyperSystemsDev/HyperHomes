package com.hyperhomes.storage.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperhomes.model.Home;
import com.hyperhomes.model.PlayerHomes;
import com.hyperhomes.storage.StorageProvider;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonArray;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * JSON file-based storage provider.
 * Stores player homes in individual JSON files.
 */
public class JsonStorageProvider implements StorageProvider {

    private final Path dataDir;
    private final Path playersDir;
    private final Gson gson;

    /**
     * Creates a new JSON storage provider.
     *
     * @param dataDir the plugin data directory
     */
    public JsonStorageProvider(@NotNull Path dataDir) {
        this.dataDir = dataDir;
        this.playersDir = dataDir.resolve("players");
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    }

    @Override
    public CompletableFuture<Void> init() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(playersDir);
                Logger.info("JSON storage initialized at %s", playersDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create storage directories", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Optional<PlayerHomes>> loadPlayerHomes(@NotNull UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Path playerFile = playersDir.resolve(uuid.toString() + ".json");

            if (!Files.exists(playerFile)) {
                return Optional.empty();
            }

            try {
                String json = Files.readString(playerFile);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                String username = root.has("username") ? root.get("username").getAsString() : "Unknown";
                long lastTeleport = root.has("lastTeleport") ? root.get("lastTeleport").getAsLong() : 0;

                Map<String, Home> homes = new HashMap<>();
                if (root.has("homes") && root.get("homes").isJsonObject()) {
                    JsonObject homesObj = root.getAsJsonObject("homes");
                    for (String homeName : homesObj.keySet()) {
                        JsonObject homeObj = homesObj.getAsJsonObject(homeName);
                        Home home = deserializeHome(homeObj);
                        homes.put(homeName.toLowerCase(), home);
                    }
                }

                return Optional.of(new PlayerHomes(uuid, username, homes, lastTeleport));
            } catch (Exception e) {
                Logger.severe("Failed to load player homes for %s", e, uuid);
                return Optional.empty();
            }
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerHomes(@NotNull PlayerHomes playerHomes) {
        return CompletableFuture.runAsync(() -> {
            Path playerFile = playersDir.resolve(playerHomes.getUuid().toString() + ".json");

            try {
                JsonObject root = new JsonObject();
                root.addProperty("uuid", playerHomes.getUuid().toString());
                root.addProperty("username", playerHomes.getUsername());
                root.addProperty("lastTeleport", playerHomes.getLastTeleport());

                JsonObject homesObj = new JsonObject();
                for (Home home : playerHomes.getHomes()) {
                    homesObj.add(home.name(), serializeHome(home));
                }
                root.add("homes", homesObj);

                Files.writeString(playerFile, gson.toJson(root));
                Logger.debug("Saved homes for %s", playerHomes.getUsername());
            } catch (IOException e) {
                Logger.severe("Failed to save player homes for %s", e, playerHomes.getUuid());
            }
        });
    }

    @Override
    public CompletableFuture<Void> deletePlayerHomes(@NotNull UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            Path playerFile = playersDir.resolve(uuid.toString() + ".json");
            try {
                Files.deleteIfExists(playerFile);
                Logger.debug("Deleted homes for %s", uuid);
            } catch (IOException e) {
                Logger.severe("Failed to delete player homes for %s", e, uuid);
            }
        });
    }

    private JsonObject serializeHome(Home home) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", home.name());
        obj.addProperty("world", home.world());
        obj.addProperty("x", home.x());
        obj.addProperty("y", home.y());
        obj.addProperty("z", home.z());
        obj.addProperty("yaw", home.yaw());
        obj.addProperty("pitch", home.pitch());
        obj.addProperty("createdAt", home.createdAt());
        obj.addProperty("lastUsed", home.lastUsed());

        // Serialize shared players
        if (!home.sharedWith().isEmpty()) {
            JsonArray sharedArray = new JsonArray();
            for (UUID sharedUuid : home.sharedWith()) {
                sharedArray.add(sharedUuid.toString());
            }
            obj.add("sharedWith", sharedArray);
        }

        return obj;
    }

    private Home deserializeHome(JsonObject obj) {
        // Deserialize shared players
        Set<UUID> sharedWith = new HashSet<>();
        if (obj.has("sharedWith") && obj.get("sharedWith").isJsonArray()) {
            JsonArray sharedArray = obj.getAsJsonArray("sharedWith");
            for (var element : sharedArray) {
                try {
                    sharedWith.add(UUID.fromString(element.getAsString()));
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid UUIDs
                }
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
}
