(ns cgen.handlers.reports.view
  (:require
   [cgen.models.grid :refer [build-report]]
   [clojure.string :as str]
   [clojure.edn :as edn]))

(def ^:private users-fields
  (array-map
   :username "Usuario"
   :firstname "Nombre"
   :lastname "Apellido"
   :dob_formatted "Fecha de Nacimiento"
   :phone "Telefono"
   :cell "Celular"
   :fax "Fax"
   :level_formatted "Nivel"
   :active_formatted "Status"))

(defn users
  [request title rows]
  (build-report request title rows "users-report" users-fields))

;; ---------------------------------------------------------------------------
;; Audit log row formatting
;; ---------------------------------------------------------------------------

(def ^:private audit-log-fields
  (array-map
   :entity "Entidad"
   :operation "Operación"
   :user_name "Modificado por"
   :data_text "Datos"
   :timestamp "Fecha/Hora"))

(def ^:private internal-keys
  #{:id :return_url :active_tab :edited_id :edited
    :__anti-forgery-token :anti-forgery-token
    :created_by :created_at :modified_by :modified_at
    :entity :file :file-column})

(defn- is-file-upload?
  "True if v is a Ring file-upload map (has :tempfile, :filename, etc.)."
  [v]
  (and (map? v)
       (contains? v :tempfile)
       (contains? v :filename)
       (contains? v :size)))

(defn- skip-value?
  "Returns true for values that should not appear in the display."
  [v]
  (or (nil? v)
      (and (string? v) (str/blank? v))
      (and (number? v) (zero? v))
      (instance? java.io.File v)
      (is-file-upload? v)))

(defn- clean-data-pairs
  "Removes internal keys, empty values, and file blobs from a data map.
   Returns a sequence of [key value] pairs sorted by key name."
  [data]
  (when (map? data)
    (sort-by (comp str/lower-case name first)
             (remove (fn [[k v]]
                       (or (internal-keys k)
                           (skip-value? v)))
                     data))))

(defn- format-label
  "Converts a keyword to a human label: :estado_id -> 'Estado Id'"
  [k]
  (->> (str/split (name k) #"_")
       (map str/capitalize)
       (str/join " ")))

(defn- display-value
  "Formats a single value for display."
  [v]
  (cond
    (string? v) v
    (keyword? v) (name v)
    :else (pr-str v)))

(defn- parse-audit-data
  "Safely reads an audit-log data string.
   Handles #object[...] tags left by pr-str on File objects
   by replacing them with nil before parsing."
  [s]
  (when (and s (not (str/blank? (str s))))
    (try
      (edn/read-string s)
      (catch Exception _
        (try
          (let [cleaned (str/replace s #"#object\[[^\]]*\]" "nil")]
            (edn/read-string cleaned))
          (catch Exception _ nil))))))

;; ---------------------------------------------------------------------------
;; Plain-text formatters (for CSV / PDF export)
;; ---------------------------------------------------------------------------

(defn- format-data-create-text
  [data]
  (let [pairs (clean-data-pairs data)]
    (if (seq pairs)
      (str/join "\n"
                (map (fn [[k v]]
                       (str (format-label k) ": " (display-value v)))
                     pairs))
      "(sin datos)")))

(defn- format-data-update-text
  [new-data old-data]
  (let [pairs (clean-data-pairs new-data)
        old-map (when (map? old-data) old-data)
        changed (remove (fn [[k v]]
                          (let [old-val (get old-map k)]
                            (or (nil? old-val)
                                (and (instance? java.io.File old-val) true)
                                (= (str old-val) (str v)))))
                        pairs)]
    (if (seq changed)
      (str/join "\n"
                (map (fn [[k v]]
                       (let [label (format-label k)
                             old-val (get old-map k)
                             old-str (if (skip-value? old-val) "(vacio)" (display-value old-val))]
                         (str label ": " old-str " \u2192 " (display-value v))))
                     changed))
      (format-data-create-text new-data))))

(defn- format-data-text
  [raw-data]
  (if-let [data (parse-audit-data raw-data)]
    (if (and (map? data) (contains? data :new))
      (format-data-update-text (:new data) (:old data))
      (format-data-create-text data))
    (or raw-data "")))

;; ---------------------------------------------------------------------------
;; Hiccup HTML formatters (for web / print view)
;; ---------------------------------------------------------------------------

(defn- format-data-create-html
  [data]
  (let [pairs (clean-data-pairs data)]
    (if (seq pairs)
      (into [:div {:style "line-height:1.7"}]
            (map (fn [[k v]]
                   [:div
                    [:span.fw-semibold.me-1 (format-label k) ":"]
                    (display-value v)])
                 pairs))
      [:span.text-muted "(sin datos)"])))

(defn- format-data-update-html
  [new-data old-data]
  (let [pairs (clean-data-pairs new-data)
        old-map (when (map? old-data) old-data)
        changed (remove (fn [[k v]]
                          (let [old-val (get old-map k)]
                            (or (nil? old-val)
                                (and (instance? java.io.File old-val) true)
                                (= (str old-val) (str v)))))
                        pairs)]
    (if (seq changed)
      (into [:div {:style "line-height:1.7"}]
            (map (fn [[k v]]
                   (let [old-val (get old-map k)
                         old-str (if (skip-value? old-val) "(vacio)" (display-value old-val))]
                     [:div.mb-1
                      [:span.fw-semibold.me-1 (format-label k) ":"]
                      [:span.text-decoration-line-through.text-muted.me-1 old-str]
                      [:span.text-success "\u2192 " (display-value v)]]))
                 changed))
      (format-data-create-html new-data))))

(defn- format-data-html
  [raw-data]
  (if-let [data (parse-audit-data raw-data)]
    (if (and (map? data) (contains? data :new))
      (format-data-update-html (:new data) (:old data))
      (format-data-create-html data))
    [:span.text-muted (or raw-data "")]))

;; ---------------------------------------------------------------------------
;; Cell renderers
;; ---------------------------------------------------------------------------

(defn- data-cell-fn
  [row]
  (format-data-html (:data row)))

;; ---------------------------------------------------------------------------
;; Row/column formatters
;; ---------------------------------------------------------------------------

(defn- format-timestamp
  [ts]
  (when (and ts (not (str/blank? (str ts))))
    (try
      (let [instant (java.time.Instant/parse ts)
            zdt (java.time.ZonedDateTime/ofInstant instant (java.time.ZoneId/systemDefault))
            formatter (java.time.format.DateTimeFormatter/ofPattern "MMM dd, yyyy hh:mm a")]
        (.format zdt formatter))
      (catch Exception _ (str ts)))))

(defn- format-operation
  [op]
  (case op
    "create"  "Crear"
    "update"  "Actualizar"
    "delete"  "Eliminar"
    (str/capitalize (name op))))

(defn- format-entity
  [e]
  (when e
    (->> (str/split (name e) #"_")
         (map str/capitalize)
         (str/join " "))))

(defn- format-audit-row
  [row]
  (let [raw-data (:data row)]
    (-> row
        (assoc :data raw-data)
        (assoc :data_text (format-data-text raw-data))
        (assoc :operation (format-operation (:operation row)))
        (assoc :timestamp (format-timestamp (:timestamp row)))
        (assoc :entity (format-entity (:entity row))))))

(defn audit-log
  [request title rows]
  (let [rows (mapv format-audit-row rows)]
    (build-report request title rows "audit-log-report" audit-log-fields
                  {:cell-fn {:data_text data-cell-fn}})))
