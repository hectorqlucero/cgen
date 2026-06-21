(ns cgen.handlers.reports.view
  (:require
   [cgen.models.grid :refer [build-report]]))

(def ^:private users-fields
  (array-map
   :username "Usuario"
   :firstname "Nombre"
   :lastname "Apellido"
   :dob_formatted "Fecha de Nacimiento"
   :phone "Telefono"
   :cell "Celular"
   :fax "Fax"
   :level_formatted "Nivel"
   :active_formatted "Status"))

(defn users
  [request title rows]
  (build-report request title rows "users-report" users-fields))

(def ^:private audit-log-fields
  (array-map
   :entity "Entidad"
   :operation "Operación"
   :data "Datos"
   :user_name "Usuario"
   :timestamp "Fecha/Hora"))

(defn audit-log
  [request title rows]
  (build-report request title rows "audit-log-report" audit-log-fields))
