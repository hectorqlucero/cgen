(ns cgen.hooks.prestamos
  (:require [clj-time.core :as t]
            [clj-time.format :as f]))

(defn- parse-date [s]
  (when s
    (try
      (t/to-date-time (f/parse (f/formatters :date) s))
      (catch Exception _ nil))))

(defn- days-between [start end]
  (when (and start end)
    (t/in-days (t/interval start end))))

(defn before-save
  "Auto-calculate vencimiento (14 days from prestamo) if not provided.
   If devuelto is set, auto-calculate days overdue."
  [params]
  (let [fecha-prestamo (or (:fecha_prestamo params) (str (t/now)))
        params (if (and (:fecha_prestamo params) (not (:fecha_vencimiento params)))
                 (assoc params :fecha_vencimiento
                        (f/unparse (f/formatters :date)
                                   (t/plus (parse-date fecha-prestamo) (t/days 14))))
                 params)]
    (when (and (:fecha_devolucion params) (= (:status params) "devuelto"))
      (let [vencimiento (parse-date (:fecha_vencimiento params))
            devolucion (parse-date (:fecha_devolucion params))]
        (when (and vencimiento devolucion (t/after? devolucion vencimiento))
          (println (str "[HOOK] Préstamo vencido por " (days-between vencimiento devolucion) " días.")))))
    params))

(defn before-load [params] params)

(defn after-load [rows _params] rows)

(defn after-save [_entity-id _params]
  {:success true})

(defn before-delete [_entity-id]
  {:success true})

(defn after-delete [_entity-id]
  {:success true})
