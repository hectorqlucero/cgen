(ns contactos.models.grid
  (:require
   [clojure.string :as st]
   [contactos.i18n.core :as i18n]
   [contactos.web.csrf :refer [csrf-field]]))

;; =============================================================================
;; Pagination rendering
;; =============================================================================

(defn- page-url [base-url params extra-params]
  (str base-url "?" (st/join "&"
       (map (fn [[k v]]
              (str (name k) "=" (java.net.URLEncoder/encode (str v) "UTF-8")))
            (merge params extra-params)))))

(defn- pagination-link [label url active? disabled?]
  [:li.page-item
   {:class (str (when active? " active") (when disabled? " disabled"))}
   (if (or active? disabled?)
     [:a.page-link {:href "#" :tabindex "-1" :aria-disabled "true"} label]
     [:a.page-link {:href url} label])])

(defn pagination-bar
  "Renders a Bootstrap 5 pagination nav.
   page-info is a map with :page, :total-pages, :per-page, :total.
   base-url is the URL path to link to.
   current-params is a map of query params to preserve (search, sort-by, etc)."
  [request page-info base-url current-params]
  (let [{:keys [page total-pages per-page total]} page-info]
    (when (and per-page (pos? per-page) total-pages (pos? total-pages))
      (let [base (dissoc current-params :page)]
        [:nav {:aria-label "Table pagination"}
         [:ul.pagination.pagination-sm.justify-content-center.mb-0.flex-wrap
          (pagination-link "&laquo;" (page-url base-url base {:page 1})
                           false (= page 1))
          (pagination-link "&lsaquo;" (page-url base-url base {:page (dec page)})
                           false (= page 1))
          (for [p (take 7
                       (if (<= total-pages 7)
                         (range 1 (inc total-pages))
                         (let [start (max 1 (- page 3))
                               end (min total-pages (+ page 3))]
                           (if (<= (- end start) 6)
                             (range start (inc end))
                             (sort (set (concat (range (max 1 (- page 3)) (inc page))
                                                (range page (min (inc total-pages) (+ page 4))))))))))]
            (pagination-link (str p) (page-url base-url base {:page p})
                             (= p page) false))
          (when (> total-pages 7)
            (pagination-link "&hellip;" "#" false true))
          (pagination-link "&rsaquo;" (page-url base-url base {:page (inc page)})
                           false (= page total-pages))
          (pagination-link "&raquo;" (page-url base-url base {:page total-pages})
                           false (= page total-pages))]
         [:div.text-center.text-muted.small.mt-1
          (str "Page " page " of " total-pages " (" total " records)")]]))))

;; =============================================================================
;; Sortable column header
;; =============================================================================

(defn sortable-header
  "Renders a sortable column header <th> with link.
   field-id is a keyword, field-label is a string.
   current-sort-by and current-sort-order control the active sort indicator.
   base-url is the URL path. current-params preserves existing params."
  [request field-id field-label base-url current-params current-sort-by current-sort-order]
  (let [field-name (name field-id)
        is-active (= (name current-sort-by) field-name)
        new-order (if (and is-active (= current-sort-order :asc)) :desc :asc)
        sort-icon (cond
                    (not is-active) ""
                    (= current-sort-order :asc) " &#9650;"
                    :else " &#9660;")
        params (assoc current-params :sort-by field-name :sort-order (name new-order) :page 1)
        href (str base-url "?" (st/join "&"
                  (map (fn [[k v]]
                         (str (name k) "=" (java.net.URLEncoder/encode (str v) "UTF-8")))
                       params)))]
    [:th.text-nowrap.text-uppercase.fw-semibold.px-2
     [:a {:href href
          :class (when is-active "text-decoration-underline")
          :style "color:inherit;text-decoration:none;"}
      (st/upper-case field-label) (when is-active [:span {:style "font-size:0.7em"} sort-icon])]]))

;; =============================================================================
;; Search form
;; =============================================================================

(defn search-form
  "Renders a search input above the table."
  [request base-url current-params]
  (let [search (get current-params :search "")
        params (dissoc current-params :search :page)]
    [:form {:method "GET" :action base-url :class "row gx-2 gy-1 align-items-center mb-2"}
     (doall
      (for [[k v] params]
        [:input {:type "hidden" :name (name k) :value (str v)}]))
     [:div.col-auto
      [:input.form-control.form-control-sm
       {:type "search" :name "search" :placeholder (i18n/tr request :datatables/search "Search...")
        :value search
        :aria-label "Search"}]]
     [:div.col-auto
      [:button.btn.btn-sm.btn-outline-primary {:type "submit"}
       [:i.bi.bi-search.me-1] (i18n/tr request :common/search "Search")]]
     (when (and search (not (st/blank? search)))
       [:div.col-auto
        [:a.btn.btn-sm.btn-outline-secondary {:href base-url}
         [:i.bi.bi-x-lg.me-1] (i18n/tr request :common/clear "Clear")]])]))

;; =============================================================================
;; Table head (with optional sortable headers)
;; =============================================================================

(defn build-grid-head
  "Renders a table header row with sortable columns and a New-record action cell.
   When page-info is provided, columns are rendered as sortable links."
  [request href fields & [args page-info current-params]]
  (let [new-record (:new args)
        {:keys [sort-by sort-order]} page-info]
    [:thead
     [:tr
      (for [field fields]
        (if (and page-info sort-by)
          (sortable-header request (key field) (val field) href
                           (or current-params {})
                           (keyword (or sort-by :id)) (keyword (or sort-order :desc)))
          [:th.text-nowrap.text-uppercase.fw-semibold.px-2
           (st/upper-case (val field))]))
      [:th.text-center.px-2
       {:style "width:1%; white-space:nowrap; padding-left:0.25rem; padding-right:0.25rem;"}
       (when new-record
         [:a.btn.btn-success.btn-sm.fw-semibold.shadow-sm
          {:href (str href "/add-form") :role "button"}
          [:i.bi.bi-plus-lg.me-1]
          (i18n/tr request :common/new)])]]]))

;; =============================================================================
;; Table body
;; =============================================================================

(defn build-grid-body
  "Renders table body rows with edit/delete action buttons.
   rows is a seq of record maps."
  [request rows href fields & [args]]
  (let [{:keys [edit delete]} args]
    [:tbody
     (if (empty? rows)
       [:tr
        [:td.text-center.text-muted.py-4
         {:colspan (+ (count fields) 1)}
         [:em (i18n/tr request :grid/no-records "No records found")]]]
       (for [row rows]
         [:tr
          (for [field fields]
            [:td.text-truncate.align-middle
             ((key field) row)])
          [:td.text-center.align-middle
           {:style "width:1%; white-space:nowrap; padding-left:0.25rem; padding-right:0.25rem;"}
           [:div.d-flex.justify-content-center.align-items-center.gap-1
            (when edit
              [:a.btn.btn-warning.btn-sm.fw-semibold.shadow-sm.rounded-pill
               {:href (str href "/edit-form/" (:id row)) :role "button"}
               [:i.bi.bi-pencil.me-1]
               (i18n/tr request :common/edit)])
            (when delete
              [:form {:method "POST"
                      :action (str href "/delete/" (:id row))
                      :style "display:inline"
                      :onsubmit "return confirm('Are you sure?')"}
               (csrf-field)
               [:button.btn.btn-danger.btn-sm.fw-semibold.shadow-sm.rounded-pill
                {:type "submit"}
                [:i.bi.bi-trash.me-1]
                (i18n/tr request :common/delete)]])]]]))]))

;; =============================================================================
;; Full grid (card + table + pagination + search)
;; =============================================================================

(defn build-grid
  "Renders a complete grid with search, sortable table, and pagination.
   Args:
   - request: Ring request
   - title: String heading
   - rows: Collection of record maps
   - table-id: String DOM id
   - fields: Array-map of {keyword label}
   - href: String base URL path
   - args: Map with :new, :edit, :delete booleans
   - page-info: Optional map with :page, :per-page, :total, :total-pages, :sort-by, :sort-order
   - current-params: Optional map of current query params (search, sort, etc.)"
  [request title rows table-id fields href & [args page-info current-params]]
  (let [args (or args {})]
    [:div.card.shadow.mb-4
     [:div.card-body.bg-gradient.bg-primary.text-white.rounded-top
      [:h4.mb-0.fw-bold title]]
     [:div.p-3.bg-white.rounded-bottom
      (search-form request href (or current-params {}))
      [:div.table-responsive
       [:table.table.table-hover.table-bordered.table-striped.table-sm.compact.align-middle.w-100
        {:id table-id}
        (build-grid-head request href fields args page-info current-params)
        (build-grid-body request rows href fields args)]]
      (when page-info
        (pagination-bar request page-info href (or current-params {})))]]))

;; =============================================================================
;; Dashboard (read-only table, no actions)
;; =============================================================================

(defn build-dashboard
  "Renders a read-only dashboard table."
  [request title rows table-id fields]
  [:div.card.shadow.mb-4
   [:div.card-body.bg-gradient.bg-primary.text-white.rounded-top
    [:h4.mb-0.fw-bold title]]
   [:div.p-3.bg-white.rounded-bottom
    [:div.table-responsive
     [:table.table.table-hover.table-bordered.table-striped.table-sm.compact.align-middle.w-100
      {:id table-id}
      [:thead
       [:tr
        (for [field fields]
          [:th.text-nowrap.text-uppercase.fw-semibold.px-2
           (st/upper-case (val field))])]]
      [:tbody
       (if (empty? rows)
         [:tr
          [:td.text-center.text-muted.py-4
           {:colspan (count fields)}
           [:em (i18n/tr request :grid/no-records "No records found")]]]
         (for [row rows]
           [:tr
            (for [field fields]
              [:td.text-truncate.align-middle
               ((key field) row)])]))]]]]])

;; =============================================================================
;; Modal wrapper (kept for backward compatibility with tabgrid forms)
;; =============================================================================

(defn build-modal
  "Wraps a form in a Bootstrap modal container."
  [title _ form]
  (list
   [:div.modal.fade {:id "exampleModal"
                     :data-bs-backdrop "static"
                     :data-bs-keyboard "false"
                     :tabindex "-1"
                     :aria-labelledby "exampleModalLabel"
                     :aria-hidden "true"}
    [:div.modal-dialog.modal-dialog-centered {:style "max-width: 700px; width: 100%;"}
     [:div.modal-content
      [:div.modal-header.bg-primary.text-white
       [:h1.modal-title.fs-5.fw-bold {:id "exampleModalLabel"
                                      :style "margin: 0; font-size: 1.25rem; text-shadow: 0 1px 3px rgba(0,0,0,0.2); letter-spacing: 0.025em;"}
        title]
       [:button.btn-close
        {:type "button"
         :data-bs-dismiss "modal"
         :aria-label "Close"}]]
      [:div.modal-body.p-0.w-100
       (if (and (vector? form) (#{:div :form} (first form)))
         (let [[tag attrs & body] form
               class-str (-> (or (:class attrs) "")
                             (st/replace #"container-fluid" "")
                             (st/replace #"container" "")
                             (st/replace #"d-flex" "")
                             (st/replace #"justify-content-center" "")
                             (st/replace #"align-items-center" "")
                             (str " w-100")
                             st/trim)
               new-attrs (assoc attrs :class class-str)]
           (into [tag new-attrs] body))
         [:div.w-100 form])]]]]))

(defn modal-script
  "Inline script to show a Bootstrap modal (used by tabgrid form fragments)."
  []
  [:script
   "
   var myModal = new bootstrap.Modal(document.getElementById('exampleModal'), {
    keyboard: false
   })
   myModal.show();
   "])

;; =============================================================================
;; Subgrid modal (kept for backward compatibility)
;; =============================================================================

(defn build-subgrid-modal
  "Modal container for legacy subgrids."
  [subgrid-url]
  [:div.modal.fade {:id "subgridModal"
                    :data-bs-backdrop "static"
                    :data-bs-keyboard "false"
                    :tabindex "-1"
                    :aria-labelledby "subgridModalLabel"
                    :aria-hidden "true"}
   [:div.modal-dialog.modal-xl.modal-dialog-centered
    [:div.modal-content
     [:div.modal-header.bg-info.text-white
      [:h1.modal-title.fs-5.fw-bold {:id "subgridModalLabel"} "Subgrid"]
      [:button.btn-close
       {:type "button"
        :data-bs-dismiss "modal"
        :aria-label "Close"}]]
     [:div.modal-body.p-2
      [:div#subgrid-content {:data-url subgrid-url}
       [:div.text-center.p-4
        [:div.spinner-border.text-primary {:role "status"}]
        [:div.mt-2 "Loading..."]]]]
     [:div.modal-footer
      [:button.btn.btn-secondary
       {:type "button"
        :data-bs-dismiss "modal"}
       "Close"]]]]])

;; =============================================================================
;; Grid with custom new-record URL (used by render-subgrid in tabgrid)
;; =============================================================================

(defn build-grid-with-custom-new
  "Builds a grid with a custom new-record URL.
   Used by render-subgrid for subgrid forms."
  [request title rows table-id fields href args custom-new-url]
  (let [new? (:new args)]
    [:div.card.shadow.mb-4
     [:div.card-body.bg-gradient.bg-primary.text-white.rounded-top
      [:h4.mb-0.fw-bold title]]
     [:div.p-3.bg-white.rounded-bottom
      [:div.table-responsive
       [:table.table.table-hover.table-bordered.table-striped.table-sm.compact.align-middle.w-100
        {:id table-id}
        [:thead
         [:tr
          (for [field fields]
            [:th.text-nowrap.text-uppercase.fw-semibold.px-2
             (st/upper-case (val field))])
          [:th.text-center.px-2
           {:style "width:1%; white-space:nowrap; padding-left:0.25rem; padding-right:0.25rem;"}
           [:div.d-flex.justify-content-center.align-items-center
            (when new?
              [:a.btn.btn-success.btn-sm.fw-semibold.shadow-sm
               {:href custom-new-url :role "button"}
               [:i.bi.bi-plus-lg.me-1]
               (i18n/tr request :common/new)])]]]]
        (build-grid-body request rows href fields args)]]]]))


;; =============================================================================
;; Subgrid helper (kept for backward compatibility — renders plain links now)
;; =============================================================================

(defn build-subgrid-trigger
  "Creates a link to open a subgrid page (no longer uses modals)."
  [parent-record subgrid-config]
  (let [{:keys [title href icon label]} subgrid-config
        parent-id (get parent-record :id)
        subgrid-url (str href "?parent_id=" parent-id)]
    [:a.btn.btn-info.btn-sm.me-1
     {:href subgrid-url :role "button"}
     [:i {:class (or icon "bi bi-list-ul")}]
     (when label [:span.ms-1 label])]))

(defn build-enhanced-grid-body
  "Enhanced grid body with subgrid triggers and action buttons."
  [request rows href fields args subgrid-configs]
  [:tbody
   (if (empty? rows)
     [:tr
      [:td.text-center.text-muted.py-4
       {:colspan (+ (count fields) 1)}
       [:em (i18n/tr request :grid/no-records "No records found")]]]
     (for [row rows]
       [:tr
        (for [field fields]
          [:td.text-truncate.align-middle
           ((key field) row)])
        [:td.text-center.align-middle
         {:style "width:1%; white-space:nowrap; padding-left:0.25rem; padding-right:0.25rem;"}
         [:div.d-flex.justify-content-center.align-items-center.gap-1
          (for [sg subgrid-configs]
            (build-subgrid-trigger row sg))
          (let [edit (:edit args)
                delete (:delete args)]
            (when edit
              [:a.btn.btn-warning.btn-sm.fw-semibold.shadow-sm.rounded-pill
               {:href (str href "/edit-form/" (:id row)) :role "button"}
               [:i.bi.bi-pencil.me-1]
               (i18n/tr request :common/edit)])
            (when delete
              [:form {:method "POST"
                      :action (str href "/delete/" (:id row))
                      :style "display:inline"
                      :onsubmit "return confirm('Are you sure?')"}
               (csrf-field)
               [:button.btn.btn-danger.btn-sm.fw-semibold.shadow-sm.rounded-pill
                {:type "submit"}
                [:i.bi.bi-trash.me-1]
                (i18n/tr request :common/delete)]]))]]]))])

(defn build-grid-with-subgrids
  "Enhanced version of build-grid that supports subgrid trigger links."
  [request title rows table-id fields href args]
  (let [subgrid-configs (:subgrids args)
        has-subgrids? (seq subgrid-configs)]
    (if-not has-subgrids?
      (build-grid request title rows table-id fields href args)
      [:div.card.shadow.mb-4
       [:div.card-body.bg-gradient.bg-primary.text-white.rounded-top
        [:h4.mb-0.fw-bold title]]
       [:div.p-3.bg-white.rounded-bottom
        [:div.table-responsive
         [:table.table.table-hover.table-bordered.table-striped.table-sm.compact.align-middle.w-100
          {:id table-id}
          [:thead
           [:tr
            (for [field fields]
              [:th.text-nowrap.text-uppercase.fw-semibold.px-2
               (st/upper-case (val field))])
            [:th.text-center.px-2
             {:style "width:1%; white-space:nowrap; padding-left:0.25rem; padding-right:0.25rem;"}
             [:div.d-flex.justify-content-center.align-items-center
              (when (:new args)
                [:a.btn.btn-success.btn-sm.fw-semibold.shadow-sm
                 {:href (str href "/add-form") :role "button"}
                 [:i.bi.bi-plus-lg.me-1]
                 (i18n/tr request :common/new)])]]]]
          (build-enhanced-grid-body request rows href fields args subgrid-configs)]]
        (build-subgrid-modal (get-in (first subgrid-configs) [:href]))]])))

(defn create-subgrid-config
  "Helper to create subgrid configuration map."
  [options]
  (merge
   {:primary-key "id"
    :icon "bi bi-list-ul"
    :label nil}
   options))

(comment
  ;; Usage examples for pagination-bar
  (pagination-bar nil {:page 1 :total-pages 5 :per-page 10 :total 42}
                  "/admin/users" {:search "john" :sort-by "name" :sort-order "asc"})
  ;; Usage examples for build-grid
  (build-grid nil "Users"
              [{:id 1 :name "Alice"} {:id 2 :name "Bob"}]
              "users-table"
              (array-map :id "Id" :name "Name")
              "/admin/users"
              {:new true :edit true :delete true}
              {:page 1 :per-page 10 :total 2 :total-pages 1 :sort-by "name" :sort-order "asc"}
              {:search ""}))
