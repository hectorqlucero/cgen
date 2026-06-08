(ns cgen.i18n.core
  "Internationalization (i18n) system for the application.
   Supports multiple languages with easy translation management.

   API (preferred):
     (tr :common/save)                 => \"Guardar\"
     (tr :common/save {:count 5})      => \"Guardar (5)\"
     (t :common/save :en)              => \"Save\"

   API (backward-compat, deprecated):
     (tr request :common/save)         => \"Guardar\"
     (tr request :common/save {..})    => \"Guardar (5)\"

   The *locale* dynamic var is bound per-request by wrap-locale middleware.
   When a key is missing from the requested locale, falls back to default-locale."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]))

(def default-locale :es)
(def ^:dynamic *locale* nil)

(def supported-locales
  "Map of supported locales with their display names"
  {:es {:name "Español" :flag "🇲🇽"}
   :en {:name "English" :flag "🇺🇸"}})

(def translations-cache
  "Atom holding all loaded translations by locale"
  (atom {}))

(defn load-translations
  "Loads translations for a specific locale from EDN file"
  [locale]
  (let [filename (str "i18n/" (name locale) ".edn")
        resource (io/resource filename)]
    (if resource
      (try
        (edn/read-string (slurp resource))
        (catch Exception e
          (println "[ERROR] Failed to load translations for" locale ":" (.getMessage e))
          {}))
      (do
        (println "[WARN] Translation file not found:" filename)
        {}))))

(defn ensure-translations-loaded
  "Ensures translations for a locale are loaded in cache"
  [locale]
  (when-not (get @translations-cache locale)
    (swap! translations-cache assoc locale (load-translations locale))))

(defn reload-translations!
  "Reloads all translations from disk"
  []
  (reset! translations-cache {})
  (doseq [locale (keys supported-locales)]
    (ensure-translations-loaded locale)))

(defn t
  "Translates a key to the given locale with optional param interpolation.

   Falls back to default-locale when the key is missing from the requested locale.
   As a last resort returns the key name."
  ([key]
   (t key default-locale))
  ([key locale]
   (t key locale {}))
  ([key locale params]
   (ensure-translations-loaded locale)
   (let [translations (get @translations-cache locale {})
         translation (get translations key)]
     (if (string? translation)
       (reduce (fn [result [k v]]
                 (clojure.string/replace result
                                         (str "{" (name k) "}")
                                         (str v)))
               translation
               params)
       (let [default-translation (do
                                   (ensure-translations-loaded default-locale)
                                   (get (get @translations-cache default-locale {}) key))]
         (if (string? default-translation)
           (do
             (println "[I18N] Missing" key "for" (name locale)
                      "- falling back to" (name default-locale))
             (reduce (fn [result [k v]]
                       (clojure.string/replace result
                                               (str "{" (name k) "}")
                                               (str v)))
                     default-translation
                     params))
           (name key)))))))

(defn translate
  "Alias for t function"
  [& args]
  (apply t args))

(defn get-locale-from-session
  "Gets the current locale from session, defaults to default-locale"
  [session]
  (or (:locale session) default-locale))

(defn set-locale!
  "Sets the locale in session"
  [session locale]
  (if (contains? supported-locales locale)
    (assoc session :locale locale)
    session))

(defn get-locale-name
  "Gets the display name for a locale"
  [locale]
  (get-in supported-locales [locale :name] (name locale)))

(defn get-locale-flag
  "Gets the flag emoji for a locale"
  [locale]
  (get-in supported-locales [locale :flag] ""))

(defn tr
  "Translate using the current request context.

   New API (preferred):
     (tr :common/save)                  => one arg — key only
     (tr :common/save {:field \"Email\"}) => two args — key + params

   Deprecated backward-compat API:
     (tr request :common/save)           => two args — request + key
     (tr request :common/save {..})      => three args — request + key + params

   Locale is resolved from *locale* (bound by wrap-locale middleware)
   or falls back to default-locale."
  ([key]
   (let [locale (or *locale* default-locale)]
     (t key locale)))
  ([key params]
   (if (map? key)
     ;; backward compat: (tr request :key)
     (let [locale (get-locale-from-session (:session key))]
       (t params locale))
     ;; new API: (tr :key params)
     (let [locale (or *locale* default-locale)]
       (t key locale params))))
  ([request key params]
   ;; backward compat: (tr request :key params)
   (let [locale (get-locale-from-session (:session request))]
     (t key locale params))))

(defn wrap-locale
  "Ring middleware that binds *locale* from the session for each request.
   Apply this early in the middleware stack so downstream code can use
   (tr :key) without threading the request object."
  [handler]
  (fn [request]
    (let [locale (get-locale-from-session (:session request))]
      (binding [*locale* locale]
        (handler request)))))

(defn init!
  "Initializes the i18n system by loading all translations"
  []
  (reload-translations!))

;; Auto-initialize on namespace load
(init!)
