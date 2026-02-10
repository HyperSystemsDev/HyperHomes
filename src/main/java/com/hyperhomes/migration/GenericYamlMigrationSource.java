package com.hyperhomes.migration;

import com.hyperhomes.data.Home;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migration source for YAML home data.
 * Supports Essentials, CMI, and other YAML-based home plugins.
 *
 * Note: This uses a simple YAML parser to avoid adding external dependencies.
 * It handles most common YAML structures used by home plugins.
 */
public class GenericYamlMigrationSource implements MigrationSource {

    // Simple regex patterns for YAML parsing
    private static final Pattern KEY_VALUE = Pattern.compile("^(\\s*)([^:]+):\\s*(.*)$");
    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)-\\s*(.*)$");

    @Override
    @NotNull
    public String getId() {
        return "yaml";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return "Generic YAML";
    }

    @Override
    @NotNull
    public String[] getSupportedExtensions() {
        return new String[]{"yml", "yaml"};
    }

    @Override
    public boolean canRead(@NotNull Path path) {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                return stream.anyMatch(p -> {
                    String name = p.toString().toLowerCase();
                    return name.endsWith(".yml") || name.endsWith(".yaml");
                });
            } catch (IOException e) {
                return false;
            }
        }
        String name = path.toString().toLowerCase();
        return Files.exists(path) && (name.endsWith(".yml") || name.endsWith(".yaml"));
    }

    @Override
    public CompletableFuture<Map<UUID, List<Home>>> readHomes(@NotNull Path path) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<Home>> result = new HashMap<>();

            try {
                if (Files.isDirectory(path)) {
                    // Process all YAML files in directory
                    try (var stream = Files.list(path)) {
                        stream.filter(p -> {
                            String name = p.toString().toLowerCase();
                            return name.endsWith(".yml") || name.endsWith(".yaml");
                        }).forEach(file -> processYamlFile(file, result));
                    }
                } else {
                    // Process single file
                    processYamlFile(path, result);
                }
            } catch (IOException e) {
                Logger.severe("Failed to read YAML files", e);
            }

            return result;
        });
    }

    private void processYamlFile(Path file, Map<UUID, List<Home>> result) {
        try {
            Map<String, Object> data = parseYaml(file);

            // Try to determine the player UUID from filename
            String fileName = file.getFileName().toString();
            String baseName = fileName.replaceFirst("\\.(yml|yaml)$", "");
            UUID playerUuid = tryParseUuid(baseName);

            if (playerUuid != null) {
                // Per-player file
                List<Home> homes = parseHomesFromYaml(data);
                if (!homes.isEmpty()) {
                    result.computeIfAbsent(playerUuid, k -> new ArrayList<>()).addAll(homes);
                }
            } else if (looksLikeMultiPlayerFile(data)) {
                // Multi-player file
                parseMultiPlayerYaml(data, result);
            } else {
                // Try parsing as single player data anyway
                List<Home> homes = parseHomesFromYaml(data);
                if (!homes.isEmpty()) {
                    Logger.warn("Found homes in %s but couldn't determine player UUID", fileName);
                }
            }

        } catch (Exception e) {
            Logger.warn("Failed to parse YAML file %s: %s", file.getFileName(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Home> parseHomesFromYaml(Map<String, Object> data) {
        List<Home> homes = new ArrayList<>();

        // Try various common structures

        // Essentials format: homes: { homeName: { world: ..., x: ... } }
        Object homesObj = data.get("homes");
        if (homesObj == null) {
            homesObj = data.get("home");
        }
        if (homesObj == null) {
            homesObj = data.get("Homes");
        }

        if (homesObj instanceof Map) {
            Map<String, Object> homesMap = (Map<String, Object>) homesObj;
            for (Map.Entry<String, Object> entry : homesMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Home home = parseHomeFromMap(entry.getKey(), (Map<String, Object>) entry.getValue());
                    if (home != null) {
                        homes.add(home);
                    }
                }
            }
        }

        return homes;
    }

    private Home parseHomeFromMap(String name, Map<String, Object> map) {
        try {
            String world = getStringValue(map, "world", "world-name", "World");
            if (world == null) return null;

            Double x = getDoubleValue(map, "x", "X");
            Double y = getDoubleValue(map, "y", "Y");
            Double z = getDoubleValue(map, "z", "Z");

            if (x == null || y == null || z == null) {
                // Try Essentials "coords" format
                Object coords = map.get("coords");
                if (coords instanceof Map) {
                    Map<String, Object> coordsMap = (Map<String, Object>) coords;
                    x = getDoubleValue(coordsMap, "x");
                    y = getDoubleValue(coordsMap, "y");
                    z = getDoubleValue(coordsMap, "z");
                }

                // Try CMI "Loc" string format
                String loc = getStringValue(map, "Loc", "loc", "location");
                if (loc != null && x == null) {
                    String[] parts = loc.split(";");
                    if (parts.length >= 4) {
                        if (world == null) world = parts[0];
                        x = Double.parseDouble(parts[1]);
                        y = Double.parseDouble(parts[2]);
                        z = Double.parseDouble(parts[3]);
                        if (parts.length > 4) {
                            float yaw = Float.parseFloat(parts[4]);
                            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
                            return Home.create(name, world, x, y, z, yaw, pitch);
                        }
                    }
                }

                if (x == null || y == null || z == null) return null;
            }

            Float yaw = getFloatValue(map, "yaw", "Yaw");
            Float pitch = getFloatValue(map, "pitch", "Pitch");

            if (yaw == null) yaw = 0f;
            if (pitch == null) pitch = 0f;

            return Home.create(name, world, x, y, z, yaw, pitch);

        } catch (Exception e) {
            Logger.debug("Failed to parse home '%s': %s", name, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void parseMultiPlayerYaml(Map<String, Object> data, Map<UUID, List<Home>> result) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            UUID playerUuid = tryParseUuid(entry.getKey());
            if (playerUuid == null) continue;

            if (entry.getValue() instanceof Map) {
                List<Home> homes = parseHomesFromYaml((Map<String, Object>) entry.getValue());
                if (!homes.isEmpty()) {
                    result.computeIfAbsent(playerUuid, k -> new ArrayList<>()).addAll(homes);
                }
            }
        }
    }

    private boolean looksLikeMultiPlayerFile(Map<String, Object> data) {
        int uuidKeys = 0;
        for (String key : data.keySet()) {
            if (tryParseUuid(key) != null) {
                uuidKeys++;
            }
        }
        return uuidKeys > 0 && uuidKeys == data.size();
    }

    /**
     * Simple YAML parser - handles basic nested structures.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(Path file) throws IOException {
        Map<String, Object> root = new HashMap<>();
        List<Map<String, Object>> stack = new ArrayList<>();
        List<Integer> indentStack = new ArrayList<>();
        List<String> keyStack = new ArrayList<>();

        stack.add(root);
        indentStack.add(-1);
        keyStack.add("");

        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip comments and empty lines
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                Matcher keyMatcher = KEY_VALUE.matcher(line);
                if (keyMatcher.matches()) {
                    int indent = keyMatcher.group(1).length();
                    String key = keyMatcher.group(2).trim();
                    String value = keyMatcher.group(3).trim();

                    // Remove quotes from key
                    key = removeQuotes(key);

                    // Pop stack to find parent
                    while (indentStack.size() > 1 && indent <= indentStack.get(indentStack.size() - 1)) {
                        stack.remove(stack.size() - 1);
                        indentStack.remove(indentStack.size() - 1);
                        keyStack.remove(keyStack.size() - 1);
                    }

                    Map<String, Object> current = stack.get(stack.size() - 1);

                    if (value.isEmpty()) {
                        // Nested object
                        Map<String, Object> nested = new HashMap<>();
                        current.put(key, nested);
                        stack.add(nested);
                        indentStack.add(indent);
                        keyStack.add(key);
                    } else {
                        // Value
                        current.put(key, parseValue(value));
                    }
                }
            }
        }

        return root;
    }

    private Object parseValue(String value) {
        if (value == null || value.isEmpty()) return null;

        // Remove quotes
        value = removeQuotes(value);

        // Try to parse as number
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException ignored) {}

        // Boolean
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;

        // Null
        if (value.equalsIgnoreCase("null") || value.equals("~")) return null;

        return value;
    }

    private String removeQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Nullable
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    @Nullable
    private Double getDoubleValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            } else if (value != null) {
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    @Nullable
    private Float getFloatValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            } else if (value != null) {
                try {
                    return Float.parseFloat(value.toString());
                } catch (NumberFormatException ignored) {}
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
            Generic YAML Migration:

            Place YAML files in: config/hyperhomes/migration/

            Supported formats:
            1. Per-player files: {uuid}.yml with homes inside
            2. Essentials userdata: { homes: { homeName: { world: ..., x: ... } } }
            3. CMI format: { Homes: { homeName: { Loc: "world;x;y;z;yaw;pitch" } } }

            Then run: /homes migrate yaml""";
    }
}
