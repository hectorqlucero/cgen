(ns cgen.scaffold.enhancement
  "Auto-TabGrid enhancement for existing scaffold system"
  (:require [clojure.edn :as edn]))

(defn should-use-tabgrid
  "Auto-detect if entity should use TabGrid UI"
  [entity-config]
  (and (seq (:subgrids entity-config))
       (:mode entity-config)))

(defn auto-enhance-entity
  "Auto-enhance entity configuration for TabGrid"
  [entity-config]
  (if (should-use-tabgrid entity-config)
    (-> entity-config
        (assoc :tabgrid-ui true :enhanced-tabgrid true)
        (update-in [:ui] merge {:split-view true :breadcrumbs true}))
    entity-config))

(defn load-entity-config
  "Load entity configuration"
  [entity-keyword]
  (try
    (edn/read-string (slurp (str "resources/entities/" (name entity-keyword) ".edn")))
    (catch Exception e
      (println (str "Error loading " (name entity-keyword) ":" (.getMessage e)))
      {})))
