CREATE TABLE IF NOT EXISTS prestamos (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  miembro_id INTEGER NOT NULL,
  fecha_prestamo TEXT NOT NULL DEFAULT (date('now')),
  fecha_vencimiento TEXT,
  fecha_devolucion TEXT,
  status TEXT DEFAULT 'activo',
  notas TEXT,
  FOREIGN KEY (miembro_id) REFERENCES miembros(id) ON DELETE RESTRICT
);
