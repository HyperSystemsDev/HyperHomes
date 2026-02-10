package com.hyperhomes.migration;

import com.hyperhomes.data.Home;
import com.hyperhomes.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Migration source for SQLite database files.
 * Supports various home plugin database schemas.
 */
public class GenericSqliteMigrationSource implements MigrationSource {

    @Override
    @NotNull
    public String getId() {
        return "sqlite";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return "SQLite Database";
    }

    @Override
    @NotNull
    public String[] getSupportedExtensions() {
        return new String[]{"db", "sqlite", "sqlite3"};
    }

    @Override
    public boolean canRead(@NotNull Path path) {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                return stream.anyMatch(this::isSqliteFile);
            } catch (IOException e) {
                return false;
            }
        }
        return isSqliteFile(path);
    }

    private boolean isSqliteFile(Path path) {
        String name = path.toString().toLowerCase();
        return Files.exists(path) && (
            name.endsWith(".db") ||
            name.endsWith(".sqlite") ||
            name.endsWith(".sqlite3")
        );
    }

    @Override
    public CompletableFuture<Map<UUID, List<Home>>> readHomes(@NotNull Path path) {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<Home>> result = new HashMap<>();

            try {
                // Ensure SQLite driver is loaded
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                Logger.severe("SQLite JDBC driver not found. Add sqlite-jdbc to your dependencies.");
                return result;
            }

            try {
                if (Files.isDirectory(path)) {
                    // Process all SQLite files in directory
                    try (var stream = Files.list(path)) {
                        stream.filter(this::isSqliteFile)
                              .forEach(file -> processSqliteFile(file, result));
                    }
                } else {
                    // Process single file
                    processSqliteFile(path, result);
                }
            } catch (IOException e) {
                Logger.severe("Failed to read SQLite files", e);
            }

            return result;
        });
    }

    private void processSqliteFile(Path file, Map<UUID, List<Home>> result) {
        String url = "jdbc:sqlite:" + file.toAbsolutePath();

        try (Connection conn = DriverManager.getConnection(url)) {
            // Find homes table
            String homesTable = findHomesTable(conn);
            if (homesTable == null) {
                Logger.warn("No homes table found in %s", file.getFileName());
                return;
            }

            Logger.info("Found homes table '%s' in %s", homesTable, file.getFileName());

            // Analyze table structure
            Map<String, String> columnMap = analyzeTableStructure(conn, homesTable);
            if (columnMap.isEmpty()) {
                Logger.warn("Could not map columns in table %s", homesTable);
                return;
            }

            // Read homes
            readHomesFromTable(conn, homesTable, columnMap, result);

        } catch (SQLException e) {
            Logger.warn("Failed to read SQLite database %s: %s", file.getFileName(), e.getMessage());
        }
    }

    private String findHomesTable(Connection conn) throws SQLException {
        // Common table names for home plugins
        String[] tableNames = {
            "homes", "player_homes", "playerhomes",
            "home", "essentials_homes", "cmi_homes",
            "warps_homes", "sethome", "sethomes"
        };

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table'")) {

            Set<String> existingTables = new HashSet<>();
            while (rs.next()) {
                existingTables.add(rs.getString("name").toLowerCase());
            }

            // Try known table names
            for (String name : tableNames) {
                if (existingTables.contains(name)) {
                    return name;
                }
            }

            // Try to find any table that looks like homes
            for (String table : existingTables) {
                if (table.contains("home")) {
                    return table;
                }
            }

            return null;
        }
    }

    private Map<String, String> analyzeTableStructure(Connection conn, String tableName) throws SQLException {
        Map<String, String> columnMap = new HashMap<>();

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " LIMIT 1")) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                String colName = meta.getColumnName(i).toLowerCase();

                // Map column names to our expected fields
                if (matches(colName, "uuid", "player_uuid", "playeruuid", "player_id", "playerid", "owner")) {
                    columnMap.put("uuid", meta.getColumnName(i));
                } else if (matches(colName, "name", "home_name", "homename")) {
                    columnMap.put("name", meta.getColumnName(i));
                } else if (matches(colName, "world", "world_name", "worldname")) {
                    columnMap.put("world", meta.getColumnName(i));
                } else if (matches(colName, "x", "pos_x", "posx", "coord_x", "coordx")) {
                    columnMap.put("x", meta.getColumnName(i));
                } else if (matches(colName, "y", "pos_y", "posy", "coord_y", "coordy")) {
                    columnMap.put("y", meta.getColumnName(i));
                } else if (matches(colName, "z", "pos_z", "posz", "coord_z", "coordz")) {
                    columnMap.put("z", meta.getColumnName(i));
                } else if (matches(colName, "yaw", "rotation_yaw", "rotationyaw")) {
                    columnMap.put("yaw", meta.getColumnName(i));
                } else if (matches(colName, "pitch", "rotation_pitch", "rotationpitch")) {
                    columnMap.put("pitch", meta.getColumnName(i));
                } else if (matches(colName, "location", "loc", "position")) {
                    // Some plugins store location as a single string
                    columnMap.put("location", meta.getColumnName(i));
                }
            }

            // Check if we have minimum required columns
            boolean hasCoords = columnMap.containsKey("x") && columnMap.containsKey("y") &&
                               columnMap.containsKey("z") && columnMap.containsKey("world");
            boolean hasLocation = columnMap.containsKey("location");

            if (!hasCoords && !hasLocation) {
                Logger.warn("Table %s missing coordinate columns", tableName);
                return new HashMap<>();
            }

            if (!columnMap.containsKey("uuid")) {
                Logger.warn("Table %s missing UUID column", tableName);
                return new HashMap<>();
            }

            return columnMap;
        }
    }

    private boolean matches(String value, String... options) {
        for (String option : options) {
            if (value.equals(option)) {
                return true;
            }
        }
        return false;
    }

    private void readHomesFromTable(Connection conn, String tableName,
                                   Map<String, String> columnMap,
                                   Map<UUID, List<Home>> result) throws SQLException {
        String query = "SELECT * FROM " + tableName;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                try {
                    // Get player UUID
                    String uuidStr = rs.getString(columnMap.get("uuid"));
                    UUID playerUuid = parseUuid(uuidStr);
                    if (playerUuid == null) continue;

                    // Get home name (default to "home" if not present)
                    String homeName = columnMap.containsKey("name") ?
                        rs.getString(columnMap.get("name")) : "home";
                    if (homeName == null || homeName.isEmpty()) {
                        homeName = "home";
                    }

                    // Get coordinates
                    String world;
                    double x, y, z;
                    float yaw = 0, pitch = 0;

                    if (columnMap.containsKey("location")) {
                        // Parse location string (format: "world;x;y;z;yaw;pitch" or similar)
                        String loc = rs.getString(columnMap.get("location"));
                        String[] parts = loc.split("[;,:]");
                        if (parts.length < 4) continue;

                        world = parts[0];
                        x = Double.parseDouble(parts[1]);
                        y = Double.parseDouble(parts[2]);
                        z = Double.parseDouble(parts[3]);
                        if (parts.length > 4) yaw = Float.parseFloat(parts[4]);
                        if (parts.length > 5) pitch = Float.parseFloat(parts[5]);
                    } else {
                        // Get individual columns
                        world = rs.getString(columnMap.get("world"));
                        x = rs.getDouble(columnMap.get("x"));
                        y = rs.getDouble(columnMap.get("y"));
                        z = rs.getDouble(columnMap.get("z"));

                        if (columnMap.containsKey("yaw")) {
                            yaw = rs.getFloat(columnMap.get("yaw"));
                        }
                        if (columnMap.containsKey("pitch")) {
                            pitch = rs.getFloat(columnMap.get("pitch"));
                        }
                    }

                    if (world == null || world.isEmpty()) continue;

                    Home home = Home.create(homeName, world, x, y, z, yaw, pitch);
                    result.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(home);

                } catch (Exception e) {
                    Logger.debug("Failed to parse row: %s", e.getMessage());
                }
            }
        }

        Logger.info("Read %d players from table %s", result.size(), tableName);
    }

    private UUID parseUuid(String str) {
        if (str == null) return null;
        try {
            // Handle UUIDs with or without dashes
            str = str.trim();
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
            SQLite Database Migration:

            Place .db, .sqlite, or .sqlite3 files in: config/hyperhomes/migration/

            The migration will automatically detect and parse tables with home data.

            Supported table structures:
            - Tables named: homes, player_homes, playerhomes, home, etc.
            - Columns: uuid/player_uuid, name/home_name, world, x, y, z, yaw, pitch
            - Or location column with format: "world;x;y;z;yaw;pitch"

            Note: Requires SQLite JDBC driver (usually included with server).

            Then run: /homes migrate sqlite""";
    }
}
