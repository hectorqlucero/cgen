(ns cgen.handlers.reports.model
  (:require
   [cgen.models.crud :refer [Query]]))

(def ^:private contactos-sql
  "
  select
  con.*,
  (select group_concat(name, ', ') from siblings where contacto_id = con.id) as siblings,
  (select group_concat(concat(company,' ',model,' ',year), ', ') from cars where contacto_id = con.id) as cars
  from contactos con
  order by con.name
  ")

(def ^:private users-sql
  "
  select * from users_view
  ")

(defn get-contactos
  []
  (Query contactos-sql))

(defn get-users
  []
  (Query users-sql))

(def ^:private audit-log-sql
  "
  select a.*, u.username as user_name
  from audit_log a
  left join users u on a.user_id = u.id
  order by a.timestamp desc
  ")

(def ^:private organizations-sql
  "
  select * from organizations
  order by name
  ")

(def ^:private departments-sql
  "
  select d.*, o.name as organization_name
  from departments d
  join organizations o on d.organization_id = o.id
  order by o.name, d.name
  ")

(def ^:private employees-sql
  "
  select e.*,
    d.name as department_name,
    o.name as organization_name,
    (select (first_name || ' ' || last_name) from employees where id = e.manager_id) as manager_name,
    (select emergency_phone from employee_profiles where employee_id = e.id) as emergency_phone
  from employees e
  join departments d on e.department_id = d.id
  join organizations o on d.organization_id = o.id
  order by o.name, d.name, e.last_name, e.first_name
  ")

(def ^:private projects-sql
  "
  select * from projects
  order by name
  ")

(def ^:private skills-sql
  "
  select * from skills
  order by category, name
  ")

(defn get-audit-log
  []
  (Query audit-log-sql))

(defn get-organizations
  []
  (Query organizations-sql))

(defn get-departments
  []
  (Query departments-sql))

(defn get-employees
  []
  (Query employees-sql))

(defn get-projects
  []
  (Query projects-sql))

(defn get-skills
  []
  (Query skills-sql))
