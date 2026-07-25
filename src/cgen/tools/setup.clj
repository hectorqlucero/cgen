(ns cgen.tools.setup
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as str])
  (:import [java.io File]))

(def ^:private source-dir
  "Root of the cgen project (this project)."
  (let [d (io/file ".")]
    (.getCanonicalFile d)))

(def ^:private exclude-patterns
  [#"\.git$"          ;; legacy fallback; primary check is in should-exclude?
   #"target"
   #"uploads"
   #"\.DS_Store"
   #"\.#"
   #"#.*#"
   #"tools/upgrade\.clj"])

(defn- should-exclude? [^File f]
  (let [path (.getPath f)
        parts (str/split path #"/")]
    (or (some #(re-find % path) exclude-patterns)
        ;; Exclude .git directory and its contents (but not .gitignore, .github, etc.)
        (some #(= % ".git") parts)
        ;; Exclude the root-level db/ directory only (not src/.../db/)
        (and (re-find #"(^|/)db(/|$)" path)
             (not (re-find #"/src/" path))
             (not (re-find #"/resources/" path))))))

(defn- file-seq-filtered
  "Recurse directory, skip excluded dirs/files."
  [^File dir]
  (filter (fn [^File f]
            (and (not (should-exclude? f))
                 (not (.isDirectory f))))
          (file-seq dir)))

(defn- copy-file! [^File src ^File dest-dir]
  (let [rel (.getAbsolutePath src)
        base (.getAbsolutePath source-dir)
        relative-path (subs rel (inc (count base)))
        dest (io/file dest-dir relative-path)]
    (io/make-parents dest)
    (io/copy src dest)
    dest))

(defn- rename-ns-in-file!
  "Replace 'cgen' with new-name in file contents, skipping binary files."
  [^File f new-name]
  (let [name (.getName f)]
    (when (some #(str/ends-with? name %) [".clj" ".edn" ".md" ".json" ".css" ".js" ".html" ".sql" ".txt" ".gitignore"])
      (let [content (slurp f)
            updated (str/replace content "cgen" new-name)]
        (spit f updated)))))

(defn- fs-name
  "Replace dashes with underscores for filesystem compatibility."
  [s]
  (str/replace s "-" "_"))

(defn- rename-file-names!
  "Rename files and dirs that contain 'cgen' in their name.
   Uses underscores in filenames for Clojure classpath compatibility."
  [^File root new-name]
  (let [fs-new (fs-name new-name)]
    ;; Walk from leaves to root
    (doseq [^File f (reverse (file-seq root))
            :when (not (should-exclude? f))
            :let [parent (.getParentFile f)
                  old-name (.getName f)
                  ;; Replace 'cgen' with fs-new in filenames
                  new-file-name (str/replace old-name "cgen" fs-new)]
            :when (not= old-name new-file-name)]
      (let [dest (io/file parent new-file-name)]
        (.renameTo f dest)
        (println "  Renamed:" (.getPath dest))))))

(defn- update-project-clj! [^File root new-name]
  (let [f (io/file root "project.clj")]
    (when (.exists f)
      (let [content (slurp f)
            updated (-> content
                        (str/replace #"cgen" new-name)
                        (str/replace #"\"cgen\"" (str "\"" new-name "\""))
                        (str/replace #":description \"cgen\"" (str ":description \"" new-name "\""))
                        (str/replace #":site-name\s+\"cgen\"" (str ":site-name \"" new-name "\""))
                        (str/replace #":company-name\s+\"change_me\"" (str ":company-name \"" new-name "\""))
                        ;; Update DB name
                        (str/replace #"db/cgen\.sqlite" (str "db/" new-name ".sqlite"))
                        ;; Update uploads path
                        (str/replace #"\./uploads/cgen/" (str "./uploads/" new-name "/")))]
        (spit f updated)
        (println "  Updated: project.clj")))))

(defn- update-app-config! [^File root new-name]
  (let [f (io/file root "resources/config/app-config.edn")]
    (when (.exists f)
      (let [content (slurp f)
            updated (-> content
                        (str/replace #":site-name\s+\"cgen\"" (str ":site-name \"" new-name "\""))
                        (str/replace #":company-name\s+\"change_me\"" (str ":company-name \"" new-name "\""))
                        (str/replace #"db/cgen\.sqlite" (str "db/" new-name ".sqlite"))
                        (str/replace #"\./uploads/cgen/" (str "./uploads/" new-name "/"))
                        (str/replace #":base-url\s+\"http://localhost:3000/\"" ":base-url \"http://localhost:3000/\"")
                        (str/replace #":img-url\s+\"http://localhost:3000/uploads/\"" ":img-url \"http://localhost:3000/uploads/\"")
                        ;; Update connection db-name for all vendors
                        (str/replace #"db-name\s+\"([^\"]*)cgen([^\"]*)\"" (str "db-name \"$1" new-name "$2")))]
        (spit f updated)
        (println "  Updated: resources/config/app-config.edn")))))

(defn- remove-setup-from-child! [^File root new-name]
  (let [fs-new (fs-name new-name)
        tools-dir (io/file root "src" fs-new "tools")]
    (when (.exists tools-dir)
      ;; Delete setup.clj but keep clean_demo.clj
      (let [setup-file (io/file tools-dir "setup.clj")]
        (when (.exists setup-file)
          (.delete setup-file)
          (println "  Removed: setup.clj from child project")))
      ;; Remove tools dir if empty (no clean_demo.clj survived)
      (let [remaining (.listFiles tools-dir)]
        (when (or (nil? remaining) (empty? remaining))
          (.delete tools-dir))))
    ;; Remove the setup and fw-upgrade aliases but keep the clean-demo and i18n-lint aliases
    (let [pf (io/file root "project.clj")]
      (when (.exists pf)
        (let [content (slurp pf)
              updated (-> content
                          (str/replace #"\s+\"setup\" \[\"run\" \"-m\" \".*?setup\" \"--\"\]" "")
                          (str/replace #"\s+\"fw-upgrade\" \[\"run\" \"-m\" \".*?upgrade\" \"--\"\]" ""))]
          (spit pf updated)
          (println "  Removed: setup and fw-upgrade aliases from project.clj"))))))

(defn- run-lein-commands! [root new-name]
  (println)
  (println "--- Running lein migrate ---")
  (let [{:keys [exit out err]} (sh/sh "lein" "migrate" :dir (str root))]
    (println out)
    (when (seq err) (println err))
    (if (zero? exit)
      (println "  ✓ Migration successful")
      (println "  ⚠ Migration exited with code" exit)))
  (println)
  (println "--- Seeding database ---")
  (let [{:keys [exit out err]} (sh/sh "lein" "database" :dir (str root))]
    (println out)
    (when (seq err) (println err))
    (if (zero? exit)
      (println "  ✓ Database seeded")
      (println "  ⚠ Seed exited with code" exit)))
  (println)
  (println "--- Seeding non-user tables ---")
  (let [{:keys [exit out err]} (sh/sh "lein" "seed-non-users" "localdb" :dir (str root))]
    (println out)
    (when (seq err) (println err))
    (if (zero? exit)
      (println "  ✓ Non-user tables seeded")
      (println "  ⚠ Seed exited with code" exit))))

(defn- print-success! [root-dir new-name]
  (println)
  (println "╔══════════════════════════════════════════════════╗")
  (println "║           Project created successfully!          ║")
  (println "╠══════════════════════════════════════════════════╣")
  (println "║                                                 ║")
  (println (str "  Project: " new-name))
  (println (str "  Location: " (.getAbsolutePath root-dir)))
  (println "║                                                 ║")
  (println "  Next steps:")
  (println)
  (println (str "  cd " (.getAbsolutePath root-dir)))
  (println "  lein with-profile dev run")
  (println)
  (println "  Open http://localhost:3000 in your browser.")
  (println "  Login with: admin@example.com / admin")
  (println "║                                                 ║")
  (println "╚══════════════════════════════════════════════════╝"))

(defn -main
  "Main entry point for lein setup.

  Usage:
    lein setup my-project        — creates ./my-project
    lein setup /tmp/my-project   — creates /tmp/my-project"
  [& args]
  (let [target-arg (first args)]
    (when (str/blank? target-arg)
      (println "Usage: lein setup <project-name>")
      (println "       lein setup /path/to/project")
      (System/exit 1))
    (let [;; Resolve target path
          target-file (io/file target-arg)
          target-dir (if (.isAbsolute target-file)
                       target-file
                       (io/file (.getParent source-dir) target-arg))
          new-name (.getName target-dir)]
      (when (.exists target-dir)
        (println (str "Error: Directory already exists: " (.getAbsolutePath target-dir)))
        (System/exit 1))
      (println (str "Creating project '" new-name "' at " (.getAbsolutePath target-dir)))
      (println)
      ;; Step 1: Copy all files
      (println "Step 1: Copying files...")
      (io/make-parents (io/file target-dir "src"))
      (doseq [^File f (file-seq-filtered source-dir)]
        (copy-file! f target-dir))
      (println "  ✓ Files copied")
      (println)
      ;; Step 2: Rename namespace references in file contents
      (println "Step 2: Updating namespace references...")
      (doseq [^File f (file-seq-filtered target-dir)]
        (rename-ns-in-file! f new-name))
      (println "  ✓ Namespace references updated")
      (println)
      ;; Step 3: Rename files and directories
      (println "Step 3: Renaming files and directories...")
      (rename-file-names! target-dir new-name)
      (println)
      ;; Step 4: Update project.clj
      (println "Step 4: Updating project configuration...")
      (update-project-clj! target-dir new-name)
      (update-app-config! target-dir new-name)
      (println)
      ;; Step 5: Remove setup tool from child project
      (println "Step 5: Cleaning up...")
      (remove-setup-from-child! target-dir new-name)
      (println)
      ;; Step 6: Run migrations and seed
      (println "Step 6: Setting up database...")
      (run-lein-commands! target-dir new-name)
      (println)
      ;; Step 7: Print success
      (print-success! target-dir new-name))
    (System/exit 0)))
