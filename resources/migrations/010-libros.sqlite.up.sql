CREATE TABLE IF NOT EXISTS libros (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  titulo TEXT NOT NULL,
  isbn TEXT UNIQUE,
  categoria_id INTEGER,
  portada TEXT,
  pdf TEXT,
  documento TEXT,
  anio_publicacion INTEGER,
  paginas INTEGER,
  sinopsis TEXT,
  status TEXT DEFAULT 'disponible',
  created_at TEXT DEFAULT (datetime('now')),
  FOREIGN KEY (categoria_id) REFERENCES categorias(id) ON DELETE SET NULL
);
