(ns cgen.handlers.reports.model
  (:require
   [cgen.models.crud :refer [Query]]))

(def ^:private users-sql
  "
  select * from users_view
  ")

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

(defn get-audit-log
  []
  (Query audit-log-sql))
