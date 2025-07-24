# SimpleLifesteal Plugin

**SimpleLifesteal** is a Minecraft 1.21.8 Paper plugin that provides a simple lifesteal mechanic where players gain hearts for killing others and lose hearts upon death. When they run out of hearts, they get banned.

## Features

- **Simple Lifesteal**: Gain a heart for killing a player, lose a heart on death.
- **Heart Withdrawal**: Players can withdraw their hearts as consumable apple items using `/withdrawheart`.
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
ban-message: "You ran out of hearts!"
```

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
   You should see: `max_hearts INTEGER` in the output.

6. **Exit SQLite:**
   ```sql
   .quit
   ```

## Commands

- `/hearts`: Shows the player their current heart count.
- `/withdrawheart [amount]`: Withdraw hearts as consumable apple items. Defaults to 1 heart if no amount specified.
- `/isbanned <player>`: Checks if a player is banned by SimpleLifesteal.
- `/slunban <player>`: Removes a player ban from the SimpleLifesteal database.
- `/checkbanresult <player>`: (Admin/RCON) Checks the result of a pending Bedrock player ban check initiated by /isbanned.

## Permissions

- `simplelifesteal.command.hearts`: Allows using the `/hearts` command (Default: `true` - everyone has access).
- `simplelifesteal.command.withdrawheart`: Allows using the `/withdrawheart` command (Default: `true` - everyone has access).
- `simplelifesteal.command.isbanned`: Allows using the /isbanned command (Default: `op` - only OPs have access).
- `simplelifesteal.command.slunban`: Allows using the /slunban command (Default: `op` - only OPs have access).
- `simplelifesteal.command.checkbanresult`: Allows using the /checkbanresult command (Default: `op` - only OPs have access).

## API for Developers

### Setup Dependencies
1. Download the latest `SimpleLifesteal.jar` and place it in a `libs` directory - and then add this to your `build.gradle` file:
    ```gradle
    dependencies {
        compileOnly files('libs/SimpleLifesteal-1.6.0.jar')
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
