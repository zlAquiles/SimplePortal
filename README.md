# SimplePortals

SimplePortals is a modern portal plugin for Paper-compatible Minecraft servers focused on fast cuboid portal creation, local and proxy destinations, configurable portal actions, and Folia-aware execution.

## Features

- Cuboid portal creation with a selector wand
- Visual selection preview with particles and hologram-style markers
- Local destinations and proxy destinations
- Portal actions such as messages, titles, sounds, bossbars, commands, and potion effects
- MiniMessage and legacy color support in `messages.yml` and `portals.yml`
- Trigger block support for `WATER`, `LAVA`, `AIR`, `NETHER_PORTAL`, `END_PORTAL`, and `END_GATEWAY`
- Chunk-based portal lookup for efficient movement checks
- Folia-aware scheduling and teleport handling
- Developer API included

## Requirements

- Java 17+
- A Paper-compatible server
- Built against `paper-api 1.19.4-R0.1-SNAPSHOT`

Proxy note:

- Proxy destinations use Bungee-compatible plugin messaging
- On Velocity, enable `bungee-plugin-message-channel = true` in `velocity.toml`

## Download

- [Modrinth](https://modrinth.com/plugin/simpleportals-)

## Installation

1. Download the latest SimplePortals jar from Modrinth.
2. Place it in your server's `plugins/` folder.
3. Start the server once to generate the configuration files.
4. Edit `config.yml`, `messages.yml`, and `portals.yml` as needed.
5. Restart the server.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/portal help` | Shows the help menu | `simpleportals.command.help` |
| `/portal wand` | Gives the selector wand | `simpleportals.command.wand` |
| `/portal create desti:<destination> [block:<type>]` | Creates a portal from the current selection | `simpleportals.command.create` |
| `/portal reload` | Reloads config, messages, and portals | `simpleportals.command.reload` |
| `/portal show <radius>` | Lists nearby portals | `simpleportals.command.show` |
| `/portal remove <name>` | Removes a portal | `simpleportals.command.remove` |
| `/portal delete <name>` | Alias of remove | `simpleportals.command.remove` |
| `/portal setblock <portal> <block>` | Changes a portal trigger block | `simpleportals.command.setblock` |
| `/portal tp <destination>` | Teleports directly to a destination | `simpleportals.command.tp` |
| `/portal destination create <name>` | Creates a local destination at your position | `simpleportals.command.destination.create` |
| `/portal destination remove <name>` | Removes a destination | `simpleportals.command.destination.remove` |

## Main Permissions

| Permission | Description |
| --- | --- |
| `simpleportals.command.help` | Allows viewing the help menu |
| `simpleportals.command.create` | Allows creating portals |
| `simpleportals.command.wand` | Allows receiving the selector wand |
| `simpleportals.command.reload` | Allows reloading plugin data |
| `simpleportals.command.show` | Allows listing nearby portals |
| `simpleportals.command.remove` | Allows deleting portals |
| `simpleportals.command.setblock` | Allows changing portal trigger blocks |
| `simpleportals.command.tp` | Allows teleporting to saved destinations |
| `simpleportals.command.destination.create` | Allows creating destinations |
| `simpleportals.command.destination.remove` | Allows removing destinations |
| `simpleportals.use` | Allows using portals |

## Configuration

SimplePortals generates these main files on first startup:

- `plugins/SimplePortals/config.yml`
- `plugins/SimplePortals/messages.yml`
- `plugins/SimplePortals/portals.yml`

Notable configurable areas include:

- selector item and preview behavior
- max radius for `/portal show`
- default portal trigger and cooldown
- automatic air replacement inside selected regions
- local and proxy destinations
- portal conditions, permissions, cooldowns, and actions
- update checker behavior

## Building

This project uses Gradle.

```bash
./gradlew shadowJar
```

On Windows:

```powershell
.\gradlew.bat shadowJar
```

The built jar will be generated in:

```text
build/libs/
```

## Notes

- SimplePortals currently loads as a classic plugin through `plugin.yml`.
- The project avoids NMS, which helps compatibility across newer Paper-compatible server versions.
- If you use proxy destinations on Velocity, make sure Bungee-compatible messaging is enabled.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).