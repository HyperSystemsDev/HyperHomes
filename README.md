# HyperHomes

Personal home teleportation plugin for Hytale servers. Part of the HyperSystems suite.

## Features

- **Multiple Named Homes** - Set and manage multiple homes per player (configurable limit)
- **Home Sharing** - Share your homes with other players
- **Interactive GUI** - Easy-to-use interface for home management
- **Warmup/Cooldown System** - Configurable teleport delays
- **Cross-World Teleportation** - Travel between worlds seamlessly
- **Safe Teleport** - Automatically finds safe landing locations
- **HyperPerms Integration** - Optional permission-based home limits
- **Bed Home** - Automatic bed location as a home option

## Installation

1. Download `HyperHomes-0.1.0.jar` from the [Releases](https://github.com/ZenithDevHQ/HyperHomes/releases) page
2. Place the JAR file in your server's `mods/` folder
3. Restart your server
4. Configure settings in `config/hyperhomes/config.json`

## Commands

| Command | Description |
|---------|-------------|
| `/home [name]` | Teleport to a home (default home if no name given) |
| `/sethome [name]` | Set a home at your current location |
| `/delhome <name>` | Delete a home |
| `/homes` | Open the home management GUI |

## Permissions

| Permission | Description |
|------------|-------------|
| `hyperhomes.home` | Use /home command |
| `hyperhomes.sethome` | Use /sethome command |
| `hyperhomes.delhome` | Use /delhome command |
| `hyperhomes.homes` | Use /homes GUI command |
| `hyperhomes.share` | Share homes with others |
| `hyperhomes.limit.<number>` | Set custom home limit |
| `hyperhomes.unlimited` | Unlimited homes |
| `hyperhomes.bypass.cooldown` | Bypass teleport cooldown |
| `hyperhomes.bypass.warmup` | Bypass teleport warmup |

## Configuration

The configuration file is located at `config/hyperhomes/config.json`:

```json
{
  "defaultHomeLimit": 3,
  "teleportWarmup": 3,
  "teleportCooldown": 30,
  "crossWorldTeleport": true,
  "safeTeleport": true
}
```

## HyperPerms Integration

HyperHomes optionally integrates with [HyperPerms](https://github.com/ZenithDevHQ/HyperPerms) for advanced permission management. When HyperPerms is installed, you can use permission nodes to control home limits per player or group.

## Support

For bug reports and feature requests, please open an issue on [GitHub Issues](https://github.com/ZenithDevHQ/HyperHomes/issues).

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
