(ns cgen.tools.upgrade
  "Idempotent framework upgrade: copies framework source files from
   template, renames namespaces, and applies backward-compatibility shims.
   Usage: lein fw-upgrade <framework-dir> [project-name]
   Example: lein fw-upgrade <framework-dir>             e.g. ~/Repo/cgen
            lein fw-upgrade <framework-dir> <project-name>
   Project name auto-detected from project.clj when omitted."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str])
  (:import [java.io File]))

;; ──────────────────────────────────────────────
;; File discovery
;; ──────────────────────────────────────────────

(def ^:private exclude-patterns
  [#"\.git"
   #"target"
   #"uploads"
   #"\.DS_Store"
   #"\.#"
   #"#.*#"])

(defn- should-exclude?
  [^File f]
  (let [path (.getPath f)]
    (some #(re-find % path) exclude-patterns)))

(defn- source-files
  "Return all non-directory, non-excluded files under dir."
  [^File dir]
  (filter (fn [^File f]
            (and (.isFile f)
                 (not (should-exclude? f))))
          (file-seq dir)))

(defn- relative-path
  "Return the path of child relative to parent."
  [^File child ^File parent]
  (let [child-path (.getAbsolutePath child)
        parent-path (.getAbsolutePath parent)]
    (subs child-path (inc (count parent-path)))))

;; ──────────────────────────────────────────────
;; Helpers
;; ──────────────────────────────────────────────

(defn- replace-ns-in-path
  "Derive destination path by replacing the framework namespace 'cgen'
   with the project's filesystem name."
  [fw-path fs-name]
  (str/replace fw-path "cgen" fs-name))

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
              has-native-access (boolean (re-find #"--enable-native-access=ALL-UNNAMED" content))
              base (-> content
                       (str/replace #"lein-ancient\s+\"0\.7\.0\""
                                    "lein-ancient \"1.0.0\"")
                       (str/replace #"sqlite-jdbc\s+\"3\.53\.1\.0\""
                                    "sqlite-jdbc \"3.53.2.0\"")
                       (str/replace #":resource-paths\s+\[\"shared\" \"resources\"\]"
                                    ":resource-paths [\"resources\"]"))
              base (if has-native-access
                     base
                     (-> base
                         (str/replace #"(:uberjar\s+\{:aot :all\s*\n\s*:main\s+\S+\s*\n\s*:jvm-opts\s+\[)([^\]]*)\]"
                                      (str "$1$2\n                                   \"--enable-native-access=ALL-UNNAMED\"]"))
                         (str/replace #"(:dev\s+\{:source-paths\s+\[\"src\" \"dev\"\]\s*\n\s*:main\s+\S+)"
                                      (str "$1\n                    :jvm-opts [\"--enable-native-access=ALL-UNNAMED\"]"))))
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
  "Usage: lein fw-upgrade <framework-dir> [project-name]
   Example: lein fw-upgrade <framework-dir>             e.g. ~/Repo/cgen
            lein fw-upgrade <framework-dir> <project-name>
   Project name auto-detected from project.clj when omitted."
  [& args]
  (let [cgen-dir (or (first args) (System/getenv "CGEN_DIR"))
        target-dir "."
        project-name (or (second args) (detect-project-name target-dir))]

    (when-not cgen-dir
      (println)
      (println "Usage: lein fw-upgrade <framework-dir> [project-name]")
      (println)
      (println "  <framework-dir>     Path to the framework template directory (required)")
      (println "  [project-name] Project name override (default: auto-detect from project.clj)")
      (println)
      (println "Examples:")
      (println "  lein fw-upgrade <framework-dir>             e.g. ~/Repo/cgen")
      (println "  lein fw-upgrade <framework-dir> <project-name>")
      (println "  CGEN_DIR=<framework-dir> lein fw-upgrade")
      (println)
      (System/exit 1))

    (when-not project-name
      (println)
      (println "ERROR: could not detect project name.")
      (println "Place project.clj in the current directory or pass name as second arg:")
      (println "  lein fw-upgrade <framework-dir> <project-name>")
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
        (println (format "ERROR: framework directory not found: %s" cgen-dir))
        (println)
        (System/exit 1))

      (println "Step 1: Copying framework source files...")
      (let [copied (atom 0)]
        (doseq [subdir ["src" "dev"]]
          (let [fw-ns-dir (io/file cgen-dir subdir "cgen")]
            (when (.exists fw-ns-dir)
              (doseq [^File f (source-files fw-ns-dir)]
                (let [rel-path (str subdir "/cgen/" (relative-path f fw-ns-dir))
                      dest-path (str target-dir "/" (replace-ns-in-path rel-path fs-name))]
                  (copy-with-ns-rename (.getPath f) dest-path project-name)
                  (swap! copied inc))))))
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
