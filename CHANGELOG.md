# Changelog

All notable changes to HyperHomes will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

**Modular Configuration System**
- Replaced monolithic `HyperHomesConfig` with new `ConfigManager` architecture
- Split single config.json into 5 specialized modules:
  - `CoreConfig` — home limits, colors, prefixes, faction integration, update checks
  - `TeleportConfig` — warmup/cooldown, cancellation, safety, cross-world
  - `GuiConfig` — GUI enabled, homes per page, delete confirmation
  - `ShareConfig` — sharing enabled, max shares, acceptance requirement, expiration
  - `BedSyncConfig` — bed sync enabled, home name
- Automatic migration from old flat config.json to new split format with backup
- `ConfigFile` base class with JSON serialization and validation framework

**Unified Permission System**
- New `PermissionManager` with chain-of-responsibility pattern (same architecture as HyperFactions)
- `PermissionProvider` interface for pluggable permission sources
- `HyperPermsProviderAdapter` for HyperPerms integration (replaces old reflection-based `HyperPermsIntegration`)
- Centralized `Permissions.java` with all permission node constants organized by category:
  - Basic access (`use`, `gui`), home management (`set`, `delete`, `list`)
  - Sharing (`share`, `share.accept`), bypass (`warmup`, `cooldown`, `limit`)
  - Admin (`admin`, `settings`, `reload`, `update`, `migrate`, `teleport.others`)
  - Limits (`unlimited`, `limit.N`)
- Smart fallback behavior: admin permissions require OP, bypass defaults to deny, normal permissions configurable
- Numeric permission values for dynamic home limits (`hyperhomes.limit.5`)
- Category wildcard resolution (`hyperhomes.bypass.*`)

**HyperFactions Integration**
- Soft dependency on HyperFactions for territory restrictions
- New `HyperFactionsIntegration` class with reflection-based API access
- Prevents setting homes in enemy territory (configurable via `restrictHomesInEnemyTerritory`)
- Territory label display ("Wilderness" or faction name)
- Fail-open design when HyperFactions is not installed

**Command Utilities**
- New `CommandUtil` class with unified messaging utilities
- Centralized color constants and standard prefix formatting
- Helper methods: `prefix()`, `msg()`, `error()`, `success()`, `info()`, `hasPermission()`

**GUI Improvements**
- New `GuiType` enum for page type identification
- New `ActivePageTracker` for tracking which GUI pages players have open
- Enables targeted refresh and cleanup when data changes
- Player disconnect cleanup support in GuiManager

**Build System**
- Auto-generated `BuildInfo.java` with version, Java version, and build timestamp
- Version expansion in `manifest.json` (`${version}` replacement)
- Plain JAR classifier fix (`jar { archiveClassifier = 'plain' }`) prevents shadow JAR overwriting in multi-project builds

### Changed

**Package Reorganization**
- Data classes moved from `model/` to `data/` package (`Home`, `Location`, `PlayerHomes`)
- Storage interfaces renamed: `StorageProvider` → `HomeStorage`, `JsonStorageProvider` → `JsonHomeStorage`
- All commands migrated to use `PermissionManager.get().hasPermission()` instead of `HyperPermsIntegration`
- All commands and managers migrated to `ConfigManager.get()` for config access
- `HyperHomes.java` refactored initialization to use `ConfigManager`, `PermissionManager`, `HyperFactionsIntegration`

### Removed

- `HyperHomesConfig.java` — replaced by modular `ConfigManager` system
- `HyperPermsIntegration.java` — replaced by `PermissionManager` + `HyperPermsProviderAdapter`
- Old `model/` package — classes moved to `data/`
- Old `StorageProvider` / `JsonStorageProvider` — renamed to `HomeStorage` / `JsonHomeStorage`

## [0.1.0] - 2026-01-23

### Added

- **Multiple Named Homes** - Set and teleport to multiple homes per player
- **Home Sharing** - Share your homes with other players
- **Interactive GUI** - Easy-to-use interface for home management via `/homes`
- **Warmup/Cooldown System** - Configurable teleport delays
- **Cross-World Teleportation** - Travel between worlds seamlessly
- **Safe Teleport** - Automatically finds safe landing locations
- **HyperPerms Integration** - Optional permission-based home limits

### Commands

- `/home [name]` - Teleport to a home (default home if no name given)
- `/sethome [name]` - Set a home at current location
- `/delhome <name>` - Delete a home
- `/homes` - Open home management GUI
