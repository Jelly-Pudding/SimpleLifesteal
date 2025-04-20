# SimpleLifesteal Plugin

**SimpleLifesteal** is a Minecraft 1.21.4 Paper plugin that provides a simple lifesteal mechanic where players gain hearts for killing others and lose hearts upon death. When they run out of hearts, they get banned.

## Features

- **Simple Lifesteal**: Gain a heart for killing a player, lose a heart on death.
- **Configurable Limits**: Set the starting and maximum number of hearts players can have.
- **Player Ban**: Players who lose their final heart (reach 0) are automatically banned.
- **Unban Reset**: Players who are externally unbanned and rejoin with 0 hearts automatically have their hearts reset to the starting amount.
- **`/hearts` Command**: Allows players to check their current heart count.
- **SQLite Storage**: Persists player heart data using a local SQLite database.
- **Developer API**: Provides a simple method for other plugins to grant hearts to players.

## Installation

1. Download the latest release [here](https://github.com/Jelly-Pudding/simplelifesteal/releases/latest).
2. Place the `.jar` file in your Minecraft server's `plugins` folder.
3. Restart your server.

## Configuration

The configuration file `plugins/SimpleLifesteal/config.yml` allows you to customise the plugin:

```yaml
# Default number of hearts players start with
starting-hearts: 15

# Maximum hearts a player can have
maximum-hearts: 20

# Message used when banning a player for running out of hearts.
# Supports standard Minecraft color codes using '&' (e.g. "&c&lOut of hearts!").
ban-message: "You ran out of hearts!"
```

## Commands

- `/hearts`: Shows the player their current heart count.
- `/isbanned <player>`: Checks if a player is banned by SimpleLifesteal.
- `/slunban <player>`: Removes a player ban from the SimpleLifesteal database.
- `/checkbanresult <player>`: (Admin/RCON) Checks the result of a pending Bedrock player ban check initiated by /isbanned.

## Permissions

- `simplelifesteal.command.hearts`: Allows using the `/hearts` command (Default: `true` - everyone has access).
- `simplelifesteal.command.isbanned`: Allows using the /isbanned command (Default: `op` - only OPs have access).
- `simplelifesteal.command.slunban`: Allows using the /slunban command (Default: `op` - only OPs have access).
- `simplelifesteal.command.checkbanresult`: Allows using the /checkbanresult command (Default: `op` - only OPs have access).

## Support Me
Donations will help me with the development of this project.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
