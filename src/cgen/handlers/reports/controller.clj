(ns cgen.handlers.reports.controller
  (:require
   [cgen.handlers.reports.model :as model]
   [cgen.handlers.reports.view :as view]
   [cgen.layout :refer [application]]
   [cgen.models.util :refer [get-session-id]]))

(defn users
  [request]
  (let [title "Reporte de usuarios"
        ok (get-session-id request)
        js nil
        rows (model/get-users)
        content (view/users request title rows)]
    (application request title ok js content)))

(defn audit-log
  [request]
  (let [title "Reporte de auditoría"
        ok (get-session-id request)
        js nil
        rows (model/get-audit-log)
        content (view/audit-log request title rows)]
    (application request title ok js content)))
