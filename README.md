# SimpleLifesteal Plugin

**SimpleLifesteal** is a Minecraft 1.21.11 Paper plugin that provides a simple lifesteal mechanic where players gain hearts for killing others and lose hearts upon death. When they run out of hearts, they get banned.

## Features

- **Simple Lifesteal**: Gain a heart for killing a player, lose a heart on death.
- **Heart Withdrawal**: Players can withdraw their hearts as consumable apple items using `/withdrawheart`.
- **Heart Crafting**: Players can craft hearts using expensive materials (fully configurable recipe).
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
starting-hearts: 10

# Maximum hearts a player can have
maximum-hearts: 20

# Message used when banning a player for running out of hearts.
# Supports standard Minecraft color codes using '&' (e.g. "&c&lOut of hearts!").
ban-message: '&cYou ran out of hearts! Visit &bwww.minecraftoffline.net/unban/store&c to get unbanned.'

# Grace Period Settings (requires OfflineStats plugin)
grace-period:
  # Enable grace period for new players
  enabled: false
  # Duration in hours (players with less playtime are protected)
  duration-hours: 1

# Heart Crafting Settings
heart-crafting:
  # Enable or disable heart crafting
  enabled: true

  # Crafting recipe layout (3x3 grid)
  # Use material names from https://jd.papermc.io/paper/1.21.10/org/bukkit/Material.html
  # Use 'AIR' or leave empty for empty slots
  # Format: [Row1, Row2, Row3] where each row has 3 items [Left, Middle, Right]
  recipe:
    # Top row
    - ['NETHERITE_INGOT', 'NETHER_STAR', 'NETHERITE_INGOT']
    # Middle row
    - ['DIAMOND_BLOCK', 'DIAMOND_BLOCK', 'DIAMOND_BLOCK']
    # Bottom row
    - ['NETHERITE_INGOT', 'NETHER_STAR', 'NETHERITE_INGOT']
```

> **Note:** Recipe uses Material names from [Paper's Material enum](https://jd.papermc.io/paper/1.21.10/org/bukkit/Material.html).

## Updating to v1.6

**If you're upgrading to v1.6 from a previous version**, you need to manually add a new column to your database to support individual player heart limits.

### Manual Database Update Steps:

1. **Stop your server** to ensure the database isn't being used.

2. **Navigate to your plugin directory:**
   ```bash
   cd plugins/SimpleLifesteal/
   ```

3. **Open the database with SQLite:**
   ```bash
   sqlite3 player_hearts.db
   ```

4. **Add the new column:**
   ```sql
   ALTER TABLE player_hearts ADD COLUMN max_hearts INTEGER;
   ```

5. **Verify the column was added:**
   ```sql
   .schema player_hearts
   ```
   You should see: `CREATE TABLE player_hearts (uuid TEXT PRIMARY KEY NOT NULL, current_hearts INTEGER NOT NULL, max_hearts INTEGER)`

6. **Exit SQLite:**
   ```sql
   .quit
   ```

## Commands

- `/hearts`: Shows the player their current heart count.
- `/withdrawheart [amount]`: Withdraw hearts as consumable apple items. Defaults to 1 heart if no amount specified.
- `/heartrecipe`: Displays the heart crafting recipe in chat.
- `/isbanned <player>`: Checks if a player is banned by SimpleLifesteal.
- `/slunban <player>`: Removes a player ban from the SimpleLifesteal database.
- `/checkbanresult <player>`: (Admin/RCON) Checks the result of a pending Bedrock player ban check initiated by /isbanned.

## Permissions

- `simplelifesteal.command.hearts`: Allows using the `/hearts` command (Default: `true` - everyone has access).
- `simplelifesteal.command.withdrawheart`: Allows using the `/withdrawheart` command (Default: `true` - everyone has access).
- `simplelifesteal.command.heartrecipe`: Allows using the `/heartrecipe` command (Default: `true` - everyone has access).
- `simplelifesteal.command.isbanned`: Allows using the /isbanned command (Default: `op` - only OPs have access).
- `simplelifesteal.command.slunban`: Allows using the /slunban command (Default: `op` - only OPs have access).
- `simplelifesteal.command.checkbanresult`: Allows using the /checkbanresult command (Default: `op` - only OPs have access).

## API for Developers

### Setup Dependencies
1. Download the latest `SimpleLifesteal.jar` and place it in a `libs` directory - and then add this to your `build.gradle` file:
    ```gradle
    dependencies {
        compileOnly files('libs/SimpleLifesteal-1.7.0.jar')
    }
    ```

2. If SimpleLifesteal is absolutely required by your plugin, then add this to your `plugin.yml` file - and this means if SimpleLifesteal is not found then your plugin will not load:
    ```yaml
    depend: [SimpleLifesteal]
    ```

### Getting SimpleLifesteal Instance
You can import SimpleLifesteal into your project through using the below code:
```java
import org.bukkit.Bukkit;
import com.jellypudding.simpleLifesteal.SimpleLifesteal;

Plugin simpleLifestealPlugin = Bukkit.getPluginManager().getPlugin("SimpleLifesteal");
if (simpleLifestealPlugin instanceof SimpleLifesteal && simpleLifestealPlugin.isEnabled()) {
    SimpleLifesteal simpleLifesteal = (SimpleLifesteal) simpleLifestealPlugin;
}
```

### Available API Methods
```java
// Get player's current heart count
int currentHearts = simpleLifesteal.getPlayerHearts(playerUUID);

// Add hearts to a player (returns success boolean)
boolean success = simpleLifesteal.addHearts(playerUUID, 5);

// Get player's current maximum heart limit
int maxHearts = simpleLifesteal.getPlayerMaxHearts(playerUUID);

// Set player's maximum heart limit (allows exceeding global maximum)
boolean success = simpleLifesteal.setPlayerMaxHearts(playerUUID, 25);

// Increase player's maximum heart limit by specified amount
boolean success = simpleLifesteal.increasePlayerMaxHearts(playerUUID, 3);
```

## Support Me
Donations will help me with the development of this project.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/K3K715TC1R)
