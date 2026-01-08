package ru.letopis.storage;

import ru.letopis.model.JournalEntry;
import ru.letopis.model.PlayerMeta;
import ru.letopis.model.Scale;
import ru.letopis.model.WorldState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StorageService {
    private final SqliteStorage storage;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Letopis-DB"));

    public StorageService(SqliteStorage storage) {
        this.storage = storage;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public List<WorldState> loadWorldStates() {
        List<WorldState> states = new ArrayList<>();
        String sql = "SELECT world, noise, ash, grove, rift, last_decay_ts, active_event, event_end_ts, cooldown_until_ts, last_dangerous_event_ts FROM world_state";
        try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    WorldState state = new WorldState(rs.getString("world"));
                    state.set(Scale.NOISE, rs.getDouble("noise"));
                    state.set(Scale.ASH, rs.getDouble("ash"));
                    state.set(Scale.GROVE, rs.getDouble("grove"));
                    state.set(Scale.RIFT, rs.getDouble("rift"));
                    state.setLastDecayTs(rs.getLong("last_decay_ts"));
                    state.setActiveEventId(rs.getString("active_event"));
                    long eventEnd = rs.getLong("event_end_ts");
                    if (!rs.wasNull()) {
                        state.setEventEndTs(eventEnd);
                    }
                    long cooldown = rs.getLong("cooldown_until_ts");
                    if (!rs.wasNull()) {
                        state.setCooldownUntilTs(cooldown);
                    }
                    state.setLastDangerousEventTs(rs.getLong("last_dangerous_event_ts"));
                    states.add(state);
                }
            }
        } catch (SQLException e) {
            return states;
        }
        return states;
    }

    public void saveWorldState(WorldState state) {
        String sql = "INSERT INTO world_state (world, noise, ash, grove, rift, last_decay_ts, active_event, event_end_ts, cooldown_until_ts, last_dangerous_event_ts) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(world) DO UPDATE SET noise=excluded.noise, ash=excluded.ash, grove=excluded.grove, rift=excluded.rift, " +
            "last_decay_ts=excluded.last_decay_ts, active_event=excluded.active_event, event_end_ts=excluded.event_end_ts, " +
            "cooldown_until_ts=excluded.cooldown_until_ts, last_dangerous_event_ts=excluded.last_dangerous_event_ts";
        executor.execute(() -> {
            try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
                ps.setString(1, state.world());
                ps.setDouble(2, state.get(Scale.NOISE));
                ps.setDouble(3, state.get(Scale.ASH));
                ps.setDouble(4, state.get(Scale.GROVE));
                ps.setDouble(5, state.get(Scale.RIFT));
                ps.setLong(6, state.lastDecayTs());
                ps.setString(7, state.activeEventId());
                if (state.eventEndTs() == null) {
                    ps.setNull(8, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(8, state.eventEndTs());
                }
                if (state.cooldownUntilTs() == null) {
                    ps.setNull(9, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(9, state.cooldownUntilTs());
                }
                ps.setLong(10, state.lastDangerousEventTs());
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public void savePlayerMeta(UUID uuid, PlayerMeta meta) {
        String sql = "INSERT INTO player_meta (uuid, title, cosmetics_enabled) VALUES (?, ?, ?) " +
            "ON CONFLICT(uuid) DO UPDATE SET title=excluded.title, cosmetics_enabled=excluded.cosmetics_enabled";
        executor.execute(() -> {
            try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, meta.title());
                ps.setInt(3, meta.cosmeticsEnabled() ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public PlayerMeta loadPlayerMeta(UUID uuid) {
        String sql = "SELECT title, cosmetics_enabled FROM player_meta WHERE uuid=?";
        try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = rs.getString("title");
                    boolean enabled = rs.getInt("cosmetics_enabled") == 1;
                    return new PlayerMeta(title, enabled);
                }
            }
        } catch (SQLException ignored) {
        }
        return new PlayerMeta(null, true);
    }

    public void addPlayerTitle(UUID uuid, String title) {
        String sql = "INSERT OR IGNORE INTO player_titles (uuid, title) VALUES (?, ?)";
        executor.execute(() -> {
            try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, title);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public List<String> loadPlayerTitles(UUID uuid) {
        List<String> titles = new ArrayList<>();
        String sql = "SELECT title FROM player_titles WHERE uuid=?";
        try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    titles.add(rs.getString("title"));
                }
            }
        } catch (SQLException ignored) {
        }
        return titles;
    }

    public Map<Scale, Double> loadContribution(UUID uuid, String world) {
        Map<Scale, Double> map = new EnumMap<>(Scale.class);
        String sql = "SELECT scale, points_24h FROM player_contrib WHERE uuid=? AND world=?";
        try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, world);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Scale scale = Scale.fromKey(rs.getString("scale"));
                    if (scale != null) {
                        map.put(scale, rs.getDouble("points_24h"));
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        return map;
    }

    public void addContribution(UUID uuid, String world, Scale scale, double delta) {
        String sql = "INSERT INTO player_contrib (uuid, world, scale, points_24h, points_1h, last_update_ts) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(uuid, world, scale) DO UPDATE SET points_24h=points_24h+excluded.points_24h, " +
            "points_1h=points_1h+excluded.points_1h, last_update_ts=excluded.last_update_ts";
        long now = Instant.now().getEpochSecond();
        executor.execute(() -> {
            try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, world);
                ps.setString(3, scale.key());
                ps.setDouble(4, delta);
                ps.setDouble(5, delta);
                ps.setLong(6, now);
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public void resetDailyContrib() {
        String sql = "UPDATE player_contrib SET points_24h=0";
        executor.execute(() -> {
            try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public void insertJournal(JournalEntry entry) {
        String sql = "INSERT INTO journal_entries (ts, world, type, scale, details) VALUES (?, ?, ?, ?, ?)";
        executor.execute(() -> {
            try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
                ps.setLong(1, entry.ts());
                ps.setString(2, entry.world());
                ps.setString(3, entry.type());
                if (entry.scale() == null) {
                    ps.setNull(4, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(4, entry.scale().key());
                }
                ps.setString(5, entry.detailsJson());
                ps.executeUpdate();
            } catch (SQLException ignored) {
            }
        });
    }

    public List<JournalEntry> loadJournal(int limit, int offset) {
        List<JournalEntry> entries = new ArrayList<>();
        String sql = "SELECT ts, world, type, scale, details FROM journal_entries ORDER BY ts DESC LIMIT ? OFFSET ?";
        try (PreparedStatement ps = storage.connection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Scale scale = Scale.fromKey(rs.getString("scale"));
                    entries.add(new JournalEntry(rs.getLong("ts"), rs.getString("world"), rs.getString("type"), scale, rs.getString("details")));
                }
            }
        } catch (SQLException ignored) {
        }
        return entries;
    }

    public String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"world_state\": [\n");
        try (PreparedStatement ps = storage.connection().prepareStatement("SELECT * FROM world_state")) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append("    {\"world\":\"").append(rs.getString("world")).append("\",")
                        .append("\"noise\":").append(rs.getDouble("noise")).append(',')
                        .append("\"ash\":").append(rs.getDouble("ash")).append(',')
                        .append("\"grove\":").append(rs.getDouble("grove")).append(',')
                        .append("\"rift\":").append(rs.getDouble("rift")).append(',')
                        .append("\"last_decay_ts\":").append(rs.getLong("last_decay_ts")).append(',')
                        .append("\"active_event\":")
                        .append(rs.getString("active_event") == null ? "null" : "\"" + rs.getString("active_event") + "\"")
                        .append(',')
                        .append("\"event_end_ts\":")
                        .append(rs.getObject("event_end_ts") == null ? "null" : rs.getLong("event_end_ts"))
                        .append(',')
                        .append("\"cooldown_until_ts\":")
                        .append(rs.getObject("cooldown_until_ts") == null ? "null" : rs.getLong("cooldown_until_ts"))
                        .append(',')
                        .append("\"last_dangerous_event_ts\":").append(rs.getLong("last_dangerous_event_ts"))
                        .append('}');
                }
            }
        } catch (SQLException ignored) {
        }
        sb.append("\n  ],\n  \"journal_entries\": [\n");
        try (PreparedStatement ps = storage.connection().prepareStatement("SELECT * FROM journal_entries ORDER BY ts DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append("    {\"ts\":").append(rs.getLong("ts")).append(',')
                        .append("\"world\":\"").append(rs.getString("world")).append("\",")
                        .append("\"type\":\"").append(rs.getString("type")).append("\",")
                        .append("\"scale\":")
                        .append(rs.getString("scale") == null ? "null" : "\"" + rs.getString("scale") + "\"")
                        .append(',')
                        .append("\"details\":")
                        .append(rs.getString("details") == null ? "null" : "\"" + escapeJson(rs.getString("details")) + "\"")
                        .append('}');
                }
            }
        } catch (SQLException ignored) {
        }
        sb.append("\n  ],\n  \"player_contrib\": [\n");
        try (PreparedStatement ps = storage.connection().prepareStatement("SELECT uuid, world, scale, points_24h, points_1h, last_update_ts FROM player_contrib")) {
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append("    {\"uuid\":\"").append(rs.getString("uuid")).append("\",")
                        .append("\"world\":\"").append(rs.getString("world")).append("\",")
                        .append("\"scale\":\"").append(rs.getString("scale")).append("\",")
                        .append("\"points_24h\":").append(rs.getDouble("points_24h")).append(',')
                        .append("\"points_1h\":").append(rs.getDouble("points_1h")).append(',')
                        .append("\"last_update_ts\":").append(rs.getLong("last_update_ts"))
                        .append('}');
                }
            }
        } catch (SQLException ignored) {
        }
        sb.append("\n  ]\n}");
        return sb.toString();
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
