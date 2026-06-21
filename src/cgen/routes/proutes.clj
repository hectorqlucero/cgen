(ns cgen.routes.proutes
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [cgen.handlers.prestamos.controller :as prestamos]
   [cgen.handlers.reportes.controller :as reportes]
   [cgen.handlers.dashboard.controller :as dashboard]
   [cgen.handlers.reports.controller :as reports]))

;; All CRUD routes now handled by parameter-driven engine
;; Add custom non-CRUD routes here if needed

(defroutes proutes
  ;; Dashboard
  (GET "/dashboard" req (dashboard/main req))
  ;; Loan desk
  (GET "/prestamos" req (prestamos/main req))
  (POST "/prestamos/crear" req (prestamos/crear-prestamo req))
  (GET "/prestamos/devolver/:id" [id :as req] (prestamos/devolver-libro req))
  (GET "/prestamos/renovar/:id" [id :as req] (prestamos/renovar-prestamo req))
  ;; Library reports
  (GET "/reportes/libros" req (reportes/libros req))
  (GET "/reportes/miembros" req (reportes/miembros req))
  (GET "/reportes/prestamos" req (reportes/prestamos req))
  ;; System reports
  (GET "/reports/users" req (reports/users req))
  (GET "/reports/audit-log" req (reports/audit-log req)))
