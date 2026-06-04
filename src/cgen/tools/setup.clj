(ns cgen.tools.setup
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]))

(def ^:private exclude-prefixes
  #{".git/" "target/" "db/" ".lsp/" ".clj-kondo/"})

(defn- excluded? [^String rel-path]
  (or (= rel-path ".nrepl-port")
      (= rel-path ".lein-repl-history")
      (some #(.startsWith rel-path %) exclude-prefixes)))

(defn- copy-tree [^java.io.File root ^java.io.File target]
  (.mkdirs target)
  (doseq [^java.io.File f (file-seq root)
          :when (.isFile f)]
    (let [root-path (.getCanonicalPath root)
          file-path (.getCanonicalPath f)
          sep (System/getProperty "file.separator")
          rel (subs file-path (inc (count root-path)))]
      (when-not (excluded? rel)
        (let [dest (io/file target rel)]
          (io/make-parents dest)
          (io/copy f dest))))))

(defn- rename-contents [^java.io.File file new-ns]
  (let [content (slurp file)
        new-content (-> content
                        (str/replace #"(?<=[\s\(\[\"\'\:])cgen\." (str new-ns "."))
                        (str/replace #"cgen/" (str new-ns "/")))]
    (when (not= content new-content)
      (spit file new-content)
      (println "  " (.getPath file)))))

(defn- rename-edn-contents [^java.io.File file new-ns]
  (let [content (slurp file)]
    (when (re-find #"\:cgen\.hooks" content)
      (spit file (str/replace content #"\:cgen\.hooks" (str ":" new-ns ".hooks")))
      (println "  " (.getPath file)))))

(defn- walk-files [base-dir pattern f new-ns]
  (let [dir (io/file base-dir)]
    (when (.exists dir)
      (doseq [^java.io.File file (file-seq dir)
              :when (.isFile file)
              :when (re-find pattern (.getName file))]
        (f file new-ns)))))

(defn- rename-dir [base-dir old-name new-name]
  (let [old-file (io/file base-dir old-name)
        new-file (io/file base-dir new-name)]
    (when (.exists old-file)
      (println "  " old-name " -> " new-name)
      (.renameTo old-file new-file))))

(defn- update-project-clj [base-dir project-name new-ns]
  (let [project-file (io/file base-dir "project.clj")
        content (slurp project-file)
        new-content (-> content
                        (str/replace #"(?<=\(defproject\s)cgen(?=\s)" project-name)
                        (str/replace #"(?<=[\s\"])cgen\." (str new-ns ".")))]
    (spit project-file new-content)
    (println "  project.clj")))

(defn- run-lein [base-dir & args]
  (println "  lein" (str/join " " args) "...")
  (let [{:keys [exit out err]}
        (binding [clojure.java.shell/*sh-dir* base-dir]
          (apply sh "lein" args))]
    (println out)
    (when (not= 0 exit)
      (println "ERROR:" err)
      (System/exit 1))))

(defn- rename-app-config [base-dir project-name]
  (let [config-file (io/file base-dir "resources/config/app-config.edn")
        content (slurp config-file)]
    (spit config-file (str/replace content "cgen" project-name))
    (println "  app-config.edn")))

(defn- remove-setup-trace [base-dir new-ns]
  (let [project-file (io/file base-dir "project.clj")
        project-clj (slurp project-file)]
    (spit project-file
          (str/replace project-clj #"\n\s+\"setup\"[^}]*" "")))
  (let [tools-dir (io/file base-dir "src" new-ns "tools")]
    (when (.exists tools-dir)
      (doseq [f (reverse (file-seq tools-dir))]
        (io/delete-file f true))
      (println "  Removed" (.getPath tools-dir)))))

(defn -main [& args]
  (let [[project-name target-parent] args
        cgen-root (.getCanonicalFile (io/file "."))
        parent-dir (if target-parent
                     (io/file target-parent)
                     (.getParentFile cgen-root))]
    (when (or (nil? project-name) (str/blank? project-name))
      (println "Usage: lein setup <project-name> [target-dir]")
      (println "  e.g.  lein setup my-project              ; creates ../my-project/")
      (println "  e.g.  lein setup my-project /path/to     ; creates /path/to/my-project/")
      (System/exit 1))

    (let [target-dir (io/file parent-dir project-name)]
      (when (.exists target-dir)
        (println "ERROR: Target already exists:" (.getCanonicalPath target-dir))
        (println "  Remove it first or choose a different name.")
        (System/exit 1))

      (println "\n=== Copying project skeleton...")
      (copy-tree cgen-root target-dir)

      (let [new-ns project-name]
        (println "\n=== Renaming source files...")
        (walk-files (io/file target-dir "src") #"\.clj$" rename-contents new-ns)
        (walk-files (io/file target-dir "dev") #"\.clj$" rename-contents new-ns)
        (walk-files (io/file target-dir "resources/entities") #"\.edn$" rename-edn-contents new-ns)
        (update-project-clj target-dir project-name new-ns)

        (println "\n=== Renaming directories...")
        (rename-dir target-dir "src/cgen" (str "src/" new-ns))
        (rename-dir target-dir "dev/cgen" (str "dev/" new-ns))

        (println "\n=== Updating config...")
        (rename-app-config target-dir project-name)
        (.mkdirs (io/file target-dir "db"))

        (println "\n=== Creating database and seeding...")
        (run-lein (.getCanonicalPath target-dir) "migrate")
        (run-lein (.getCanonicalPath target-dir) "run" "-m" (str new-ns ".models.cdb/database") "localdb")
        (run-lein (.getCanonicalPath target-dir) "run" "-m" (str new-ns ".models.cdb/seed-non-users") "localdb")

        (println "\n=== Cleaning up...")
        (remove-setup-trace target-dir new-ns)

        (println "\n" (str "✓ Project " project-name " created successfully!"))
        (println "  Location:" (.getCanonicalPath target-dir))
        (println "  Users:")
        (println "    user@example.com / user")
        (println "    admin@example.com / admin")
        (println "    system@example.com / system")
        (println)
        (println "  Next steps:")
        (println "    1. cd" project-name)
        (println "    2. Delete example migrations, entities, hooks you don't need")
        (println "    3. Write your own migrations, re-run lein scaffold --all")
        (println "    4. Start the dev server: lein with-profile dev run")))))
