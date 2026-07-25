(ns cgen.tabgrid.data
  "Data fetching and transformation for TabGrid.
   Each relationship type has its own fetch strategy:
     :one-to-many  - SQL WHERE filtering on foreign key
     :one-to-one   - SQL WHERE filtering, returns single record
     :many-to-many - junction table join + related entity merge"
  (:require
   [clojure.string :as str]
   [cgen.engine.config :as config]
   [cgen.engine.query :as query]
   [cgen.models.crud :as crud]))

(defn build-fields-map
  "Builds a field map for rendering from entity config"
  [entity]
  (let [display-fields (config/get-display-fields entity)]
    (apply array-map
           (mapcat (fn [field]
                     [(:id field) (:label field)])
                   display-fields))))

(defn fetch-parent-record
  "Fetches a single parent record by ID"
  [entity parent-id]
  (when parent-id
    (query/get-with-hooks entity parent-id)))

(defn fetch-all-parent-records
  "Fetches all parent records for selection modal"
  [entity]
  (query/list-with-hooks entity))

(defn fetch-subgrid-records
  "Fetches subgrid records filtered by parent foreign key using SQL WHERE."
  [subgrid-entity parent-id foreign-key]
  (when (and subgrid-entity parent-id foreign-key)
    (let [cfg     (config/get-entity-config subgrid-entity)
          table   (:table cfg)
          conn    (:connection cfg)
          fk-name (name foreign-key)]
      (if table
        (crud/Query [(str "SELECT * FROM " table " WHERE " fk-name " = ?")
                     (Long/parseLong (str parent-id))]
                    :conn conn)
        ;; fallback: no table in config, use list query + filter
        (let [fk-kw (keyword foreign-key)]
          (filter #(= (str (get % fk-kw)) (str parent-id))
                  (query/list-with-hooks subgrid-entity)))))))

(defn fetch-one-to-one-record
  "Fetches the single associated record for a one-to-one relationship, or nil.
   Uses SQL WHERE filtering."
  [sg-entity parent-id foreign-key]
  (when (and sg-entity parent-id foreign-key)
    (let [cfg     (config/get-entity-config sg-entity)
          table   (:table cfg)
          conn    (:connection cfg)
          fk-name (name foreign-key)]
      (if table
        (first (crud/Query [(str "SELECT * FROM " table " WHERE " fk-name " = ?")
                            (Long/parseLong (str parent-id))]
                           :conn conn))
        ;; fallback
        (let [fk-kw (keyword foreign-key)]
          (first (filter #(= (str (get % fk-kw)) (str parent-id))
                         (query/list-with-hooks sg-entity))))))))

(defn fetch-many-to-many-records
  "Returns the related-entity rows linked to parent-id via a junction table,
   enriched with junction-table attributes (e.g. proficiency, role).
   Uses SQL WHERE filtering for both junction and related queries.
   Returns {:records [...] :linked-ids #{...}}."
  [junction-entity related-entity parent-id parent-fk related-fk]
  (when (and junction-entity parent-id parent-fk)
    (let [parent-fk-kw  (keyword parent-fk)
          related-fk-kw (keyword related-fk)
          junction-cfg  (config/get-entity-config junction-entity)
          j-table       (:table junction-cfg)
          j-conn        (:connection junction-cfg)
          parent-id-val (Long/parseLong (str parent-id))
          junctions     (if j-table
                          (crud/Query [(str "SELECT * FROM " j-table
                                          " WHERE " (name parent-fk) " = ?")
                                       parent-id-val]
                                      :conn j-conn)
                          (filter #(= (str (get % parent-fk-kw)) (str parent-id))
                                  (query/list-with-hooks junction-entity)))
          related-by-id (when related-entity
                          (let [r-cfg (config/get-entity-config related-entity)]
                            (into {} (map (fn [r] [(:id r) r])
                                          (if (:table r-cfg)
                                            (crud/Query (str "SELECT * FROM " (:table r-cfg))
                                                        :conn (:connection r-cfg))
                                            (query/list-with-hooks related-entity))))))
          linked-ids    (into #{} (map #(get % related-fk-kw) junctions))]
      {:records   (mapv (fn [j]
                          (merge (get related-by-id (get j related-fk-kw) {})
                                 j))
                        junctions)
       :linked-ids linked-ids})))

(defn fetch-available-for-linking
  "Returns related-entity records that are NOT yet linked via the junction table.
   Uses SQL WHERE NOT IN for efficiency when possible."
  [related-entity linked-ids]
  (when related-entity
    (let [cfg    (config/get-entity-config related-entity)
          table  (:table cfg)
          conn   (:connection cfg)]
      (if (and table (seq linked-ids))
        (let [placeholders (str/join "," (repeat (count linked-ids) "?"))
              params       (mapv #(Long/parseLong (str %)) linked-ids)
              sql-vec      (into [(str "SELECT * FROM " table " WHERE id NOT IN (" placeholders ")")]
                                 params)
              records (crud/Query sql-vec :conn conn)]
          records)
        ;; fallback: load all and filter
        (remove #(contains? linked-ids (:id %))
                (query/list-with-hooks related-entity))))))

(defn prepare-subgrid-config
  "Prepares a single subgrid configuration for rendering.
   Pre-fetches data for all relationship types (no more AJAX lazy loading)."
  [_parent-entity subgrid-spec parent-id]
  (let [sg-entity  (:entity subgrid-spec)
        sg-config  (config/get-entity-config sg-entity)
        sg-fields  (build-fields-map sg-entity)
        fk         (:foreign-key subgrid-spec)
        rel-type   (or (:relationship-type subgrid-spec) :one-to-many)
        base       {:entity            sg-entity
                    :title             (or (:title subgrid-spec) (:title sg-config) (name sg-entity))
                    :foreign-key       fk
                    :icon              (or (:icon subgrid-spec) "bi bi-list-ul")
                    :label             (or (:label subgrid-spec) (:title sg-config))
                    :relationship-type rel-type
                    :through-table     (or (:through-table subgrid-spec) sg-entity)
                    :related-entity    (:related-entity subgrid-spec)
                    :related-fk        (:related-fk subgrid-spec)
                    :fields            sg-fields
                    :actions           (or (:actions sg-config) {:new true :edit true :delete true})}]
    (case rel-type
      :one-to-one
      (let [record (when parent-id (fetch-one-to-one-record sg-entity parent-id fk))]
        (assoc base
               :record record
               :count  (if record 1 0)))

      :many-to-many
      (let [junction       (or (:through-table subgrid-spec) sg-entity)
            related        (:related-entity subgrid-spec)
            related-fk     (:related-fk subgrid-spec)
            related-fields (when related (build-fields-map related))
            m2m-data       (when parent-id
                             (fetch-many-to-many-records junction related parent-id fk related-fk))
            records        (or (:records m2m-data) [])
            linked-ids     (or (:linked-ids m2m-data) #{})
            available      (fetch-available-for-linking related linked-ids)]
        (assoc base
               ;; Use related-entity fields for display in the M2M pane
               :fields         (or related-fields sg-fields)
               :records        records
               :available      available
               :related-fields (or related-fields sg-fields)
               :count          (count records)))

      ;; :one-to-many — pre-fetch all records server-side
      (let [records (when parent-id (fetch-subgrid-records sg-entity parent-id fk))]
        (assoc base
               :records records
               :count (count records))))))

(defn prepare-all-subgrids
  "Prepares all subgrid configurations from parent entity config, with record counts."
  [parent-entity parent-id]
  (let [parent-config (config/get-entity-config parent-entity)
        subgrid-specs (:subgrids parent-config)]
    (when (seq subgrid-specs)
      (mapv #(prepare-subgrid-config parent-entity % parent-id) subgrid-specs))))

(defn prepare-tabgrid-data
  "Prepares all data needed for tabgrid rendering"
  [entity parent-id]
  (let [entity-config (config/get-entity-config entity)
        ;; Get ALL records for the selection modal
        all-records (fetch-all-parent-records entity)
        ;; Get the specific parent record to display
        parent-record (if parent-id
                        (fetch-parent-record entity parent-id)
                        (first all-records))
        ;; Parent display shows ONLY the selected record (or first if none selected)
        parent-display-rows (if parent-record [parent-record] [])
        fields (build-fields-map entity)
        subgrids (prepare-all-subgrids entity (when parent-record
                                                (or (:id parent-record)
                                                    parent-id)))
        actions (or (:actions entity-config) {:new true :edit true :delete true})]
    {:entity entity
     :entity-name (name entity)
     :title (:title entity-config)
     :parent-record parent-record
     :parent-rows parent-display-rows  ; Single record for display
     :all-records all-records          ; All records for modal
     :fields fields
     :subgrids subgrids
     :actions actions}))
