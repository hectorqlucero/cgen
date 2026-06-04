(ns cgen.handlers.dashboard.controller
  (:require
   [cgen.handlers.dashboard.model :as model]
   [cgen.handlers.dashboard.view :as view]
   [cgen.layout :refer [application]]
   [cgen.models.util :refer [get-session-id]]))

(defn main
  [request]
  (let [title "DASHBOARD"
        ok (get-session-id request)
        js nil
        stats (model/get-stats)
        content (view/main title stats)]
    (application request title ok js content)))
