# SimpleLifesteal Plugin

**SimpleLifesteal** is a Minecraft 1.21.11 Paper plugin custom-designed for [minecraftoffline.net](https://www.minecraftoffline.net) but it can be used by other servers. It provides a lifesteal mechanic where players gain hearts by killing others and lose hearts upon death. When a player runs out of hearts they are banned, but through the **Blood Shrine** system other players can sacrifice hearts to free them.

## Features

- **Lifesteal**: Gain a heart for killing a player, lose a heart on death.
- **Heart Withdrawal**: Withdraw hearts as consumable apple items using `/withdrawheart`.
- **Heart Crafting**: Craft hearts using expensive materials (fully configurable recipe).
- **Configurable Limits**: Set starting and maximum heart counts per player.
- **Auto-ban**: Players who reach 0 hearts are automatically banned.
- **Unban Reset**: Players externally unbanned who rejoin with 0 hearts have their hearts reset to the starting amount.
- **Blood Shrine**: A structure that spawns randomly in the world at configurable intervals. Players standing nearby see a boss bar showing the shrine's heart cost, time remaining, and unbans left. To free a banned player, a nearby player runs `/shrine unban <name>` and sacrifices the required number of hearts from their inventory. The shrine explodes when all its unbans are used or its timer runs out or if players physically destroy all the shrine's blocks.
- **Grace Period**: New players can be protected for a configurable playtime window (requires OfflineStats).
- **SQLite Storage**: Persists player heart data in a local SQLite database.
- **Developer API**: Simple methods for other plugins to grant hearts to players.

## Installation

1. Download the latest release [here](https://github.com/Jelly-Pudding/simplelifesteal/releases/latest).
2. Place the `.jar` file in your server's `plugins` folder.
3. Restart your server.

## Configuration

`plugins/SimpleLifesteal/config.yml`:

```yaml
# Default number of hearts players start with
starting-hearts: 10

# Maximum hearts a player can have
maximum-hearts: 20

# Ban message shown to a player who runs out of hearts
ban-message: '&cYou ran out of hearts! Visit &bwww.minecraftoffline.net &cand join our Discord. Maybe a friend will unban you through in-game chat...'

# Grace Period Settings (requires OfflineStats plugin)
grace-period:
  enabled: false
  duration-hours: 1

# Blood Shrine Settings
shrine:
  # Enable or disable the shrine system entirely
  enabled: true

  # World in which the shrine spawns
  world: world

  # Time range (in minutes) between each shrine appearance
  spawn-interval-min: 420
  spawn-interval-max: 720

  # Maximum distance from 0, 0 (in blocks) where the shrine can appear
  spawn-radius: 4500

  # Heart items the activating player must sacrifice per unban (random in this range each spawn)
  hearts-cost-min: 5
  hearts-cost-max: 100

  # How many unbans a single shrine can process (random in this range each spawn)
  unbans-min: 1
  unbans-max: 5

  # How long (in minutes) the shrine lasts before it explodes (random in this range each spawn)
  duration-min: 20
  duration-max: 50

  # Radius (in blocks) within which players see the boss bar and hear ambient sounds
  proximity-radius: 30

  # Radius (in blocks) within which players can actually perform a shrine unban
  use-radius: 10

# Heart Crafting Settings
heart-crafting:
  enabled: true
  recipe:
    - ['NETHERITE_INGOT', 'NETHER_STAR', 'NETHERITE_INGOT']
    - ['DIAMOND_BLOCK', 'DIAMOND_BLOCK', 'DIAMOND_BLOCK']
    - ['NETHERITE_INGOT', 'NETHER_STAR', 'NETHERITE_INGOT']
```

> Recipe slot names come from [Paper's Material enum](https://jd.papermc.io/paper/1.21.10/org/bukkit/Material.html).

## Commands

| Command | Description |
|---|---|
| `/hearts` | Shows your current heart count. |
| `/withdrawheart [amount]` | Withdraws hearts as consumable items. |
| `/heartrecipe` | Displays the heart crafting recipe in chat. |
| `/shrine unban <player>` | Sacrifice heart items at an active Blood Shrine to unban a player. Must be within `use-radius` of the shrine. |
| `/shrine info` | (Admin) Shows the active shrine's location, cost, and time remaining. |
| `/shrine spawn` | (Admin) Forces a shrine to spawn immediately. |
| `/shrine cancel` | (Admin) Detonates the active shrine. |
| `/isbanned <player>` | Checks if a player is banned by SimpleLifesteal. |
| `/slunban <player>` | Removes a player's ban from the SimpleLifesteal database. |
| `/checkbanresult <player>` | (Admin/RCON) Checks a pending Bedrock ban-check result. |

> Bedrock player names must be prefixed with `.` (e.g. `/shrine unban .BedrockPlayer`).

## Permissions

| Permission | Default | Description |
|---|---|---|
| `simplelifesteal.command.hearts` | everyone | Use `/hearts`. |
| `simplelifesteal.command.withdrawheart` | everyone | Use `/withdrawheart`. |
| `simplelifesteal.command.heartrecipe` | everyone | Use `/heartrecipe`. |
| `simplelifesteal.shrine.unban` | everyone | Use `/shrine unban`. |
| `simplelifesteal.command.isbanned` | op | Use `/isbanned`. |
| `simplelifesteal.command.slunban` | op | Use `/slunban`. |
| `simplelifesteal.command.checkbanresult` | op | Use `/checkbanresult`. |
| `simplelifesteal.shrine.admin` | op | Use `/shrine spawn`, `/shrine cancel`, `/shrine info`. |

## API for Developers

### Setup

Place `SimpleLifesteal.jar` in a `libs` directory and add to `build.gradle`:

```gradle
dependencies {
    compileOnly files('libs/SimpleLifesteal-2.0.jar')
}
```

If SimpleLifesteal is required by your plugin, add to `plugin.yml`:

```yaml
depend: [SimpleLifesteal]
```

### Getting the Instance

```java
import org.bukkit.Bukkit;
import com.jellypudding.simpleLifesteal.SimpleLifesteal;

Plugin pl = Bukkit.getPluginManager().getPlugin("SimpleLifesteal");
if (pl instanceof SimpleLifesteal simpleLifesteal && pl.isEnabled()) {
    // use simpleLifesteal
}
```

### Available Methods

```java
// Get a player's current heart count
int hearts = simpleLifesteal.getPlayerHearts(playerUUID);

// Add hearts to a player (returns true on success)
boolean success = simpleLifesteal.addHearts(playerUUID, 5);

// Get a player's current maximum heart limit
int max = simpleLifesteal.getPlayerMaxHearts(playerUUID);

// Set a player's maximum heart limit (can exceed the global maximum)
boolean success = simpleLifesteal.setPlayerMaxHearts(playerUUID, 25);

// Increase a player's maximum heart limit
boolean success = simpleLifesteal.increasePlayerMaxHearts(playerUUID, 3);

// Check if a player is currently in their grace period
boolean inGrace = simpleLifesteal.isPlayerInGracePeriod(playerUUID);
```

## Support Me
Donations will help me with the development of this project.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
