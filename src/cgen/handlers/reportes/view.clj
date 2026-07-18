(ns cgen.handlers.reportes.view
  (:require [cgen.models.grid :refer [build-report]]))

(defn- stat-card
  [label value color]
  [:div.col-6.col-md-3.mb-3
   [:div.card.border-0.shadow-sm
    [:div.card-body.text-center
     [:h3.text (str value)]
     [:p.text-muted.mb-0 label]]]])

(defn- categoria-bar
  [cat max-total]
  (let [pct (if (pos? max-total)
              (int (* (/ (:total cat) max-total) 100))
              0)]
    [:div.mb-2
     [:div.d-flex.justify-content-between.small
      [:span (:nombre cat)]
      [:span (:total cat)]]
     [:div.progress {:style "height: 8px;"}
      [:div.progress-bar {:role "progressbar"
                          :style (str "width: " pct "%")
                          :aria-valuenow (:total cat)
                          :aria-valuemin "0"
                          :aria-valuemax max-total}]]]))

(defn- report-content
  [request title rows table-id fields]
  (let [r (build-report request title rows table-id fields)]
    (if (and (map? r) (= :html (:type r)))
      (:content r)
      r)))

(defn libros
  [request title rows stats]
  [:div.container-fluid
   [:h2.mb-4 "Reporte de Libros"]
   [:div.row.mb-4
    (stat-card "Total" (:total stats) "primary")
    (stat-card "Disponibles" (:disponibles stats) "success")
    (stat-card "Prestados" (:prestados stats) "warning")]
   (when (seq (:categorias stats))
     [:div.card.shadow-sm.mb-4
      [:div.card-body
       [:h5.card-title "Libros por Categoría"]
       (let [max-total (apply max (map :total (:categorias stats)))]
         (map #(categoria-bar % max-total) (:categorias stats)))]])
   (report-content request title rows "libros-report"
                   (array-map
                    :titulo "Título"
                    :isbn "ISBN"
                    :categoria_nombre "Categoría"
                    :anio_publicacion "Año"
                    :paginas "Páginas"
                    :status "Estado"
                    :num_autores "Autores"
                    :num_imagenes "Imágenes"))])

(defn miembros
  [request title rows stats]
  [:div.container-fluid
   [:h2.mb-4 "Reporte de Miembros"]
   [:div.row.mb-4
    (stat-card "Total" (:total stats) "primary")
    (stat-card "Activos" (:activos stats) "success")
    (stat-card "Nuevos (30d)" (:nuevos stats) "info")]
   (report-content request title rows "miembros-report"
                   (array-map
                    :nombre "Nombre"
                    :email "Correo"
                    :telefono "Teléfono"
                    :fecha_registro "Registro"
                    :activo "Activo"
                    :num_prestamos "Préstamos"
                    :prestamos_activos "Activos"))])

(defn prestamos
  [request title rows stats]
  [:div.container-fluid
   [:h2.mb-4 "Reporte de Préstamos"]
   [:div.row.mb-4
    (stat-card "Total" (:total stats) "primary")
    (stat-card "Activos" (:activos stats) "success")
    (stat-card "Vencidos" (:vencidos stats) "danger")
    (stat-card "Devueltos" (:devueltos stats) "info")]
   (report-content request title rows "prestamos-report"
                   (array-map
                    :miembro_nombre "Miembro"
                    :fecha_prestamo "Inicio"
                    :fecha_vencimiento "Vence"
                    :fecha_devolucion "Devuelto"
                    :status "Estado"
                    :libros_prestados "Libros"))])
