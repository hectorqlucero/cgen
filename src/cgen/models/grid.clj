(ns cgen.models.grid
  (:require
   [clojure.string :as st]
   [cgen.i18n.core :as i18n]
   [cgen.models.export :as export]
   [cgen.web.csrf :refer [csrf-field]]))

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
  [page-info base-url current-params]
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
  [field-id field-label base-url current-params current-sort-by current-sort-order]
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
       {:type "search" :name "search" :placeholder (i18n/tr request :common/search)
        :value search
        :aria-label "Search"}]]
     [:div.col-auto
      [:button.btn.btn-sm.btn-outline-primary {:type "submit"}
       [:i.bi.bi-search.me-1] (i18n/tr request :common/search)]]
     (when (and search (not (st/blank? search)))
       [:div.col-auto
        [:a.btn.btn-sm.btn-outline-secondary {:href base-url}
         [:i.bi.bi-x-lg.me-1] (i18n/tr request :common/clear)]])]))

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
          (sortable-header (key field) (val field) href
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
         [:em (i18n/tr request :grid/no-records)]]]
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
        (pagination-bar page-info href (or current-params {})))]]))

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
           [:em (i18n/tr request :grid/no-records)]]]
         (for [row rows]
           [:tr
            (for [field fields]
              [:td.text-truncate.align-middle
               ((key field) row)])]))]]]]])

;; =============================================================================
;; Report (read-only table with export buttons)
;; =============================================================================

(defn- build-query-string
  "Builds a URL query string from a map of params, URL-encoding values."
  [params]
  (when (seq params)
    (st/join "&"
             (map (fn [[k v]]
                    (str (name k) "=" (java.net.URLEncoder/encode (str v) "UTF-8")))
                  params))))

(defn- filter-rows
  "Filters rows where any field value contains search string (case-insensitive)."
  [rows fields search]
  (let [s (st/lower-case search)]
    (filter (fn [row]
              (some (fn [[k _]]
                      (let [v (str (get row k ""))]
                        (st/includes? (st/lower-case v) s)))
                    fields))
            rows)))

(defn- sort-rows
  "Sorts rows by field-key, case-insensitive, in the given direction."
  [rows field-key direction]
  (let [key-fn #(st/lower-case (str (get % field-key "")))]
    (if (= direction :desc)
      (reverse (sort-by key-fn (remove nil? rows)))
      (sort-by key-fn (remove nil? rows)))))

(defn build-report
  "Renders a read-only report table with export buttons, search, and sort.
   Automatically handles ?export=csv, ?export=pdf, ?search=, ?sort-by=, and
   ?sort-order= query parameters from the request.
   Optional page-info and current-params can override auto-detection."
  [request title rows table-id fields & [page-info current-params]]
  (let [cp (or current-params
               (let [qp (:query-params request)]
                 (reduce-kv (fn [m k v]
                              (if (contains? #{"search" "sort-by" "sort-order"} k)
                                (assoc m (keyword k) v)
                                m))
                            {} (or qp {}))))
        search (get cp :search)
        rows (if (and search (not (st/blank? (str search))))
               (filter-rows rows fields search)
               rows)
        sort-by-field (get cp :sort-by)
        sort-order (get cp :sort-order "asc")
        rows (if sort-by-field
               (sort-rows rows (keyword sort-by-field) (keyword sort-order))
               rows)
        pi (or page-info
               (cond-> {}
                 sort-by-field (assoc :sort-by sort-by-field :sort-order sort-order)))
        export-fmt (get-in request [:query-params "export"])]
    (case export-fmt
      "csv"
      (let [csv-str (export/rows->csv rows fields)]
        {:type :response
         :response {:status 200
                    :headers {"Content-Type" "text/csv; charset=utf-8"
                              "Content-Disposition" (str "attachment; filename=\"" table-id ".csv\"")}
                    :body csv-str}})
      "pdf"
      (let [pdf-bytes (export/rows->pdf title rows fields)]
        {:type :response
         :response {:status 200
                    :headers {"Content-Type" "application/pdf"
                              "Content-Disposition" (str "attachment; filename=\"" table-id ".pdf\"")}
                    :body pdf-bytes}})
      ;; Default: render HTML with search and sort
      (let [base-url (:uri request)
            qs (build-query-string (dissoc cp :export))
            export-base (str base-url (when qs (str "?" qs)))]
        {:type :html
         :content
         [:div.card.shadow.mb-4
          [:style "@media print{nav.navbar,footer,#export-toolbar,.search-form{display:none!important}.card{box-shadow:none!important;border:1px solid #dee2e6}.bg-gradient{background:#0d6efd!important;color:#fff!important;-webkit-print-color-adjust:exact;print-color-adjust:exact}}"]
          [:div.card-body.bg-gradient.bg-primary.text-white.rounded-top.d-flex.justify-content-between.align-items-center
           [:h4.mb-0.fw-bold title]
           [:div#export-toolbar.d-flex.gap-1
            [:a.btn.btn-sm.btn-light {:href (str export-base (if qs "&" "?") "export=csv") :role "button"}
             [:i.bi.bi-file-earmark-spreadsheet.me-1] "Excel"]
            [:a.btn.btn-sm.btn-light {:href (str export-base (if qs "&" "?") "export=pdf") :role "button"}
             [:i.bi.bi-file-earmark-pdf.me-1] "PDF"]
            [:button.btn.btn-sm.btn-light {:type "button" :onclick "window.print()"}
             [:i.bi.bi-printer.me-1] "Print"]]]
          [:div.p-3.bg-white.rounded-bottom
           [:div.search-form (search-form request base-url cp)]
           [:div.table-responsive
            [:table.table.table-hover.table-bordered.table-striped.table-sm.compact.align-middle.w-100
             {:id table-id}
             [:thead
              [:tr
               (for [field fields]
                 (sortable-header (key field) (val field) base-url cp
                                  (keyword (or (:sort-by pi) "id"))
                                  (keyword (or (:sort-order pi) "asc"))))]]
             [:tbody
              (if (empty? rows)
                [:tr
                 [:td.text-center.text-muted.py-4
                  {:colspan (count fields)}
                  [:em (i18n/tr request :grid/no-records)]]]
                (for [row rows]
                  [:tr
                   (for [field fields]
                     [:td.text-truncate.align-middle
                      ((key field) row)])]))]]]]]}))))

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
