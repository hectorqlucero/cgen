(ns contactos.routes.tabgrid
  "Routes for TabGrid AJAX handlers"
  (:require
   [compojure.core :refer [GET POST defroutes]]
   [contactos.tabgrid.handlers :as handlers]))

(defroutes tabgrid-routes
  ;; Load subgrid data
  (GET "/tabgrid/load-subgrid" request
    (handlers/handle-load-subgrid request))

  ;; Get parent record
  (GET "/tabgrid/get-parent" request
    (handlers/handle-get-parent request))

  ;; Many-to-many association management
  (POST "/tabgrid/dissociate" request
    (handlers/handle-dissociate request))

  (GET "/tabgrid/pivot-form" request
    (handlers/handle-pivot-form request))

  ;; Fresh M2M pane HTML fragment (used for in-place refresh without page reload)
  (GET "/tabgrid/m2m-pane" request
    (handlers/handle-m2m-pane request))

  (POST "/tabgrid/save-pivot" request
    (handlers/handle-save-pivot request))

  ;; Full-page link form (replaces the old modal)
  (GET "/tabgrid/link-form" request
    (handlers/handle-link-form request))

  (POST "/tabgrid/link-save" request
    (handlers/handle-link-save request)))
