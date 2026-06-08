(ns cgen.handlers.reports.controller
  (:require
   [cgen.handlers.reports.model :as model]
   [cgen.handlers.reports.view :as view]
   [cgen.layout :refer [application]]
   [cgen.models.util :refer [get-session-id]]))

(defn contactos
  [request]
  (let [title "Reporte de contactos"
        ok (get-session-id request)
        js nil
        rows (model/get-contactos)
        content (view/contactos request title rows)]
    (application request title ok js content)))

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

(defn organizations
  [request]
  (let [title "Reporte de organizaciones"
        ok (get-session-id request)
        js nil
        rows (model/get-organizations)
        content (view/organizations request title rows)]
    (application request title ok js content)))

(defn departments
  [request]
  (let [title "Reporte de departamentos"
        ok (get-session-id request)
        js nil
        rows (model/get-departments)
        content (view/departments request title rows)]
    (application request title ok js content)))

(defn employees
  [request]
  (let [title "Reporte de empleados"
        ok (get-session-id request)
        js nil
        rows (model/get-employees)
        content (view/employees request title rows)]
    (application request title ok js content)))

(defn projects
  [request]
  (let [title "Reporte de proyectos"
        ok (get-session-id request)
        js nil
        rows (model/get-projects)
        content (view/projects request title rows)]
    (application request title ok js content)))

(defn skills
  [request]
  (let [title "Reporte de habilidades"
        ok (get-session-id request)
        js nil
        rows (model/get-skills)
        content (view/skills request title rows)]
    (application request title ok js content)))
