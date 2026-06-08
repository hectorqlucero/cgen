(ns cgen.handlers.home.view
  (:require
   [cgen.i18n.core :as i18n]
   [cgen.models.form :refer [login-form password-form]]
   [cgen.models.util :refer [user-email user-level]]
   [cgen.web.csrf :refer [csrf-field]]))

(defn home-view
  []
  (list
   [:div.container.mt-5
    [:div.text-center
     [:h1.text-info (:site-name cgen.models.crud/config)]
     [:p.text-muted (i18n/tr :auth/welcome)]]]))

(defn main-view
  "This creates the login form and we are passing the title from the controller"
  [title]
  (let [href "/home/login"]
    (login-form title href)))

(defn temp-password-view
  [users selected-username message temp-password]
  [:div.container.mt-5
   [:div.row.justify-content-center
    [:div.col-lg-8
     [:div.card.shadow
      [:div.card-header.bg-primary.text-white
       [:h4.mb-0 (i18n/tr :temp-password/title)]]
      [:div.card-body
       (when message
         [:div.alert.alert-info message])
       [:form {:method "POST" :action "/home/temp-password"}
        (csrf-field)
        [:div.mb-3
         [:label.form-label.fw-semibold {:for "username"}
          (i18n/tr :temp-password/select-user)]
         [:select.form-select {:id "username" :name "username" :required true}
          [:option {:value ""} (i18n/tr :temp-password/placeholder-user)]
          (for [user users]
            [:option {:value (:username user)
                      :selected (= (:username user) selected-username)}
             (str (:username user)
                  (when-let [email (:email user)]
                    (str " (" email ")")))])]]
        [:div.d-flex.gap-2.justify-content-end.mt-4
         [:button.btn.btn-success {:type "submit"}
          (i18n/tr :temp-password/title)]]]
       (when temp-password
         [:div.mt-4
          [:div.alert.alert-success
           [:h5.mb-0 (i18n/tr :temp-password/created)]]
          [:p.mb-1 (i18n/tr :temp-password/copy-warning)]
          [:pre.p-3.bg-light.rounded [:code temp-password]]])]]]]])

(defn change-password-view
  [request title]
  (let [level (user-level request)
        email (user-email request)
        email-readonly? (not (some #(= level %) #{"A" "S"}))]
    (password-form title :user-email email :email-readonly? email-readonly?)))
