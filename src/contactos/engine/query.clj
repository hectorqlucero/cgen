(ns contactos.engine.query
  (:require
   [clojure.string :as str]
   [contactos.engine.config :as config]
   [contactos.models.crud :as crud]
   [contactos.models.crud :refer [Query]]))

;; =============================================================================
;; Query Resolution
;; =============================================================================

(defn- resolve-query
  [query-def]
  (cond
    (string? query-def) query-def
    (fn? query-def) query-def
    (keyword? query-def) (config/resolve-fn-ref query-def)
    :else (throw (ex-info "Invalid query definition" {:query query-def}))))

(defn get-query
  "Gets a query definition from an entity configuration."
  [entity query-key]
  (let [config (config/get-entity-config entity)
        query (get-in config [:queries query-key])]
    (when-not query
      (throw (ex-info "Query not defined for entity"
                      {:entity entity :query-key query-key})))
    (resolve-query query)))

(defn execute-query
  [entity {:keys [query-key params conn] :or {params [] conn nil}}]
  (let [config (config/get-entity-config entity)
        connection (or conn (:connection config) :default)
        query-def (get-in config [:queries query-key])
        query (resolve-query query-def)]

    (cond
      ;; SQL string query
      (string? query)
      (if (empty? params)
        (crud/Query query :conn connection)
        (crud/Query (into [query] params) :conn connection))

      ;; Function query
      (fn? query)
      (query params connection)

      :else
      (throw (ex-info "Unsupported query type"
                      {:entity entity :query-key query-key :query query})))))

(defn list-records
  "Executes the :list query for an entity.
   Returns all records according to the entity's list query."
  [entity & [opts]]
  (execute-query entity (merge {:query-key :list} opts)))

(defn- parse-composite-id
  "Parse composite key IDs that are pipe-separated (e.g., '1|2').
   Returns a vector of individual key values."
  [id primary-key]
  (if (and primary-key (vector? primary-key) (> (count primary-key) 1))
    (str/split (str id) #"\|")
    [id]))

(defn get-record
  "Executes the :get query for an entity with an ID.
   Returns a single record. Handles both simple and composite keys."
  [entity id & [opts]]
  (let [config (config/get-entity-config entity)
        params (parse-composite-id id (:primary-key config))]
    (first
     (execute-query entity
                    (merge {:query-key :get :params params} opts)))))

(defn custom-query
  "Executes a custom named query from the entity configuration.
   Example: (custom-query :users :active-users)"
  [entity query-name & [params opts]]
  (execute-query entity
                 (merge {:query-key query-name
                         :params (or params [])}
                        opts)))

(defn generate-list-query
  "Generates a default list query for an entity based on its table."
  [entity]
  (let [config (config/get-entity-config entity)
        table (:table config)
        fields (config/get-display-fields entity)
        field-names (map (comp name :id) fields)
        field-list (clojure.string/join ", " field-names)]
    (str "SELECT " field-list " FROM " table " ORDER BY id DESC")))

(defn generate-get-query
  "Generates a default get query for an entity based on its table."
  [entity]
  (let [config (config/get-entity-config entity)
        table (:table config)]
    (str "SELECT * FROM " table " WHERE id = ?")))

(defn ensure-default-queries
  "Ensures an entity has default queries if not explicitly defined."
  [entity]
  (let [config (config/get-entity-config entity)
        queries (:queries config {})
        queries (if (:list queries)
                  queries
                  (assoc queries :list (generate-list-query entity)))
        queries (if (:get queries)
                  queries
                  (assoc queries :get (generate-get-query entity)))]
    (swap! config/config-cache assoc-in [entity :queries] queries)
    queries))

(defn execute-with-hooks
  "Executes a query with before/after hooks.
   
   Options:
   - :before-hook - Hook function to call before query
   - :after-hook - Hook function to call after query
   - :query-key - Which query to execute
   - :params - Query parameters
   - :conn - Connection key"
  [entity {:keys [before-hook after-hook] :as opts}]
  (let [config (config/get-entity-config entity)
        params (:params opts [])

        ;; Execute before hook
        params (if before-hook
                 (before-hook params)
                 params)

        ;; Execute query
        result (execute-query entity (assoc opts :params params))

        ;; Execute after hook
        result (if after-hook
                 (after-hook result params)
                 result)]

    result))

(defn list-with-hooks
  "Lists records with before-load and after-load hooks."
  [entity & [opts]]
  (let [config (config/get-entity-config entity)
        hooks (:hooks config)
        before-load (:before-load hooks)
        after-load (:after-load hooks)]
    (execute-with-hooks entity
                        (merge {:query-key :list
                                :before-hook before-load
                                :after-hook after-load}
                               opts))))

(defn count-records
  "Counts total records for an entity, with optional search filter.
   Uses the entity's :table to generate COUNT queries.
   Returns the count as a long."
  [entity & [opts]]
  (let [cfg (config/get-entity-config entity)
        table (:table cfg)
        search (:search opts)
        search-fields (or (:search-fields opts)
                          (config/get-search-fields entity))
        conn (:connection cfg)]
    (if table
      (let [[sql params] (if (and search (not (str/blank? search)) (seq search-fields))
                           (let [likes (map #(str (name %) " LIKE ?") search-fields)]
                             [(str "SELECT COUNT(*) FROM " table " WHERE " (str/join " OR " likes))
                              (repeat (count search-fields) (str "%" search "%"))])
                           [(str "SELECT COUNT(*) FROM " table) []])]
        (-> (first (Query (into [sql] params) :conn conn))
            first
            val
            (or 0)))
      ;; Fallback: use list query and count in Clojure
      (count (list-records entity opts))))

(defn list-records-paginated
  "Lists records for an entity with pagination, search, and sort.
   Uses the entity's :table to build SQL directly (ignores :list query)
   for maximum compatibility with server-rendered tables.

   Options:
   - :page         Page number (1-based, default 1)
   - :per-page     Records per page (default 0 = no pagination)
   - :search       Search string (applied as LIKE on searchable fields)
   - :sort-by      Column keyword to sort by (default :id)
   - :sort-order   :asc or :desc (default :desc)
   - :search-fields  Custom list of field keywords to search

   Returns a map with :records, :total, :page, :per-page."
  [entity & [opts]]
  (let [cfg (config/get-entity-config entity)
        table (:table cfg)
        page (max 1 (or (:page opts) 1))
        per-page (or (:per-page opts) 0)
        search (:search opts)
        sort-by (or (:sort-by opts) :id)
        sort-order (or (:sort-order opts) :desc)
        search-fields (or (:search-fields opts)
                          (config/get-search-fields entity))
        conn (:connection cfg)]
    (if (and table (pos? per-page))
      (let [offset (* (dec page) per-page)
            ;; Build WHERE for search
            [where-sql where-params] (if (and search (not (str/blank? search)) (seq search-fields))
                                       (let [likes (map #(str (name %) " LIKE ?") search-fields)]
                                         [(str " WHERE " (str/join " OR " likes))
                                          (repeat (count search-fields) (str "%" search "%"))])
                                       ["" []])
            ;; Build ORDER BY
            order-str (str " ORDER BY " (name sort-by) " " (name sort-order))
            ;; Build LIMIT/OFFSET
            limit-str " LIMIT ? OFFSET ?"
            ;; Full SQL
            sql (str "SELECT * FROM " table where-sql order-str limit-str)
            params (concat where-params [per-page offset])
            records (Query (into [sql] params) :conn conn)
            ;; Count total (with same search filter)
            [count-sql count-params] (if (and search (not (str/blank? search)) (seq search-fields))
                                       (let [likes (map #(str (name %) " LIKE ?") search-fields)]
                                         [(str "SELECT COUNT(*) FROM " table " WHERE " (str/join " OR " likes))
                                          (repeat (count search-fields) (str "%" search "%"))])
                                       [(str "SELECT COUNT(*) FROM " table) []])
            total (-> (first (Query (into [count-sql] count-params) :conn conn))
                      first
                      val
                      (or 0))]
         {:records records
          :total total
          :page page
          :per-page per-page
          :total-pages (long (Math/ceil (/ (max 1 total) per-page)))})
      ;; No pagination requested — fall through to normal list
      (let [records (list-with-hooks entity opts)]
        {:records records
         :total (count records)
         :page 1
         :per-page 0})))))

(defn get-with-hooks
  "Gets a record with before-load and after-load hooks.
   Handles both simple and composite keys."
  [entity id & [opts]]
  (let [config (config/get-entity-config entity)
        hooks (:hooks config)
        before-load (:before-load hooks)
        after-load (:after-load hooks)
        params (parse-composite-id id (:primary-key config))
        result (execute-with-hooks entity
                                   (merge {:query-key :get
                                           :params params
                                           :before-hook before-load
                                           :after-hook after-load}
                                          opts))]
    (first result)))

(comment
  ;; Usage examples
  (list-records :users)
  (get-record :users 1)
  (custom-query :users :active-users)
  (list-with-hooks :users)
  (get-with-hooks :users 1)
  (generate-list-query :users)
  (ensure-default-queries :users))
