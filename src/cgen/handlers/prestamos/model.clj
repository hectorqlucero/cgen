(ns cgen.handlers.prestamos.model
  (:require [cgen.models.crud :refer [Query Insert Update]]
            [clj-time.core :as t]
            [clj-time.format :as f]))

;; -----------------------------
;; helpers
;; -----------------------------

(defn- extract-insert-id [res]
  (cond
    (number? res) res

    (map? res)
    (or (:id res)
        (:generated_key res)
        (:generated-key res)
        ((keyword "last_insert_rowid()") res))

    (sequential? res)
    (let [m (first res)]
      (when (map? m)
        (or (:id m)
            (:generated_key m)
            (:generated-key m)
            ((keyword "last_insert_rowid()") m))))

    :else nil))

(defn- parse-libro-id [lk]
  (try
    (let [s (name lk)]
      (when-let [[_ id] (re-find #"libro_(\d+)" s)]
        (Long/parseLong id)))
    (catch Exception _ nil)))

;; -----------------------------
;; queries
;; -----------------------------

(defn get-libros-disponibles []
  (Query "SELECT id, titulo, isbn
           FROM libros
           WHERE status = 'disponible'
           ORDER BY titulo"))

(defn get-prestamos-activos []
  (Query "SELECT p.id,
                  p.fecha_prestamo,
                  p.fecha_vencimiento,
                  p.status,
                  m.nombre as miembro_nombre
           FROM prestamos p
           JOIN miembros m ON p.miembro_id = m.id
           WHERE p.status IN ('activo', 'vencido')
           ORDER BY p.fecha_prestamo DESC"))

(defn get-miembros []
  (Query "SELECT id, nombre, email
           FROM miembros
           WHERE activo = 'T'
           ORDER BY nombre"))

;; -----------------------------
;; CREATE PRESTAMO (FIXED)
;; -----------------------------

(defn crear-prestamo!
  [miembro-id libro-ids fecha-prestamo]
  (try
    (let [fecha (or fecha-prestamo
                    (f/unparse (f/formatters :date) (t/now)))

          vencimiento (f/unparse (f/formatters :date)
                                 (t/plus (t/now) (t/days 14)))

          miembro (try (Long/parseLong (str miembro-id))
                       (catch Exception _ nil))

          _ (when-not miembro
              (throw (ex-info "miembro-id inválido" {:miembro-id miembro-id})))

          insert-res (Insert :prestamos
                             {:miembro_id miembro
                              :fecha_prestamo fecha
                              :fecha_vencimiento vencimiento
                              :status "activo"})

          prestamo-id (extract-insert-id insert-res)]

      (when-not prestamo-id
        (throw (ex-info "No se pudo obtener prestamo-id"
                        {:insert-res insert-res})))

      ;; insert details safely
      (doseq [lk libro-ids]
        (let [libro-id (parse-libro-id lk)]

          (when (and libro-id (pos? libro-id))
            (Insert :prestamos_detalle
                    {:prestamo_id prestamo-id
                     :libro_id libro-id
                     :cantidad 1})

            (Update :libros
                    {:status "prestado"}
                    ["id = ?" libro-id]))))

      {:success true
       :prestamo-id prestamo-id})

    (catch Exception e
      (println "[ERROR] crear-prestamo!:" (.getMessage e))
      {:success false
       :error (.getMessage e)})))

;; -----------------------------
;; DEVOLVER
;; -----------------------------

(defn devolver-prestamo!
  [prestamo-id]
  (try
    (let [hoy (f/unparse (f/formatters :date) (t/now))
          detalles (Query ["SELECT libro_id
                            FROM prestamos_detalle
                            WHERE prestamo_id = ?"
                           prestamo-id])]

      (Update :prestamos
              {:fecha_devolucion hoy
               :status "devuelto"}
              ["id = ?" prestamo-id])

      (doseq [d detalles]
        (Update :libros
                {:status "disponible"}
                ["id = ?" (:libro_id d)]))

      {:success true})

    (catch Exception e
      (println "[ERROR] devolver-prestamo!:" (.getMessage e))
      {:success false
       :error (.getMessage e)})))

;; -----------------------------
;; RENOVAR
;; -----------------------------

(defn renovar-prestamo!
  [prestamo-id]
  (try
    (let [row (first
               (Query ["SELECT fecha_vencimiento
                        FROM prestamos
                        WHERE id = ?"
                       prestamo-id]))

          nueva-fecha
          (when-let [fecha-str (:fecha_vencimiento row)]
            (let [fecha (f/parse (f/formatters :date) fecha-str)]
              (f/unparse (f/formatters :date)
                         (t/plus fecha (t/days 14)))))]

      (when nueva-fecha
        (Update :prestamos
                {:fecha_vencimiento nueva-fecha
                 :status "activo"}
                ["id = ?" prestamo-id]))

      {:success true})

    (catch Exception e
      (println "[ERROR] renovar-prestamo!:" (.getMessage e))
      {:success false
       :error (.getMessage e)})))
