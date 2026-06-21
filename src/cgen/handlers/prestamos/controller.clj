(ns cgen.handlers.prestamos.controller
  (:require
   [cgen.handlers.prestamos.model :as model]
   [cgen.handlers.prestamos.view :as view]
   [cgen.layout :refer [application]]
   [cgen.models.util :refer [get-session-id]]
   [ring.util.response :refer [redirect]]))

(defn main
  "Muestra el panel de préstamos (escritorio de préstamos)."
  [request]
  (let [title "Escritorio de Préstamos"
        ok (get-session-id request)
        js nil
        libros-disponibles (model/get-libros-disponibles)
        prestamos-activos (model/get-prestamos-activos)
        miembros (model/get-miembros)
        content (view/main title libros-disponibles prestamos-activos miembros)]
    (application request title ok js content)))

(defn crear-prestamo
  "Procesa el formulario de creación de préstamo vía POST."
  [{:keys [params session] :as request}]
  (let [title "Escritorio de Préstamos"
        ok (get-session-id request)
        miembro-id (:miembro_id params)
        libro-ids (filter #(re-matches #"libro_\d+" (name %)) (keys params))
        fecha-prestamo (:fecha_prestamo params)]
    (if (and miembro-id (seq libro-ids))
      (let [result (model/crear-prestamo! miembro-id libro-ids fecha-prestamo)]
        (if (:success result)
          (redirect "/prestamos")
          (let [js nil
                content (view/main title
                                   (model/get-libros-disponibles)
                                   (model/get-prestamos-activos)
                                   (model/get-miembros)
                                   (:error result))]
            (application request title ok js content))))
      (let [js nil
            content (view/main title
                               (model/get-libros-disponibles)
                               (model/get-prestamos-activos)
                               (model/get-miembros)
                               "Seleccione un miembro y al menos un libro.")]
        (application request title ok js content)))))

(defn devolver-libro
  "Marca un préstamo como devuelto."
  [request]
  (let [prestamo-id (get-in request [:params :id])
        ok (get-session-id request)]
    (model/devolver-prestamo! prestamo-id)
    (redirect "/prestamos")))

(defn renovar-prestamo
  "Renueva un préstamo por 14 días más."
  [request]
  (let [prestamo-id (get-in request [:params :id])
        ok (get-session-id request)]
    (model/renovar-prestamo! prestamo-id)
    (redirect "/prestamos")))
