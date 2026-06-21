CREATE TABLE IF NOT EXISTS miembros (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  nombre TEXT NOT NULL,
  email TEXT UNIQUE,
  telefono TEXT,
  foto TEXT,
  fecha_registro TEXT DEFAULT (date('now')),
  activo TEXT DEFAULT 'T'
);
