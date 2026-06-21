(ns cgen.handlers.reportes.model
  (:require [cgen.models.crud :refer [Query]]))

(defn get-libros-reporte
  "Obtiene todos los libros con su categoría y conteo de autores."
  []
  (Query "SELECT lib.*, cat.nombre as categoria_nombre,
          (SELECT COUNT(*) FROM libros_autores WHERE libro_id = lib.id) as num_autores,
          (SELECT COUNT(*) FROM libros_imagenes WHERE libro_id = lib.id) as num_imagenes
          FROM libros lib
          LEFT JOIN categorias cat ON lib.categoria_id = cat.id
          ORDER BY lib.titulo"))

(defn get-libros-stats
  "Estadísticas de libros."
  []
  (let [total (-> (Query "SELECT COUNT(*) as c FROM libros") first :c)
        disponibles (-> (Query "SELECT COUNT(*) as c FROM libros WHERE status = 'disponible'") first :c)
        prestados (-> (Query "SELECT COUNT(*) as c FROM libros WHERE status = 'prestado'") first :c)
        categorias (Query "SELECT cat.nombre, COUNT(lib.id) as total FROM categorias cat LEFT JOIN libros lib ON lib.categoria_id = cat.id GROUP BY cat.id ORDER BY total DESC")]
    {:total total :disponibles disponibles :prestados prestados :categorias categorias}))

(defn get-miembros-reporte
  "Obtiene todos los miembros con conteo de préstamos."
  []
  (Query "SELECT m.*,
          (SELECT COUNT(*) FROM prestamos WHERE miembro_id = m.id) as num_prestamos,
          (SELECT COUNT(*) FROM prestamos WHERE miembro_id = m.id AND status = 'activo') as prestamos_activos
          FROM miembros m ORDER BY m.nombre"))

(defn get-miembros-stats
  "Estadísticas de miembros."
  []
  (let [total (-> (Query "SELECT COUNT(*) as c FROM miembros") first :c)
        activos (-> (Query "SELECT COUNT(*) as c FROM miembros WHERE activo = 'T'") first :c)
        nuevos (-> (Query "SELECT COUNT(*) as c FROM miembros WHERE fecha_registro >= date('now', '-30 days')") first :c)]
    {:total total :activos activos :nuevos nuevos}))

(defn get-prestamos-reporte
  "Obtiene todos los préstamos con detalle."
  []
  (Query "SELECT p.*, m.nombre as miembro_nombre,
          (SELECT GROUP_CONCAT(lib.titulo, ', ') FROM prestamos_detalle pd JOIN libros lib ON pd.libro_id = lib.id WHERE pd.prestamo_id = p.id) as libros_prestados
          FROM prestamos p
          JOIN miembros m ON p.miembro_id = m.id
          ORDER BY p.fecha_prestamo DESC"))

(defn get-prestamos-stats
  "Estadísticas de préstamos."
  []
  (let [total (-> (Query "SELECT COUNT(*) as c FROM prestamos") first :c)
        activos (-> (Query "SELECT COUNT(*) as c FROM prestamos WHERE status = 'activo'") first :c)
        vencidos (-> (Query "SELECT COUNT(*) as c FROM prestamos WHERE status = 'vencido'") first :c)
        devueltos (-> (Query "SELECT COUNT(*) as c FROM prestamos WHERE status = 'devuelto'") first :c)]
    {:total total :activos activos :vencidos vencidos :devueltos devueltos}))
