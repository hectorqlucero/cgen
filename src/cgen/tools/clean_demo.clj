(ns cgen.tools.clean-demo
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import [java.io File]))

;; ----------------------------
;; helpers
;; ----------------------------

(defn- pr-name []
  (second (re-find #"\(defproject\s+([^\s]+)" (slurp "project.clj"))))

(defn- fs-name [s]
  (str/replace s "-" "_"))

(defn- delete-file! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (.delete f)
      (println (str "  Deleted: " path)))))

(defn- delete-files! [dir pattern]
  (let [d (io/file dir)]
    (when (.exists d)
      (doseq [^File f (.listFiles d)
              :when (re-find pattern (.getName f))]
        (.delete f)
        (println (str "  Deleted: " (.getPath f)))))))

(defn- delete-tree! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [^File child (reverse (file-seq f))]
        (.delete child))
      (println (str "  Deleted: " path)))))

(defn- edit-file! [path edits]
  (let [f (io/file path)]
    (when (.exists f)
      (let [content (slurp f)
            updated (reduce (fn [c [old new]]
                              (str/replace c old new))
                            content
                            edits)]
        (if (= content updated)
          (println (str "  ⚠ No changes made to: " path))
          (do
            (spit f updated)
            (println (str "  Updated: " path))))))))

;; ----------------------------
;; SAFE EDN handling (FIX)
;; ----------------------------

(defn- load-edn [path]
  (read-string (slurp path)))

(defn- write-edn [path data]
  (spit path (with-out-str (pr data))))

(defn- safe-remove-i18n-keys []
  (println "\n--- Updating i18n files (SAFE MODE)...")

  (doseq [lang ["es" "en"]]
    (let [path (str "resources/i18n/" lang ".edn")]
      (when (.exists (io/file path))
        (let [data (load-edn path)

              ;; remove only known demo keys safely
              cleaned (apply dissoc data
                             [:entity/autores
                              :entity/categorias
                              :entity/libros
                              :entity/libros_imagenes
                              :entity/libros_autores
                              :entity/miembros
                              :entity/prestamos
                              :entity/prestamos_detalle])]

          (write-edn path cleaned)
          (println (str "  ✓ cleaned i18n: " lang)))))))

;; ----------------------------
;; other cleanup functions (unchanged)
;; ----------------------------

(defn- delete-migration-files! []
  (println "\n--- Deleting migration files...")
  (delete-files! "resources/migrations" #"^(008|009|010|011|012|013|014|015)-")
  (println "  ✓ Migration files deleted"))

(defn- delete-entity-configs! []
  (println "\n--- Deleting entity configs...")
  (doseq [e ["autores" "categorias" "libros" "libros_imagenes"
             "libros_autores" "miembros"
             "prestamos" "prestamos_detalle"]]
    (delete-file! (str "resources/entities/" e ".edn")))
  (println "  ✓ Entity configs deleted"))

(defn- delete-hooks! [pname]
  (println "\n--- Deleting hooks...")
  (let [base (str "src/" (fs-name pname) "/hooks")]
    (doseq [h ["libros" "prestamos"]]
      (delete-file! (str base "/" h ".clj"))))
  (println "  ✓ Hooks deleted"))

(defn- delete-handler-dirs! [pname]
  (println "\n--- Deleting handler directories...")
  (let [base (str "src/" (fs-name pname) "/handlers")]
    (doseq [d ["prestamos" "reportes"]]
      (delete-tree! (str base "/" d))))
  (println "  ✓ Handler directories deleted"))

(defn- delete-uploads! []
  (println "\n--- Deleting uploads...")
  (let [f (io/file "uploads")]
    (when (.exists f)
      (delete-tree! "uploads")))
  (println "  ✓ Uploads deleted"))

;; ----------------------------
;; route/menu cleanup (unchanged logic)
;; ----------------------------

(defn- update-proutes! [pname]
  (println "\n--- Updating routes...")
  (edit-file! (str "src/" (fs-name pname) "/routes/proutes.clj")
              [["   [cgen.handlers.prestamos.controller :as prestamos]\n" ""]
               ["   [cgen.handlers.reportes.controller :as reportes]\n" ""]
               ["  ;; Loan desk\n  (GET \"/prestamos\" req (prestamos/main req))\n  (POST \"/prestamos/crear\" req (prestamos/crear-prestamo req))\n  (GET \"/prestamos/devolver/:id\" [id :as req] (prestamos/devolver-libro req))\n  (GET \"/prestamos/renovar/:id\" [id :as req] (prestamos/renovar-prestamo req))\n  ;; Library reports\n  (GET \"/reportes/libros\" req (reportes/libros req))\n  (GET \"/reportes/miembros\" req (reportes/miembros req))\n  (GET \"/reportes/prestamos\" req (reportes/prestamos req))\n" ""]])
  (println "  ✓ Routes updated"))

(defn- update-menu! [pname]
  (println "\n--- Updating menu...")
  (edit-file! (str "src/" (fs-name pname) "/menu.clj")
              [["   [\"/dashboard\" \"DASHBOARD\" \"bi bi-speedometer2\" \"U\" 10]\n   [\"/prestamos\" \"Escritorio Préstamos\" \"bi bi-journal-text\" \"U\" 15]])"
                "   [\"/dashboard\" \"DASHBOARD\" \"bi bi-speedometer2\" \"U\" 10]])"]])
  (println "  ✓ Menu updated"))

;; ----------------------------
;; main entry
;; ----------------------------

(defn -main [& args]
  (let [pname (pr-name)]
    (println (str "\nCleaning demo from project: " pname))

    (delete-migration-files!)
    (delete-entity-configs!)
    (delete-hooks! pname)
    (delete-handler-dirs! pname)
    (delete-uploads!)
    (update-proutes! pname)
    (update-menu! pname)

    ;; FIXED SAFE I18N UPDATE
    (safe-remove-i18n-keys)

    (println "\n✓ Cleanup complete!")
    (println "  The database still contains the demo tables.")
    (println "  To start fresh: drop/recreate DB, run migrations, seed again.")))
