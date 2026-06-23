(ns cgen.tools.upgrade
  "Idempotent framework upgrade: copies framework source files from cgen
   template, renames namespaces, and applies backward-compatibility shims.
   Usage: lein fw-upgrade <cgen-dir> [project-name]
   Example: lein fw-upgrade ~/Repo/cgen
            lein fw-upgrade ~/Repo/cgen contactos
   Project name auto-detected from project.clj when omitted."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import [java.io File]))

;; ──────────────────────────────────────────────
;; Manifest: cgen source paths (flat list)
;; Destination is derived by replacing "cgen" with the project's fs-name.
;; ──────────────────────────────────────────────

(def framework-src-files
  "Relative paths of framework source files in the cgen template.
   Destination path is derived by replacing 'cgen' with the project's
   filesystem-safe name (e.g. 'src/cgen/core.clj' → 'src/<project>/core.clj')."
  ["src/cgen/core.clj"
   "src/cgen/i18n/core.clj"
   "src/cgen/i18n/lint.clj"
   "src/cgen/models/export.clj"
   "src/cgen/models/crud.clj"
   "src/cgen/models/db.clj"
   "src/cgen/models/db/sqlite.clj"
   "src/cgen/models/db/mysql.clj"
   "src/cgen/models/db/postgres.clj"
   "src/cgen/models/form.clj"
   "src/cgen/models/grid.clj"
   "src/cgen/models/util.clj"
   "src/cgen/models/routes.clj"
   "src/cgen/models/schema_enhanced.clj"
   "src/cgen/models/email.clj"
   "src/cgen/web/csrf.clj"
   "src/cgen/config/loader.clj"
   "src/cgen/migrations.clj"
   "src/cgen/engine/config.clj"
   "src/cgen/engine/query.clj"
   "src/cgen/engine/crud.clj"
   "src/cgen/engine/render.clj"
   "src/cgen/engine/router.clj"
   "src/cgen/engine/scaffold.clj"
   "src/cgen/engine/menu.clj"
   "src/cgen/tabgrid/core.clj"
   "src/cgen/tabgrid/data.clj"
   "src/cgen/tabgrid/handlers.clj"
   "src/cgen/tabgrid/render.clj"
   "src/cgen/layout.clj"
   "src/cgen/gen/handler.clj"])

(def dev-src-files
  ["dev/cgen/dev.clj"])

;; ──────────────────────────────────────────────
;; Helpers
;; ──────────────────────────────────────────────

(defn- cgen->dest-path
  "Derive destination path by replacing the first path component 'cgen'
   with the project's filesystem name."
  [cgen-path fs-name]
  (str/replace-first cgen-path "cgen" fs-name))

(defn- detect-project-name
  "Read project.clj and extract the project name from (defproject <name> ...).
   Returns nil if not found."
  [dir]
  (let [f (io/file dir "project.clj")]
    (when (.exists f)
      (when-let [m (re-find #"\(defproject\s+([^\s\"']+)" (slurp f))]
        (second m)))))

(defn- copy-with-ns-rename
  "Read src file, replace all 'cgen' with 'project-name', write to dest.
   Returns true on success."
  [src-path dest-path project-name]
  (let [src-file (io/file src-path)]
    (when (.exists src-file)
      (let [content (slurp src-file)
            updated (str/replace content "cgen" project-name)]
        (io/make-parents (io/file dest-path))
        (spit dest-path updated)
        (println (format "  ✓ %s" dest-path))
        true))))

(defn- append-to-file
  "Appends text to an existing file."
  [path text]
  (let [f (io/file path)]
    (when (.exists f)
      (spit f (str (slurp f) "\n" text) :append true)
      (println (format "  ✓ (patched) %s" path)))))

;; ──────────────────────────────────────────────
;; Backward-compatibility shims
;; ──────────────────────────────────────────────

(defn- add-backward-compat-shims!
  "Add backward-compatibility wrappers for project-specific code that
   depends on the old API."
  [target-dir project-name]
  (let [fs-name (str/replace project-name "-" "_")
        util-file (str target-dir "/src/" fs-name "/models/util.clj")]
    ;; image-link → file-link alias (hooks use image-link)
    (when (.exists (io/file util-file))
      (let [content (slurp util-file)]
        (when-not (re-find #"defn image-link" content)
          (append-to-file util-file
                          (str "\n;; Backward-compat alias for hooks (added by lein upgrade)\n"
                               "(defn image-link [image-name]\n"
                               "  (file-link image-name))\n"))
          (println "  ✓ Added image-link alias (backward compat)"))))))

;; ──────────────────────────────────────────────
;; project.clj update
;; ──────────────────────────────────────────────

(defn- update-project-clj!
  [target-dir project-name]
  (let [f (io/file target-dir "project.clj")]
    (when (.exists f)
      (let [content (slurp f)]
        (let [has-alias (boolean (re-find #"\"fw-upgrade\"" content))
              base (-> content
                       (str/replace #"lein-ancient\s+\"0\.7\.0\""
                                    "lein-ancient \"1.0.0\"")
                       (str/replace #"sqlite-jdbc\s+\"3\.53\.1\.0\""
                                    "sqlite-jdbc \"3.53.2.0\"")
                       (str/replace #":resource-paths\s+\[\"shared\" \"resources\"\]"
                                    ":resource-paths [\"resources\"]")
                       (str/replace #"(:uberjar\s+\{:aot :all\s*\n\s*:main\s+\S+\s*\n\s*:jvm-opts\s+\[)([^\]]*)\]"
                                    (str "$1$2\n                                   \"--enable-native-access=ALL-UNNAMED\"]"))
                       (str/replace #"(:dev\s+\{:source-paths\s+\[\"src\" \"dev\"\]\s*\n\s*:main\s+\S+)"
                                    (str "$1\n                    :jvm-opts [\"--enable-native-access=ALL-UNNAMED\"]")))
              updated (if has-alias
                        base
                        (str/replace base
                                     #"(\"gen-handler\" \[\"run\" \"-m\" \".*?\" \"--\"\])"
                                     (str "$1\n             \"i18n-lint\" [\"run\" \"-m\" \"" project-name ".i18n.lint\"]\n"
                                          "             \"clean-demo\" [\"run\" \"-m\" \"" project-name ".tools.clean-demo\" \"--\"]\n"
                                          "             \"fw-upgrade\" [\"run\" \"-m\" \"" project-name ".tools.upgrade\" \"--\"]")))]
          (spit f updated)
          (println "  ✓ project.clj updated"))))))

;; ──────────────────────────────────────────────
;; Main
;; ──────────────────────────────────────────────

(defn -main
  "Usage: lein fw-upgrade <cgen-dir> [project-name]
   Example: lein fw-upgrade ~/Repo/cgen
            lein fw-upgrade ~/Repo/cgen contactos
   Project name auto-detected from project.clj when omitted."
  [& args]
  (let [cgen-dir (or (first args) (System/getenv "CGEN_DIR"))
        target-dir "."
        project-name (or (second args) (detect-project-name target-dir))]

    (when-not cgen-dir
      (println)
      (println "Usage: lein fw-upgrade <cgen-dir> [project-name]")
      (println)
      (println "  <cgen-dir>     Path to the cgen template directory (required)")
      (println "  [project-name] Project name override (default: auto-detect from project.clj)")
      (println)
      (println "Examples:")
      (println "  lein fw-upgrade ~/Repo/cgen")
      (println "  lein fw-upgrade ~/Repo/cgen contactos")
      (println "  CGEN_DIR=~/Repo/cgen lein fw-upgrade")
      (println)
      (System/exit 1))

    (when-not project-name
      (println)
      (println "ERROR: could not detect project name.")
      (println "Place project.clj in the current directory or pass name as second arg:")
      (println "  lein fw-upgrade ~/Repo/cgen contactos")
      (println)
      (System/exit 1))

    (let [cgen-dir (if (.endsWith cgen-dir "/") (subs cgen-dir 0 (dec (count cgen-dir))) cgen-dir)
          cgen-dir-f (io/file cgen-dir)
          fs-name (str/replace project-name "-" "_")]

      (println)
      (println "╔══════════════════════════════════════════════╗")
      (println "║       Framework Upgrade Tool                 ║")
      (println "╚══════════════════════════════════════════════╝")
      (println)
      (println (format "  Source:      %s" cgen-dir))
      (println (format "  Target:      %s" (.getCanonicalFile (io/file target-dir))))
      (println (format "  Project:     %s" project-name))
      (println)

      (when-not (.exists cgen-dir-f)
        (println (format "ERROR: cgen directory not found: %s" cgen-dir))
        (println)
        (System/exit 1))

      (println "Step 1: Copying framework source files...")
      (let [copied (atom 0)]
        (doseq [src framework-src-files]
          (let [src-path (str cgen-dir "/" src)
                dest-path (str target-dir "/" (cgen->dest-path src fs-name))]
            (when (copy-with-ns-rename src-path dest-path project-name)
              (swap! copied inc))))
        (doseq [src dev-src-files]
          (let [src-path (str cgen-dir "/" src)
                dest-path (str target-dir "/" (cgen->dest-path src fs-name))]
            (copy-with-ns-rename src-path dest-path project-name)
            (swap! copied inc)))
        (println (format "  ▶ %d files copied" @copied)))

      (println)
      (println "Step 2: Applying backward-compatibility shims...")
      (add-backward-compat-shims! target-dir project-name)

      (println)
      (println "Step 3: Updating project.clj...")
      (update-project-clj! target-dir project-name)

      (println)
      (println "╔══════════════════════════════════════════════╗")
      (println "║           Upgrade Complete!                  ║")
      (println "╚══════════════════════════════════════════════╝")
      (println)
      (println "  Run: lein with-profile dev run")
      (println "  to test the upgraded project.")
      (println))))
