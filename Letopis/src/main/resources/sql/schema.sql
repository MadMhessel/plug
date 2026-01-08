CREATE TABLE IF NOT EXISTS world_state (
  world TEXT PRIMARY KEY,
  noise REAL NOT NULL DEFAULT 0.0,
  ash REAL NOT NULL DEFAULT 0.0,
  grove REAL NOT NULL DEFAULT 0.0,
  rift REAL NOT NULL DEFAULT 0.0,
  last_decay_ts INTEGER NOT NULL DEFAULT 0,
  active_event TEXT NULL,
  event_end_ts INTEGER NULL,
  cooldown_until_ts INTEGER NULL,
  last_dangerous_event_ts INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS player_contrib (
  uuid TEXT NOT NULL,
  world TEXT NOT NULL,
  scale TEXT NOT NULL,
  points_24h REAL NOT NULL DEFAULT 0.0,
  points_1h REAL NOT NULL DEFAULT 0.0,
  last_update_ts INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (uuid, world, scale)
);

CREATE TABLE IF NOT EXISTS journal_entries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts INTEGER NOT NULL,
  world TEXT NOT NULL,
  type TEXT NOT NULL,
  scale TEXT NULL,
  details TEXT NULL
);

CREATE TABLE IF NOT EXISTS player_meta (
  uuid TEXT PRIMARY KEY,
  title TEXT NULL,
  cosmetics_enabled INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS player_titles (
  uuid TEXT NOT NULL,
  title TEXT NOT NULL,
  PRIMARY KEY (uuid, title)
);
