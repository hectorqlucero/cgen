(ns cgen.handlers.home.controller
  (:require
   [buddy.hashers :as hashers]
   [clojure.string :as st]
   [cgen.config.loader :as cfg]
   [cgen.i18n.core :as i18n]
   [cgen.handlers.home.model :refer [get-user get-users update-password]]
   [cgen.handlers.home.view :refer [change-password-view home-view main-view
                                    temp-password-view]]
   [cgen.layout :refer [application]]
   [cgen.models.email :as email]
   [cgen.models.util :refer [get-session-id user-auth]]
   [ring.util.response :refer [redirect]]))

(defn- generate-temp-password []
  (let [chars "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+"
        rnd (java.security.SecureRandom.)]
    (apply str (repeatedly 12 #(nth chars (.nextInt rnd (count chars)))))))

(defn- valid-email-config? []
  (let [cfg-map (cfg/get-app-config)
        host (:email-host cfg-map)
        user (:email-user cfg-map)
        passwd (:email-passwd cfg-map)]
    (and (not (st/blank? host)) (not= host "change_me")
         (not (st/blank? user)) (not= user "change_me")
         (not (st/blank? passwd)) (not= passwd "change_me"))))

(defn- temp-password-email-body [to temp-pass]
  {:from (or (:email-user (cfg/get-app-config)) "no-reply@example.com")
   :to to
   :subject (i18n/tr :temp-password/created)
   :body [{:type "text/plain"
           :content (str (i18n/tr :success/temp-password) "\n\n"
                         (i18n/tr :temp-password/title) ": " temp-pass "\n\n"
                         (i18n/tr :temp-password/copy-warning))}]})

(defn- send-temp-password-email [email-address temp-pass]
  (try
    (let [body (temp-password-email-body email-address temp-pass)
          result (email/send-email body)]
      (if (= 0 (:code result))
        {:ok true}
        {:ok false :error (str "SMTP send failed: " (pr-str result))}))
    (catch Exception e
      {:ok false :error (.getMessage e)})))

(defn main
  [request]
  (let [title (i18n/tr :layout/home)
        ok (get-session-id request)
        js nil
        content (if (> ok 0)
                  (home-view)
                  [:h2.text-info.text-center (i18n/tr :auth/welcome)])]
    (application request title ok js content)))

(defn login
  [request]
  (let [title (i18n/tr :auth/login)
        ok (get-session-id request)
        js nil
        content (main-view title)]
    (application request title ok js content)))

(defn login-user
  [{:keys [params session]}]
  (let [title (i18n/tr :auth/login)
        username (:username params)
        password (:password params)
        row (first (get-user username))
        active (:active row)
        return-path "/"
        back-msg (i18n/tr :common/back)
        error-general (i18n/tr :error/general)
        content-error-general [:p error-general [:a {:href return-path} back-msg]]
        error-forbidden (i18n/tr :auth/invalid-credentials)
        content-error-forbidden [:p error-forbidden [:a {:href return-path} back-msg]]]
    (if (= active "T")
      (if (hashers/check password (:password row))
        (-> (redirect "/")
            (assoc :session (assoc session :user_id (:id row))))
        (application params title 0 nil content-error-general))
      (application params title 0 nil content-error-forbidden))))

(defn change-password
  [request]
  (let [title (i18n/tr :auth/change-password)
        ok (get-session-id request)
        js nil
        content (change-password-view request title)]
    (application request title ok js content)))

(defn temp-password
  [request]
  (if (user-auth request ["A" "S"])
    (let [title (i18n/tr :temp-password/title)
          ok (get-session-id request)
          users (get-users)
          content (temp-password-view users nil nil nil)]
      (application request title ok nil content))
    (redirect "/")))

(defn process-temp-password
  [{:keys [params] :as request}]
  (if (user-auth request ["A" "S"])
    (let [title (i18n/tr :temp-password/title)
          ok (get-session-id request)
          username (:username params)
          users (get-users)
          row (when username (first (get-user username)))]
      (if (and row (= (:active row) "T"))
        (let [temp-pass (generate-temp-password)
              result (update-password username (hashers/derive temp-pass))]
          (if (> result 0)
            (let [email-address (:email row)
                  email-message (cond
                                  (not (valid-email-config?))
                                  (i18n/tr :error/email-not-configured)

                                  (st/blank? email-address)
                                  (i18n/tr :error/no-email-address)

                                  :else
                                  (let [send-result (send-temp-password-email email-address temp-pass)]
                                    (if (:ok send-result)
                                      (i18n/tr :temp-password/email-sent {:email email-address})
                                      (i18n/tr :temp-password/email-failed {:error (:error send-result)}))))]
              (application request title ok nil
                           (temp-password-view users username
                                               (str (i18n/tr :success/temp-password) ". " email-message)
                                               temp-pass)))
            (application request title ok nil
                         (temp-password-view users username (i18n/tr :error/password-update) nil))))
        (application request title ok nil
                     (temp-password-view users username (i18n/tr :error/user-not-found) nil))))
    (redirect "/")))

(defn process-password
  [{:keys [params session] :as request}]
  (let [title (i18n/tr :auth/login)
        user-id (:user_id session)
        username (:email params)
        password (:password params)
        row (first (get-user username))
        return-path "/home/login"
        back-msg (i18n/tr :common/back)
        error-general (i18n/tr :error/general)
        content-error-general [:p error-general [:a {:href return-path} back-msg]]]
    (if (nil? user-id)
      (redirect "/home/login")
      (if (and row (= (:active row) "T") (= (:id row) user-id))
        (let [confirm-password (:confirm-password params)]
          (cond
            (or (st/blank? password) (st/blank? confirm-password))
            (application request title (get-session-id request) nil
                         [:p (i18n/tr :validation/password-required)])

            (not= password confirm-password)
            (application request title (get-session-id request) nil
                         [:p (i18n/tr :validation/password-confirmation)])

            :else
            (let [result (or (update-password username (hashers/derive password)) 0)]
              (if (> result 0)
                (-> (redirect "/home/login")
                    (assoc :session {}))
                (application request title (get-session-id request) nil content-error-general)))))
        (application request title (get-session-id request) nil content-error-general)))))

(defn logoff-user
  [_]
  (-> (redirect "/")
      (assoc :session {})))

(comment
  (:username (first (get-users))))
