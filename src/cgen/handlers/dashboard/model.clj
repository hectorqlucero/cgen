(ns cgen.handlers.dashboard.model
  (:require
   [cgen.models.crud :refer [Query]]))

(def ^:private tables-sql
  "
  select name
  from sqlite_schema
  where type = 'table'
  and name <> 'sqlite_sequence'
  ")

(def ^:private tables
  (->> (Query tables-sql)
       (map :name)))

(defn- tt
  [table]
  (let [sql (str "select count(*) as count from " table)
        k (keyword table)
        v (->> (Query sql)
               first
               :count)]
    [k v]))

(defn get-stats
  []
  (into (sorted-map)
        (pmap tt tables)))
