(ns cgen.models.db.mysql
  (:require [clojure.java.jdbc :as j]))

(defn q-opts [] {:entities (j/quoted \`)})

(defn table-sql-name [tname]
  (str "`" tname "`"))

(defn time-projection [field]
  (str "TIME_FORMAT(" field ", '%H:%i') AS " field))

(defn describe-table [table query-fn]
  (query-fn (str "DESCRIBE " table)))

(defn primary-keys-fallback [_table _query-fn]
  [])

(defn last-insert-id [t-con]
  (some-> (j/query t-con ["SELECT last_insert_id() AS id"]) first :id))

