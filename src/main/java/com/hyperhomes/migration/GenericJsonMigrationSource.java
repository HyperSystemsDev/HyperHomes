package com.hyperhomes.migration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyperhomes.model.Home;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Migration source for generic JSON home data.
 * Supports various JSON formats from different home plugins.
 *
 * Supported formats:
 * 1. Per-player files: Each player has their own .json file named by UUID
 * 2. Single file with all players: homes.json with player UUIDs as keys
 * 3. Essentials-like format: userdata/{uuid}.json with home data inside
 */
public class GenericJsonMigrationSource implements MigrationSource {

    @Override
    @NotNull
    public String getId() {
        return "json";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return "Generic JSON";
    }

    @Override
    @NotNull
    public String[] getSupportedExtensions() {
        return new String[]{"json"};
    }

    @Override
    public boolean canRead(@NotNull Path path) {
        if (Files.isDirectory(path)) {
            // Check if directory contains .json files
            try (var stream = Files.list(path)) {
                return stream.anyMatch(p -> p.toString().toLowerCase().endsWith(".json"));
            } catch (IOException e) {
                return false;
            }
        }
        return Files.exists(path) && path.toString().toLowerCase().endsWith(".json");
    }

    @Override
    public CompletableFuture<Map<UUID, List<Home>>> readHomes(@NotNull Path path) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<Home>> result = new HashMap<>();

            try {
                if (Files.isDirectory(path)) {
                    // Process all JSON files in directory
                    try (var stream = Files.list(path)) {
                        stream.filter(p -> p.toString().toLowerCase().endsWith(".json"))
                              .forEach(file -> processJsonFile(file, result));
                    }
                } else {
                    // Process single file
                    processJsonFile(path, result);
                }
            } catch (IOException e) {
                Logger.severe("Failed to read JSON files", e);
            }

            return result;
        });
    }

    private void processJsonFile(Path file, Map<UUID, List<Home>> result) {
        try {
            String content = Files.readString(file);
            JsonElement root = JsonParser.parseString(content);

            if (!root.isJsonObject()) {
                Logger.warn("Skipping non-object JSON file: %s", file.getFileName());
                return;
            }

            JsonObject obj = root.getAsJsonObject();

            // Try to detect the format and parse accordingly

            // Format 1: File named as UUID (per-player file)
            String fileName = file.getFileName().toString();
            if (fileName.endsWith(".json")) {
                String baseName = fileName.substring(0, fileName.length() - 5);
                UUID playerUuid = tryParseUuid(baseName);

                if (playerUuid != null) {
                    // Per-player file
                    List<Home> homes = parseHomesFromPlayerFile(obj, playerUuid);
                    if (!homes.isEmpty()) {
                        result.computeIfAbsent(playerUuid, k -> new ArrayList<>()).addAll(homes);
                    }
                    return;
                }
            }

            // Format 2: Single file with UUID keys (all players)
            if (looksLikeMultiPlayerFile(obj)) {
                parseMultiPlayerFile(obj, result);
                return;
            }

            // Format 3: Try parsing as a single player's data
            List<Home> homes = parseHomesFromPlayerFile(obj, null);
            if (!homes.isEmpty()) {
                // We don't have a UUID, generate a warning
                Logger.warn("Found homes in %s but couldn't determine player UUID", file.getFileName());
            }

        } catch (Exception e) {
            Logger.warn("Failed to parse JSON file %s: %s", file.getFileName(), e.getMessage());
        }
    }

    private boolean looksLikeMultiPlayerFile(JsonObject obj) {
        // Check if all top-level keys look like UUIDs
        int uuidKeys = 0;
        int totalKeys = 0;
        for (String key : obj.keySet()) {
            totalKeys++;
            if (tryParseUuid(key) != null) {
                uuidKeys++;
            }
        }
        return totalKeys > 0 && uuidKeys == totalKeys;
    }

    private void parseMultiPlayerFile(JsonObject obj, Map<UUID, List<Home>> result) {
        for (String key : obj.keySet()) {
            UUID playerUuid = tryParseUuid(key);
            if (playerUuid == null) continue;

            JsonElement playerData = obj.get(key);
            if (!playerData.isJsonObject()) continue;

            List<Home> homes = parseHomesFromPlayerFile(playerData.getAsJsonObject(), playerUuid);
            if (!homes.isEmpty()) {
                result.computeIfAbsent(playerUuid, k -> new ArrayList<>()).addAll(homes);
            }
        }
    }

    private List<Home> parseHomesFromPlayerFile(JsonObject obj, UUID fallbackUuid) {
        List<Home> homes = new ArrayList<>();

        // Try various common home data locations

        // Format: { "homes": { "homeName": {...} } }
        if (obj.has("homes") && obj.get("homes").isJsonObject()) {
            parseHomesObject(obj.getAsJsonObject("homes"), homes);
            return homes;
        }

        // Format: { "home": { "homeName": {...} } } (singular)
        if (obj.has("home") && obj.get("home").isJsonObject()) {
            parseHomesObject(obj.getAsJsonObject("home"), homes);
            return homes;
        }

        // Essentials format: { "homes": { "homeName": { "world": ..., "x": ... } } }
        // Already covered above

        // CMI format: { "Homes": { "homeName": { "Loc": "world;x;y;z;yaw;pitch" } } }
        if (obj.has("Homes") && obj.get("Homes").isJsonObject()) {
            parseCmiHomes(obj.getAsJsonObject("Homes"), homes);
            return homes;
        }

        // Try parsing the object itself as a homes map
        if (hasHomeStructure(obj)) {
            parseHomesObject(obj, homes);
        }

        return homes;
    }

    private void parseHomesObject(JsonObject homesObj, List<Home> homes) {
        for (String homeName : homesObj.keySet()) {
            JsonElement homeElement = homesObj.get(homeName);
            if (!homeElement.isJsonObject()) continue;

            Home home = parseHome(homeName, homeElement.getAsJsonObject());
            if (home != null) {
                homes.add(home);
            }
        }
    }

    private Home parseHome(String name, JsonObject obj) {
        try {
            // Check for nested location object (Hytale Homes format)
            JsonObject locationObj = obj;
            if (obj.has("location") && obj.get("location").isJsonObject()) {
                locationObj = obj.getAsJsonObject("location");
            }

            String world = getStringField(locationObj, "world", "world_name", "World");
            if (world == null) return null;

            Double x = getDoubleField(locationObj, "x", "X", "posX");
            Double y = getDoubleField(locationObj, "y", "Y", "posY");
            Double z = getDoubleField(locationObj, "z", "Z", "posZ");

            if (x == null || y == null || z == null) return null;

            Float yaw = getFloatField(locationObj, "yaw", "Yaw", "rotYaw");
            Float pitch = getFloatField(locationObj, "pitch", "Pitch", "rotPitch");

            if (yaw == null) yaw = 0f;
            if (pitch == null) pitch = 0f;

            // Use name from "name" field if present, otherwise use the key
            if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
                name = obj.get("name").getAsString();
            }

            // Try to get timestamps
            long createdAt = System.currentTimeMillis();
            long lastUsed = System.currentTimeMillis();

            if (obj.has("createdAt")) {
                createdAt = obj.get("createdAt").getAsLong();
            } else if (obj.has("timestamp")) {
                createdAt = obj.get("timestamp").getAsLong();
            }

            if (obj.has("lastUsed")) {
                lastUsed = obj.get("lastUsed").getAsLong();
            } else if (obj.has("lastTeleport")) {
                lastUsed = obj.get("lastTeleport").getAsLong();
            }

            // Get shared players if present
            Set<UUID> sharedWith = new HashSet<>();
            if (obj.has("sharedWith") && obj.get("sharedWith").isJsonArray()) {
                for (var elem : obj.getAsJsonArray("sharedWith")) {
                    UUID uuid = tryParseUuid(elem.getAsString());
                    if (uuid != null) {
                        sharedWith.add(uuid);
                    }
                }
            }

            return new Home(name, world, x, y, z, yaw, pitch, createdAt, lastUsed, sharedWith);

        } catch (Exception e) {
            Logger.debug("Failed to parse home '%s': %s", name, e.getMessage());
            return null;
        }
    }

    private void parseCmiHomes(JsonObject homesObj, List<Home> homes) {
        for (String homeName : homesObj.keySet()) {
            JsonElement homeElement = homesObj.get(homeName);
            if (!homeElement.isJsonObject()) continue;

            JsonObject homeObj = homeElement.getAsJsonObject();

            // CMI uses "Loc" field with format "world;x;y;z;yaw;pitch"
            if (homeObj.has("Loc")) {
                String loc = homeObj.get("Loc").getAsString();
                String[] parts = loc.split(";");
                if (parts.length >= 4) {
                    try {
                        String world = parts[0];
                        double x = Double.parseDouble(parts[1]);
                        double y = Double.parseDouble(parts[2]);
                        double z = Double.parseDouble(parts[3]);
                        float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
                        float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;

                        homes.add(Home.create(homeName, world, x, y, z, yaw, pitch));
                    } catch (NumberFormatException e) {
                        Logger.debug("Failed to parse CMI home location: %s", loc);
                    }
                }
            }
        }
    }

    private boolean hasHomeStructure(JsonObject obj) {
        // Check if any key looks like a home entry (has world/x/y/z)
        for (String key : obj.keySet()) {
            JsonElement elem = obj.get(key);
            if (elem.isJsonObject()) {
                JsonObject child = elem.getAsJsonObject();
                if (child.has("world") && child.has("x") && child.has("y") && child.has("z")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String getStringField(JsonObject obj, String... names) {
        for (String name : names) {
            if (obj.has(name)) {
                JsonElement elem = obj.get(name);
                if (elem.isJsonPrimitive()) {
                    return elem.getAsString();
                }
            }
        }
        return null;
    }

    private Double getDoubleField(JsonObject obj, String... names) {
        for (String name : names) {
            if (obj.has(name)) {
                JsonElement elem = obj.get(name);
                if (elem.isJsonPrimitive()) {
                    return elem.getAsDouble();
                }
            }
        }
        return null;
    }

    private Float getFloatField(JsonObject obj, String... names) {
        for (String name : names) {
            if (obj.has(name)) {
                JsonElement elem = obj.get(name);
                if (elem.isJsonPrimitive()) {
                    return elem.getAsFloat();
                }
            }
        }
        return null;
    }

    private UUID tryParseUuid(String str) {
        if (str == null) return null;
        try {
            // Handle UUIDs with or without dashes
            if (str.length() == 32) {
                str = str.substring(0, 8) + "-" + str.substring(8, 12) + "-" +
                      str.substring(12, 16) + "-" + str.substring(16, 20) + "-" +
                      str.substring(20);
            }
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @NotNull
    public String getUsageInstructions() {
        return """
            Generic JSON Migration:

            Place JSON files in: config/hyperhomes/migration/

            Supported formats:
            1. Per-player files: {uuid}.json with homes inside
            2. All players in one file: homes.json with UUID keys
            3. Essentials-like: { "homes": { "homeName": { "world": ..., "x": ... } } }
            4. CMI-like: { "Homes": { "homeName": { "Loc": "world;x;y;z;yaw;pitch" } } }

            Then run: /homes migrate json""";
    }
}
