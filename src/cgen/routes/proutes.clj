(ns cgen.routes.proutes
  (:require
   [compojure.core :refer [defroutes GET]]
   [cgen.handlers.dashboard.controller :as dashboard]
   [cgen.handlers.reports.controller :as reports]))

;; All CRUD routes now handled by parameter-driven engine
;; Add custom non-CRUD routes here if needed

(defroutes proutes
  ;; Dashboard
  (GET "/dashboard" req (dashboard/main req))
  ;; Reports
  (GET "/reports/contactos" req (reports/contactos req))
  (GET "/reports/users" req (reports/users req))
  (GET "/reports/audit-log" req (reports/audit-log req))
  (GET "/reports/organizations" req (reports/organizations req))
  (GET "/reports/departments" req (reports/departments req))
  (GET "/reports/employees" req (reports/employees req))
  (GET "/reports/projects" req (reports/projects req))
  (GET "/reports/skills" req (reports/skills req)))
