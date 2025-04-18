package com.jellypudding.simpleLifesteal.database;

import com.jellypudding.simpleLifesteal.SimpleLifesteal;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final SimpleLifesteal plugin;
    private Connection connection;
    private final File dbFile;

    public DatabaseManager(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "player_hearts.db");
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    private void connect() throws SQLException {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create database file!", e);
                throw new SQLException("Could not create database file", e);
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("Successfully connected to SQLite database.");
            initialiseDatabase();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
            throw new SQLException("SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database!", e);
            throw e;
        }
    }

    private void initialiseDatabase() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_hearts ("
                   + " uuid TEXT PRIMARY KEY NOT NULL,"
                   + " current_hearts INTEGER NOT NULL"
                   + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            plugin.getLogger().info("Player hearts table initialized successfully.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create player_hearts table!", e);
            throw e;
        }
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        }
    }

    public int getPlayerHearts(UUID uuid) {
        String sql = "SELECT current_hearts FROM player_hearts WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("current_hearts");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve hearts for UUID: " + uuid, e);
        }
         // Indicate player not found or error.
        return -1;
    }

    public void setPlayerHearts(UUID uuid, int hearts) {
        String sql = "REPLACE INTO player_hearts (uuid, current_hearts) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, uuid.toString());
            pstmt.setInt(2, hearts);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not set hearts for UUID: " + uuid, e);
        }
    }
}
