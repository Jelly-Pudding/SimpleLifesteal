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
    private volatile Connection connection;
    private final File dbFile;
    private final Object connectionLock = new Object();

    public DatabaseManager(SimpleLifesteal plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "player_hearts.db");
    }

    public Connection getConnection() throws SQLException {
        synchronized (connectionLock) {
            if (connection == null || connection.isClosed()) {
                connect();
            }
            return connection;
        }
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
            closeConnectionInternal();
            throw new SQLException("SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not connect to SQLite database!", e);
            closeConnectionInternal();
            throw e;
        }
    }

    private void initialiseDatabase() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_hearts (" +
                   " uuid TEXT PRIMARY KEY NOT NULL," +
                   " current_hearts INTEGER NOT NULL," +
                   " max_hearts INTEGER" +
                   ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialise player_hearts table!", e);
            throw e;
        }

        String banSql = "CREATE TABLE IF NOT EXISTS plugin_bans (" +
                        " uuid TEXT PRIMARY KEY NOT NULL," +
                        " reason TEXT," +
                        " ban_timestamp INTEGER NOT NULL" +
                        ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(banSql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialise plugin_bans table!", e);
            throw e;
        }
    }

    public void closeConnection() {
        synchronized (connectionLock) {
            closeConnectionInternal();
        }
    }

    private void closeConnectionInternal() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        } finally {
            connection = null;
        }
    }

    public int getPlayerHearts(UUID uuid) {
        String sql = "SELECT current_hearts FROM player_hearts WHERE uuid = ?";
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return rs.getInt("current_hearts");
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve hearts for UUID: " + uuid, e);
            }
        }
        return -1;
    }

    public void setPlayerHearts(UUID uuid, int hearts) {
        String sql = "INSERT OR REPLACE INTO player_hearts (uuid, current_hearts, max_hearts) VALUES (?, ?, COALESCE((SELECT max_hearts FROM player_hearts WHERE uuid = ?), NULL))";
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.setInt(2, hearts);
                    pstmt.setString(3, uuid.toString());
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set hearts for UUID: " + uuid, e);
            }
        }
    }

    public Integer getPlayerMaxHearts(UUID uuid) {
        String sql = "SELECT max_hearts FROM player_hearts WHERE uuid = ?";
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            int maxHearts = rs.getInt("max_hearts");
                            return rs.wasNull() ? null : maxHearts;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve max hearts for UUID: " + uuid, e);
            }
        }
        return null;
    }

    public void setPlayerMaxHearts(UUID uuid, int maxHearts) {
        String sql = "REPLACE INTO player_hearts (uuid, current_hearts, max_hearts) VALUES (?, ?, ?)";
        int currentHearts = getPlayerHearts(uuid);
        if (currentHearts == -1) {
            currentHearts = plugin.getStartingHearts();
        }
        
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.setInt(2, currentHearts);
                    pstmt.setInt(3, maxHearts);
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not set max hearts for UUID: " + uuid, e);
            }
        }
    }

    public void addPluginBan(UUID uuid, String reason) {
        String sql = "REPLACE INTO plugin_bans (uuid, reason, ban_timestamp) VALUES (?, ?, ?)";
        long timestamp = System.currentTimeMillis(); // Record current time

        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, reason);
                    pstmt.setLong(3, timestamp);
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not add plugin ban record for UUID: " + uuid, e);
            }
        }
    }

    public boolean isPlayerBannedByPlugin(UUID uuid) {
        String sql = "SELECT 1 FROM plugin_bans WHERE uuid = ? LIMIT 1";
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not check plugin ban status for UUID: " + uuid, e);
            }
        }
        return false;
    }

    public boolean removePluginBan(UUID uuid) {
        String sql = "DELETE FROM plugin_bans WHERE uuid = ?";
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    int affectedRows = pstmt.executeUpdate();
                    return affectedRows > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not remove plugin ban record for UUID: " + uuid, e);
            }
        }
        return false;
    }

    public int getTotalHeartBans() {
        String sql = "SELECT COUNT(*) AS ban_count FROM plugin_bans";
        int banCount = 0;
        synchronized (connectionLock) {
            Connection conn = null;
            try {
                conn = getConnection();
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    if (rs.next()) {
                        banCount = rs.getInt("ban_count");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not retrieve total heart bans count from database!", e);
            }
        }
        return banCount;
    }

}
