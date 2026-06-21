(ns cgen.handlers.prestamos.view
  (:require [cgen.web.csrf :refer [csrf-field]]))

(defn- libro-card
  [libro]
  [:div.col-md-4.col-lg-3.mb-3
   [:div.card.h-100
    [:div.card-body
     [:h6.card-title.text-primary (:titulo libro)]
     [:p.card-text.small.text-muted (str "ISBN: " (:isbn libro))]]
    [:div.card-footer.bg-transparent
     [:div.form-check
      [:input.form-check-input {:type "checkbox"
                                :name (str "libro_" (:id libro))
                                :value (:id libro)
                                :id (str "libro_" (:id libro))}]
      [:label.form-check-label.small {:for (str "libro_" (:id libro))}
       "Seleccionar"]]]]])

(defn- prestamo-row
  [p]
  (let [status (:status p)
        badge-class (case status
                      "activo" "bg-success"
                      "vencido" "bg-danger"
                      "bg-secondary")
        label (case status
                "activo" "Activo"
                "vencido" "Vencido"
                status)]
    [:tr
     [:td (:miembro_nombre p)]
     [:td (:fecha_prestamo p)]
     [:td (:fecha_vencimiento p)]
     [:td
      [:span.badge {:class badge-class}
       label]]
     [:td
      [:div.btn-group.btn-group-sm
       [:a.btn.btn-outline-success {:href (str "/prestamos/devolver/" (:id p))}
        "Devolver"]
       [:a.btn.btn-outline-primary {:href (str "/prestamos/renovar/" (:id p))}
        "Renovar"]]]]))

(defn main
  [title libros-disponibles prestamos-activos miembros & [error]]
  [:div.container-fluid
   [:h2.mb-4 "Escritorio de Préstamos"]
   (when error
     [:div.alert.alert-danger error])
   [:div.row
    [:div.col-lg-8
     [:div.card.shadow-sm.mb-4
      [:div.card-header.bg-primary.text-white
       [:h5.mb-0 "Nuevo Préstamo"]]
      [:div.card-body
       [:form {:method "POST" :action "/prestamos/crear"}
        (csrf-field)
        [:div.mb-3
         [:label.form-label.fw-semibold {:for "miembro_id"} "Miembro"]
         [:select.form-select {:id "miembro_id" :name "miembro_id" :required true}
          [:option {:value ""} "Seleccione un miembro..."]
          (for [m miembros]
            [:option {:value (:id m)} (str (:nombre m) " (" (:email m) ")")])]]
        [:h6.mb-3.text-muted "Libros disponibles para prestar"]
        (if (seq libros-disponibles)
          [:div.row
           (map libro-card libros-disponibles)]
          [:p.text-warning "No hay libros disponibles en este momento."])
        [:div.mt-4
         [:button.btn.btn-success {:type "submit"}
          "Crear Préstamo"]]]]]]
    [:div.col-lg-4
     [:div.card.shadow-sm.mb-4
      [:div.card-header.bg-info.text-white
       [:h5.mb-0 "Préstamos Activos"]]
      [:div.card-body.p-0
       (if (seq prestamos-activos)
         [:div.table-responsive
          [:table.table.table-sm.table-hover.mb-0
           [:thead.table-light
            [:tr
             [:th "Miembro"]
             [:th "Inicio"]
             [:th "Vence"]
             [:th "Estado"]
             [:th "Acciones"]]]
           [:tbody
            (map prestamo-row prestamos-activos)]]]
         [:p.text-muted.p-3 "No hay préstamos activos."])]]]]])
