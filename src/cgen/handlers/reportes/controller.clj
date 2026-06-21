(ns cgen.handlers.reportes.controller
  (:require
   [cgen.handlers.reportes.model :as model]
   [cgen.handlers.reportes.view :as view]
   [cgen.layout :refer [application]]
   [cgen.models.grid :refer [build-report]]
   [cgen.models.util :refer [get-session-id]]))

(defn- handle-export
  "If an export format is requested, return the direct response.
  Otherwise returns nil."
  [request title rows table-id fields]
  (let [export (get-in request [:query-params "export"])]
    (when export
      (let [result (build-report request title rows table-id fields)]
        (when (= :response (:type result))
          (:response result))))))

(defn libros
  "Reporte de libros con estadísticas."
  [request]
  (let [title "Reporte de Libros"
        ok (get-session-id request)
        js nil
        libros-rows (model/get-libros-reporte)
        stats (model/get-libros-stats)]
    (or (handle-export request title libros-rows "libros-report"
                       (array-map :titulo "Título" :isbn "ISBN"
                                  :categoria_nombre "Categoría"
                                  :anio_publicacion "Año"
                                  :paginas "Páginas" :status "Estado"
                                  :num_autores "Autores"
                                  :num_imagenes "Imágenes"))
        (application request title ok js
                     (view/libros request title libros-rows stats)))))

(defn miembros
  "Reporte de miembros registrados."
  [request]
  (let [title "Reporte de Miembros"
        ok (get-session-id request)
        js nil
        rows (model/get-miembros-reporte)
        stats (model/get-miembros-stats)]
    (or (handle-export request title rows "miembros-report"
                       (array-map :nombre "Nombre" :email "Correo"
                                  :telefono "Teléfono"
                                  :fecha_registro "Registro"
                                  :activo "Activo"
                                  :num_prestamos "Préstamos"
                                  :prestamos_activos "Activos"))
        (application request title ok js
                     (view/miembros request title rows stats)))))

(defn prestamos
  "Reporte de préstamos."
  [request]
  (let [title "Reporte de Préstamos"
        ok (get-session-id request)
        js nil
        rows (model/get-prestamos-reporte)
        stats (model/get-prestamos-stats)]
    (or (handle-export request title rows "prestamos-report"
                       (array-map :miembro_nombre "Miembro"
                                  :fecha_prestamo "Inicio"
                                  :fecha_vencimiento "Vence"
                                  :fecha_devolucion "Devuelto"
                                  :status "Estado"
                                  :libros_prestados "Libros"))
        (application request title ok js
                     (view/prestamos request title rows stats)))))
