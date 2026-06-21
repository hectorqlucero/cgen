(ns cgen.models.db.sqlite
  (:require [clojure.java.jdbc :as j]))

(defn q-opts [] {})

(defn table-sql-name [tname]
  tname)

(defn time-projection [field]
  (str "strftime('%H:%M', " field ") AS " field))

(defn describe-table [table query-fn]
  (let [rows (query-fn (str "PRAGMA table_info(" table ")"))]
    (map (fn [r]
           {:field (:name r)
            :type  (:type r)
            :null  (if (= 1 (:notnull r)) "NO" "YES")
            :default (:dflt_value r)
            :extra ""
            :privileges ""
            :comment ""
            :key (when (= 1 (:pk r)) "PRI")})
         rows)))

(defn primary-keys-fallback [_table _query-fn]
  [])

(defn last-insert-id [t-con]
  (some-> (j/query t-con ["SELECT last_insert_rowid() AS id"]) first :id))

