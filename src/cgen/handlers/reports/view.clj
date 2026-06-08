(ns cgen.handlers.reports.view
  (:require
   [cgen.models.grid :refer [build-report]]))

(def ^:private contactos-fields
  (array-map
   :name "Nombre"
   :phone "Telefono"
   :email "Email"
   :siblings "Hermanos"
   :cars "Carros"))

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

(defn contactos
  [request title rows]
  (build-report request title rows "contactos-report" contactos-fields))

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

(def ^:private organizations-fields
  (array-map
   :name "Nombre"
   :code "Código"
   :active "Activo"))

(def ^:private departments-fields
  (array-map
   :organization_name "Organización"
   :name "Nombre"
   :code "Código"))

(def ^:private employees-fields
  (array-map
   :first_name "Nombre"
   :last_name "Apellido"
   :email "Email"
   :department_name "Departamento"
   :organization_name "Organización"
   :manager_name "Supervisor"
   :active "Activo"
   :emergency_phone "Tel. Emergencia"))

(def ^:private projects-fields
  (array-map
   :project_code "Código"
   :name "Nombre"
   :starts_on "Inicio"
   :ends_on "Fin"))

(def ^:private skills-fields
  (array-map
   :name "Nombre"
   :category "Categoría"))

(defn audit-log
  [request title rows]
  (build-report request title rows "audit-log-report" audit-log-fields))

(defn organizations
  [request title rows]
  (build-report request title rows "organizations-report" organizations-fields))

(defn departments
  [request title rows]
  (build-report request title rows "departments-report" departments-fields))

(defn employees
  [request title rows]
  (build-report request title rows "employees-report" employees-fields))

(defn projects
  [request title rows]
  (build-report request title rows "projects-report" projects-fields))

(defn skills
  [request title rows]
  (build-report request title rows "skills-report" skills-fields))
