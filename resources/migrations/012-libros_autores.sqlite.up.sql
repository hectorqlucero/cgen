CREATE TABLE IF NOT EXISTS libros_autores (
  libro_id INTEGER NOT NULL,
  autor_id INTEGER NOT NULL,
  rol TEXT DEFAULT 'Autor',
  PRIMARY KEY (libro_id, autor_id),
  FOREIGN KEY (libro_id) REFERENCES libros(id) ON DELETE CASCADE,
  FOREIGN KEY (autor_id) REFERENCES autores(id) ON DELETE CASCADE
);
