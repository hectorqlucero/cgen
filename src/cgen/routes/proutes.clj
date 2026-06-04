(ns cgen.routes.proutes
  (:require
   [compojure.core :refer [defroutes GET]]
   [cgen.handlers.dashboard.controller :as dashboard]))

;; All CRUD routes now handled by parameter-driven engine
;; Add custom non-CRUD routes here if needed

(defroutes proutes
  ;; Dashboard
  (GET "/dashboard" req (dashboard/main req)))
