(ns cgen.tabgrid.handlers
  "HTTP handlers for TabGrid AJAX requests"
  (:require
   [hiccup2.core :refer [html]]
   [cgen.tabgrid.data :as data]
   [cgen.tabgrid.render :as render]
   [cgen.engine.config :as config]
   [cgen.models.crud :as crud]
   [cheshire.core :as json]
   [clojure.string :as str]
   [cgen.web.csrf :refer [csrf-field]]
   [cgen.layout :refer [application]]
   [cgen.models.util :refer [get-session-id]]
   [cgen.i18n.core :as i18n]))

(defn- render-html
  [hiccup-body]
  (str (html hiccup-body)))

(defn- safe-parse-long
  "Safely parses a string to long. Returns nil on nil or invalid input."
  [s]
  (when s
    (try (Long/parseLong (str s))
         (catch NumberFormatException _ nil))))

(defn- validate-m2m-params
  "Validates through_table, parent_fk, related_fk against known entity configs.
   Returns a map of validated keyword values, or nil if validation fails."
  [through-table parent-fk related-fk]
  (when (and through-table parent-fk related-fk)
    (try
      (let [entity-kw  (keyword through-table)
            entity-cfg (config/get-entity-config entity-kw)
            field-ids  (set (map :id (:fields entity-cfg)))
            parent-kw  (keyword parent-fk)
            related-kw (keyword related-fk)]
        (when (and (contains? field-ids parent-kw)
                   (contains? field-ids related-kw))
          {:through-table entity-kw
           :parent-fk     parent-kw
           :related-fk    related-kw}))
      (catch Exception _ nil))))

(defn handle-load-subgrid
  "AJAX handler: loads subgrid data for a specific parent"
  [request]
  (let [params (:params request)
        subgrid-entity (keyword (:subgrid_entity params))
        parent-id (:parent_id params)
        foreign-key (:foreign_key params)]

    (try
      (let [records (data/fetch-subgrid-records subgrid-entity parent-id foreign-key)
            fields (data/build-fields-map subgrid-entity)
            subgrid-config (config/get-entity-config subgrid-entity)
            actions (or (:actions subgrid-config) {:new true :edit true :delete true})]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success true
                 :records records
                 :count (count records)
                 :fields fields
                 :actions actions})})
      (catch Exception e
        (.printStackTrace e)
        {:status 500
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success false
                 :error (.getMessage e)})}))))

(defn handle-get-parent
  "AJAX handler: gets a specific parent record"
  [request]
  (let [params (:params request)
        entity (keyword (:entity params))
        parent-id (:parent_id params)]

    (try
      (let [record (data/fetch-parent-record entity parent-id)]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success true
                 :record record})})
      (catch Exception e
        {:status 500
         :headers {"Content-Type" "application/json"}
         :body (json/generate-string
                {:success false
                 :error (.getMessage e)})}))))

(defn handle-dissociate
  "POST /tabgrid/dissociate — deletes a row from the junction table, then redirects."
  [request]
  (let [params        (:params request)
        referer       (get-in request [:headers "referer"] "/")
        active-tab    (:active_tab params)
        location      (if active-tab
                        (let [base (str/replace referer #"active_tab=[^&]*"
                                                (str "active_tab=" active-tab))]
                          (if (= base referer)
                            (str referer
                                 (if (.contains ^String referer "?") "&" "?")
                                 "active_tab=" active-tab)
                            base))
                        referer)
        validated     (validate-m2m-params (:through_table params)
                                           (:parent_fk params)
                                           (:related_fk params))
        parent-id     (safe-parse-long (:parent_id params))
        related-id    (safe-parse-long (:related_id params))]
    (if (and validated parent-id related-id)
      (try
        (let [where [(str (name (:parent-fk validated)) " = ? AND "
                          (name (:related-fk validated)) " = ?")
                     parent-id related-id]]
          (crud/Delete (:through-table validated) where))
        {:status 302 :headers {"Location" location}}
        (catch Exception _e
          {:status 302 :headers {"Location" location}}))
      {:status 302 :headers {"Location" location}})))

(defn handle-m2m-pane
  "GET /tabgrid/m2m-pane — returns fresh HTML fragment for one M2M accordion section.
   Used by the client after associate/dissociate to refresh the pane without a full reload."
  [request]
  (let [params          (:params request)
        entity          (keyword (:entity params))
        parent-id       (:parent_id params)
        subgrid-entity  (:subgrid_entity params)   ;; raw entity name, e.g. "employee_skills"
        entity-config   (config/get-entity-config entity)
        entity-title    (or (:title entity-config) (name entity))
        parent-record   (data/fetch-parent-record entity parent-id)
        fields          (data/build-fields-map entity)
        subgrid-spec    (first (filter #(= (name (:entity %)) subgrid-entity)
                                       (:subgrids entity-config)))
        subgrid         (data/prepare-subgrid-config entity subgrid-spec parent-id)]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (render-html (render/render-m2m-pane request (name entity) entity-title
                                                subgrid parent-id))}))

(defn handle-subgrid-pane
  "GET /tabgrid/subgrid-pane — returns fresh HTML fragment for any subgrid type (1:N, 1:1, M:M).
   Used by the 'Show all' link and client-side refresh after associate/dissociate."
  [request]
  (let [params          (:params request)
        entity          (keyword (:entity params))
        parent-id       (:parent_id params)
        subgrid-entity  (:subgrid_entity params)
        show-all?       (= "true" (:show_all params))
        entity-config   (config/get-entity-config entity)
        subgrid-spec    (first (filter #(= (name (:entity %)) subgrid-entity)
                                       (:subgrids entity-config)))
        subgrid         (data/prepare-subgrid-config entity subgrid-spec parent-id)]
    {:status 200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body (render-html (render/subgrid-pane-body (name entity) parent-id subgrid
                                                  :show-all? show-all?))}))

(defn- pivot-visible-fields
  "Returns non-hidden, non-FK fields from a junction entity config."
  [junction-kw parent-fk-str related-fk-str]
  (let [cfg    (config/get-entity-config junction-kw)
        fk-ids #{(keyword parent-fk-str) (keyword related-fk-str)}]
    (remove #(or (= :hidden (:type %)) (contains? fk-ids (:id %)))
            (:fields cfg))))

(defn handle-pivot-form
  "GET /tabgrid/pivot-form — returns HTML form for editing junction pivot attributes."
  [request]
  (let [params      (:params request)
        validated   (validate-m2m-params (:through_table params)
                                         (:parent_fk params)
                                         (:related_fk params))]
    (if-not validated
      (application request "Invalid parameters" (get-session-id request) nil
                   [:div.container.py-4.text-center
                    [:h4.text-danger "Invalid parameters"]
                    [:p.text-muted "The submitted form data is invalid."]])
      (let [parent-id   (:parent_id params)
            related-id  (:related_id params)
            active-tab  (:active_tab params)
            return-url  (:return_url params)
            through-str (:through_table params)
            p-id        (safe-parse-long parent-id)
            r-id        (safe-parse-long related-id)
            junction    (:through-table validated)
            parent-fk   (name (:parent-fk validated))
            related-fk  (name (:related-fk validated))
            title       (i18n/tr :pivot/edit-title
                                 {:title (try (:title (config/get-entity-config junction))
                                              (catch Exception _ through-str))})
            fields      (pivot-visible-fields junction parent-fk related-fk)
            sql         [(str "SELECT * FROM " through-str
                              " WHERE " parent-fk " = ? AND " related-fk " = ?")
                         p-id r-id]
            current-row (first (crud/Query sql))]
        (application request title (get-session-id request) nil
                     [:div.container.py-4
                      [:h4.mb-3 title]
                      [:form {:method "POST" :action "/tabgrid/save-pivot"}
                       (csrf-field)
                       [:input {:type "hidden" :name "through_table" :value through-str}]
                       [:input {:type "hidden" :name "parent_fk"     :value parent-fk}]
                       [:input {:type "hidden" :name "parent_id"     :value parent-id}]
                       [:input {:type "hidden" :name "related_fk"    :value related-fk}]
                       [:input {:type "hidden" :name "related_id"    :value related-id}]
                       (when active-tab
                         [:input {:type "hidden" :name "active_tab" :value active-tab}])
                       (when return-url
                         [:input {:type "hidden" :name "return_url" :value return-url}])
                       (if (seq fields)
                         (for [field fields
                               :let [fid   (:id field)
                                     ftype (or (:type field) :text)
                                     options (or (:options field) [])
                                     value (or (get current-row fid) (:value field) "")]]
                           [:div.mb-3
                            [:label.form-label.fw-semibold (:label field)]
                            (case ftype
                              :number   [:input.form-control-lg.form-control
                                         {:type "number" :name (name fid) :value (str value)}]
                              :date     [:input.form-control-lg.form-control
                                         {:type "date" :name (name fid) :value (str value)}]
                              :select   [:select.form-select-lg.form-select {:name (name fid)}
                                         (for [opt options]
                                           [:option {:value (:value opt)} (:label opt)])]
                              :radio    [:div.mb-3
                                         [:div.mt-2
                                          (for [opt options]
                                            [:div.form-check.form-check-inline.me-4
                                             [:input.form-check-input
                                              {:type "radio"
                                               :name (name fid)
                                               :value (:value opt)
                                               :id (:id opt)
                                               :checked (when (= (str value) (str (:value opt))) true)}]
                                             [:label.form-check-label.fw-medium.ms-2
                                              {:for (:id opt)}
                                              (:label opt)]])]]
                              :textarea [:textarea.form-control-lg.form-control {:name (name fid) :rows 3} (str value)]
                              [:input.form-control-lg.form-control {:type "text" :name (name fid) :value (str value)}])])
                         [:p.text-muted (i18n/tr :pivot/no-attributes)])
                       [:div.d-flex.gap-2.mt-3
                        [:button.btn.btn-primary {:type "submit"}
                         [:i.bi.bi-check.me-1] (i18n/tr :common/save)]
                        [:a.btn.btn-secondary
                         {:href (if return-url
                                  (if active-tab
                                    (str return-url
                                         (if (.contains ^String return-url "?") "&" "?")
                                         "active_tab=" active-tab)
                                    return-url)
                                  "javascript:history.back()")}
                         (i18n/tr :common/cancel)]]]])))))

(defn handle-save-pivot
  "POST /tabgrid/save-pivot — updates pivot attributes on a junction table row."
  [request]
  (let [params          (:params request)
        validated       (validate-m2m-params (:through_table params)
                                             (:parent_fk params)
                                             (:related_fk params))
        parent-id       (safe-parse-long (:parent_id params))
        related-id      (safe-parse-long (:related_id params))
        active-tab      (:active_tab params)
        return-url      (:return_url params)
        location        (if return-url
                          (if active-tab
                            (str return-url
                                 (if (.contains ^String return-url "?") "&" "?")
                                 "active_tab=" active-tab)
                            return-url)
                          (get-in request [:headers "referer"] "/"))]
    (if (and validated parent-id related-id)
      (let [junction        (:through-table validated)
            parent-fk       (name (:parent-fk validated))
            related-fk      (name (:related-fk validated))
            pivot-field-ids (map :id (pivot-visible-fields junction parent-fk related-fk))
            row             (into {} (for [fid pivot-field-ids
                                           :let [v (get params (keyword (name fid)))]
                                           :when (some? v)]
                                       [fid v]))
            where           [(str parent-fk " = ? AND " related-fk " = ?")
                             parent-id related-id]]
        (try
          (when (seq row)
            (crud/Update junction row where))
          {:status 302 :headers {"Location" location}}
          (catch Exception _e
            {:status 302 :headers {"Location" location}})))
      {:status 302 :headers {"Location" location}})))

(defn handle-link-form
  "GET /tabgrid/link-form — full-page form with checkboxes to link M2M records."
  [request]
  (let [params          (:params request)
        validated       (validate-m2m-params (:through_table params)
                                             (:parent_fk params)
                                             (:related_fk params))
        parent-id       (safe-parse-long (:parent_id params))
        return-url      (:return_url params)]
    (if-not (and validated parent-id)
      (application request "Invalid parameters" (get-session-id request) nil
                   [:div.container.py-4.text-center
                    [:h4.text-danger "Invalid parameters"]
                    [:p.text-muted "The submitted form data is invalid."]])
      (let [through-str    (:through_table params)
            parent-fk      (name (:parent-fk validated))
            related-fk     (name (:related-fk validated))
            related-entity (keyword (:related_entity params))
            junction       (:through-table validated)
            junction-cfg   (try (config/get-entity-config junction) (catch Exception _ nil))
            title          (or (:title junction-cfg) (name junction))
            heading        (i18n/tr :subgrid/link-form-title {:title title})
            m2m-data       (data/fetch-many-to-many-records junction related-entity
                                                            parent-id
                                                            parent-fk related-fk)
            linked-ids     (or (:linked-ids m2m-data) #{})
            available      (data/fetch-available-for-linking related-entity linked-ids)
            fields         (data/build-fields-map related-entity)]
        (application request heading (get-session-id request) nil
                     [:div.container.py-4
                      [:h4.mb-3 heading]
                      [:form {:method "POST" :action "/tabgrid/link-save"}
                       (csrf-field)
                       [:input {:type "hidden" :name "through_table" :value through-str}]
                       [:input {:type "hidden" :name "parent_fk"     :value parent-fk}]
                       [:input {:type "hidden" :name "parent_id"     :value (:parent_id params)}]
                       [:input {:type "hidden" :name "related_fk"    :value related-fk}]
                       (when return-url
                         [:input {:type "hidden" :name "return_url" :value return-url}])
                       (when (:active_tab params)
                         [:input {:type "hidden" :name "active_tab" :value (:active_tab params)}])
                       (if (seq available)
                         [:div
                          [:p.text-muted (i18n/tr :subgrid/select-records)]
                          [:div.table-responsive
                           [:table.table.table-hover.table-sm
                            [:thead
                             [:tr
                              [:th {:style "width:48px"}
                               [:input.form-check-input {:type "checkbox" :id "m2m-select-all"}]]
                              (for [[_ label] fields] [:th label])]]
                            (into [:tbody]
                                  (for [row available]
                                    [:tr
                                     [:td {:data-label ""}
                                      [:input.form-check-input {:type "checkbox" :name "selected_ids"
                                                                :value (:id row)}]]
                                     (for [[fid label] fields]
                                       [:td.small {:data-label label} (get row fid)])]))]]
                          [:div.d-flex.gap-2.mt-3
                           [:button.btn.btn-primary {:type "submit"}
                            [:i.bi.bi-check.me-1] (i18n/tr :common/save)]
                           [:a.btn.btn-secondary
                            {:href (if return-url
                                     (if (:active_tab params)
                                       (str return-url
                                            (if (.contains ^String return-url "?") "&" "?")
                                            "active_tab=" (:active_tab params))
                                       return-url)
                                     "javascript:history.back()")}
                            (i18n/tr :common/cancel)]]]
                         [:div.text-center.p-4.text-muted
                          [:i.bi.bi-check-circle {:style "font-size:2rem"}]
                          [:p.mt-2 (i18n/tr :subgrid/no-available-records)]
                          [:a.btn.btn-secondary
                           {:href (if return-url
                                    (if (:active_tab params)
                                      (str return-url
                                           (if (.contains ^String return-url "?") "&" "?")
                                           "active_tab=" (:active_tab params))
                                      return-url)
                                    "javascript:history.back()")}
                           (i18n/tr :common/back)]])
                       [:script
                        "document.getElementById('m2m-select-all').addEventListener('change',function(){var c=document.querySelectorAll('input[name=\"selected_ids\"]');for(var i=0;i<c.length;i++)c[i].checked=this.checked;})"]]])))))
(defn handle-link-save
  "POST /tabgrid/link-save — inserts junction rows for each selected record, then redirects."
  [request]
  (let [params        (:params request)
        validated     (validate-m2m-params (:through_table params)
                                           (:parent_fk params)
                                           (:related_fk params))
        parent-id     (safe-parse-long (:parent_id params))
        selected-ids  (:selected_ids params)
        return-url    (:return_url params)
        active-tab    (:active_tab params)
        ids           (if (sequential? selected-ids) selected-ids [selected-ids])
        location      (if return-url
                        (if active-tab
                          (str return-url
                               (if (.contains ^String return-url "?") "&" "?")
                               "active_tab=" active-tab)
                          return-url)
                        "/")]
    (if (and validated parent-id ids)
      (try
        (doseq [rid ids]
          (let [parsed-rid (safe-parse-long rid)]
            (when parsed-rid
              (crud/Insert (:through-table validated)
                           {(:parent-fk validated) parent-id
                            (:related-fk validated) parsed-rid}))))
        {:status 302 :headers {"Location" location}}
        (catch Exception _e
          {:status 302 :headers {"Location" location}}))
      {:status 302 :headers {"Location" location}})))
