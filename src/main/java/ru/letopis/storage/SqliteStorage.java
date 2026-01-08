package ru.letopis.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteStorage {

    private final JavaPlugin plugin;
    private Connection connection;

    public SqliteStorage(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "letopis.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            this.connection = DriverManager.getConnection(url);

            runSchema();
        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось открыть SQLite: " + e.getMessage());
        }
    }

    private void runSchema() {
        String schema = readResource("sql/schema.sql");
        if (schema == null || schema.isBlank()) {
            plugin.getLogger().severe("schema.sql не найден или пуст.");
            return;
        }

        try (Statement st = connection.createStatement()) {
            st.executeUpdate("PRAGMA journal_mode=WAL;");
            st.executeUpdate("PRAGMA synchronous=NORMAL;");
            st.executeUpdate(schema);
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка инициализации схемы: " + e.getMessage());
        }
    }

    private String readResource(String path) {
        try (InputStream is = plugin.getResource(path)) {
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (IOException e) {
            return null;
        }
    }

    public Connection connection() {
        return connection;
    }

    public void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {}
    }
}
