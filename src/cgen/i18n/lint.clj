(ns cgen.i18n.lint
  "i18n lint tool: scans source code for tr/t translation key references,
   validates them against translation files, and reports missing keys.
   Usage: lein i18n-lint"
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn- find-files [dir]
  (->> (io/file dir)
       (file-seq)
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".clj"))
       (remove #(str/ends-with? (.getPath %) "i18n/lint.clj"))))

(defn- extract-keys-from-file [file]
  (let [content (slurp file)
        ;; Find all (tr :kw/name) calls
        tr-keys (re-seq #"\(tr\s+:([a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)+)" content)
        t-keys (re-seq #"\(t\s+:([a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)+)" content)
        ;; Also find (i18n/tr :kw/name) calls
        i18n-tr-keys (re-seq #"i18n/tr\s+:([a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)+)" content)
        i18n-t-keys (re-seq #"i18n/t\s+:([a-zA-Z0-9_-]+(?:/[a-zA-Z0-9_-]+)+)" content)]
    (mapv second (concat tr-keys t-keys i18n-tr-keys i18n-t-keys))))

(defn- load-translation-keys [locale]
  (let [path (str "i18n/" (name locale) ".edn")
        resource (io/resource path)]
    (if resource
      (-> resource slurp edn/read-string keys set)
      (println "[WARN] Translation file not found:" path))))

(defn lint
  "Lint i18n usage in source code against translation files.
   Scans src/ for tr/t calls and validates keys exist in both en.edn and es.edn.
   Returns a map with :missing-in-source, :missing-in-en, :missing-in-es, :extra-keys."
  [& [src-dir]]
  (let [src-dir (or src-dir "src")
        source-keys (->> (find-files src-dir)
                         (mapcat extract-keys-from-file)
                         (map keyword)
                         (remove nil?)
                         (into #{})
                         (sort))
        en-keys (load-translation-keys :en)
        es-keys (load-translation-keys :es)
        all-translated (into #{} (concat en-keys es-keys))
        missing-in-en (remove en-keys source-keys)
        missing-in-es (remove es-keys source-keys)
        ;; Keys in translation files but not referenced in source
        extra-in-en (remove (set source-keys) en-keys)
        extra-in-es (remove (set source-keys) es-keys)]
    (println "\n=== i18n Lint Report ===")
    (println (count source-keys) "unique translation keys referenced in source\n")
    (when (seq missing-in-en)
      (println "⚠  Missing in en.edn:")
      (doseq [k missing-in-en] (println "  -" k)))
    (when (seq missing-in-es)
      (println "⚠  Missing in es.edn:")
      (doseq [k missing-in-es] (println "  -" k)))
    (when (seq extra-in-en)
      (println "\nℹ  Keys in en.edn not referenced in source (possibly dead):")
      (doseq [k extra-in-en] (println "  -" k)))
    (when (seq extra-in-es)
      (println "\nℹ  Keys in es.edn not referenced in source (possibly dead):")
      (doseq [k extra-in-es] (println "  -" k)))
    (when (and (empty? missing-in-en) (empty? missing-in-es))
      (println "✅ All source keys are present in both translation files."))
    {:source-keys source-keys
     :missing-in-en missing-in-en
     :missing-in-es missing-in-es
     :extra-in-en extra-in-en
     :extra-in-es extra-in-es}))

(defn -main
  "Entry point for lein run"
  [& args]
  (lint (first args)))
