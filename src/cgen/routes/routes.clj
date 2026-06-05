(ns cgen.routes.routes
  (:require
   [compojure.core :refer [defroutes GET POST]]
   [cgen.handlers.home.controller :as home-controller]))

(defroutes open-routes
  (GET "/" params [] (home-controller/main params))
  (GET "/home/login" params [] (home-controller/login params))
  (POST "/home/login" params [] (home-controller/login-user params))
  (GET "/home/logoff" params [] (home-controller/logoff-user params)))

(defroutes password-routes
  (GET "/change/password" params [] (home-controller/change-password params))
  (POST "/change/password" params [] (home-controller/process-password params))
  (GET "/home/temp-password" params [] (home-controller/temp-password params))
  (POST "/home/temp-password" params [] (home-controller/process-temp-password params)))
