# HyperHomes

[![Discord](https://img.shields.io/badge/Discord-Join%20Us-7289DA?logo=discord&logoColor=white)](https://discord.gg/SNPjyfkYPc)
[![GitHub](https://img.shields.io/github/stars/HyperSystemsDev/HyperHomes?style=social)](https://github.com/HyperSystemsDev/HyperHomes)

Personal home teleportation plugin for Hytale servers. Part of the **HyperSystems** plugin suite.

**Version:** 0.1.0
**Game:** Hytale Early Access
**License:** GPLv3

---

## Overview

HyperHomes allows players to set, manage, and teleport to personal home locations. Features include multiple named homes, home sharing, an interactive GUI, warmup/cooldown mechanics, and optional HyperPerms integration for permission-based limits.

---

## Key Features

- **Multiple Named Homes** - Set and manage multiple homes per player (configurable limit)
- **Home Sharing** - Share your homes with other players
- **Interactive GUI** - Easy-to-use interface for home management
- **Warmup/Cooldown System** - Configurable teleport delays
- **Cross-World Teleportation** - Travel between worlds seamlessly
- **Safe Teleport** - Automatically finds safe landing locations
- **HyperPerms Integration** - Optional permission-based home limits
- **Bed Home** - Automatic bed location as a home option

---

## Installation

1. Download `HyperHomes-0.1.0.jar` from the [Releases](https://github.com/HyperSystemsDev/HyperHomes/releases) page
2. Place the JAR file in your server's `mods` folder
3. Restart your server
4. Configure settings in `mods/com.hyperhomes_HyperHomes/config.json`

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/home [name]` | Teleport to a home (default home if no name given) | `hyperhomes.home` |
| `/sethome [name]` | Set a home at your current location | `hyperhomes.sethome` |
| `/delhome <name>` | Delete a home | `hyperhomes.delhome` |
| `/homes` | Open the home management GUI | `hyperhomes.homes` |

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `hyperhomes.home` | Use /home command | true |
| `hyperhomes.sethome` | Use /sethome command | true |
| `hyperhomes.delhome` | Use /delhome command | true |
| `hyperhomes.homes` | Use /homes GUI command | true |
| `hyperhomes.share` | Share homes with others | true |
| `hyperhomes.limit.<number>` | Set custom home limit | false |
| `hyperhomes.unlimited` | Unlimited homes | op |
| `hyperhomes.bypass.cooldown` | Bypass teleport cooldown | op |
| `hyperhomes.bypass.warmup` | Bypass teleport warmup | op |

---

## Configuration

Configuration file: `mods/com.hyperhomes_HyperHomes/config.json`

```json
{
  "defaultHomeLimit": 3,
  "teleport": {
    "warmup": 3,
    "cooldown": 5,
    "cancelOnMove": true,
    "cancelOnDamage": true,
    "allowCrossWorld": true
  },
  "safety": {
    "safeTeleport": true,
    "safeRadius": 3
  },
  "gui": {
    "enabled": true,
    "homesPerPage": 6,
    "confirmDelete": true
  }
}
```

---

## HyperPerms Integration

HyperHomes optionally integrates with [HyperPerms](https://github.com/HyperSystemsDev/HyperPerms) for advanced permission management. When HyperPerms is installed, you can use permission nodes to control home limits per player or group.

---

## Building from Source

### Requirements

- Java 21+ (for building)
- Java 25 (for running on Hytale server)
- Gradle 8.12+
- Hytale Server (Early Access)

```bash
./gradlew build
```

The output JAR will be in `build/libs/`.

---

## Support

- **Discord:** https://discord.gg/SNPjyfkYPc
- **GitHub Issues:** https://github.com/HyperSystemsDev/HyperHomes/issues

---

## Credits

Developed by **HyperSystemsDev**

Part of the **HyperSystems** plugin suite:
- [HyperPerms](https://github.com/HyperSystemsDev/HyperPerms) - Advanced permissions
- [HyperHomes](https://github.com/HyperSystemsDev/HyperHomes) - Home teleportation
- [HyperFactions](https://github.com/HyperSystemsDev/HyperFactions) - Faction management
- [HyperWarp](https://github.com/HyperSystemsDev/HyperWarp) - Warps, spawns, TPA

---

*HyperHomes - Your Place, Anywhere*
