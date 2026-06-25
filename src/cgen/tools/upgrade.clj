(ns cgen.tools.upgrade
  "Idempotent framework upgrade: copies framework source files from
   template, renames namespaces, and applies backward-compatibility shims.
   Usage:   lein fw-upgrade <target-dir>
   Example: lein fw-upgrade /path/to/project
   Run from the cgen project root.  Project name auto-detected from
   target's project.clj."
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
   #"#.*#"
   #"menu\.clj"
   #"routes\.clj"
   #"proutes\.clj"
   #"tools/upgrade\.clj"
   #"tools/setup\.clj"])

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
      (spit f (str "\n" text) :append true)
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
;; Menu backward-compatibility
;; ──────────────────────────────────────────────

(defn- find-matching-close
  "Find the index of the matching close paren for the open paren at start-idx."
  [s start-idx]
  (loop [i (inc start-idx)
         depth 1]
    (if (zero? depth)
      (dec i)
      (let [c (get s i)]
        (case c
          \( (recur (inc i) (inc depth))
          \) (recur (inc i) (dec depth))
          (recur (inc i) depth))))))

(defn- add-menu-backward-compat!
  "Insert (def custom-dropdown-items ...) after (def custom-dropdowns ...)
   and update get-menu-config to use it, in the project's menu.clj."
  [target-dir project-name]
  (let [fs-name (str/replace project-name "-" "_")
        template-menu (io/file "." "src" "cgen" "menu.clj")
        project-menu (io/file target-dir "src" fs-name "menu.clj")]
    (when (and (.exists template-menu) (.exists project-menu))
      (let [template-content (slurp template-menu)
            project-content (slurp project-menu)
            has-items (re-find #"def custom-dropdown-items" project-content)
            has-integration (re-find #"combined-with-extra-items" project-content)
            needs-items? (not has-items)
            needs-update? (or (not has-items) (not has-integration))
            result (atom project-content)]

        ;; Step 1: Insert (def custom-dropdown-items ...) after custom-dropdowns if missing
        (when needs-items?
          (let [tmpl-open (str/index-of template-content "(def custom-dropdown-items")]
            (when tmpl-open
              (let [tmpl-close (find-matching-close template-content tmpl-open)
                    items-form (subs template-content tmpl-open (inc tmpl-close))
                    proj-open (str/index-of @result "(def custom-dropdowns")]
                (when proj-open
                  (let [proj-close (find-matching-close @result proj-open)
                        before (subs @result 0 (inc proj-close))
                        after (subs @result (inc proj-close))]
                    (reset! result (str before "\n\n" items-form "\n" after))))))))

        ;; Step 2: Replace get-menu-config with template version if it lacks custom-dropdown-items integration
        (when needs-update?
          (let [tmpl-gmc-open (str/index-of template-content "(defn get-menu-config")]
            (when tmpl-gmc-open
              (let [tmpl-gmc-close (find-matching-close template-content tmpl-gmc-open)
                    tmpl-gmc (subs template-content tmpl-gmc-open (inc tmpl-gmc-close))
                    proj-gmc-open (str/index-of @result "(defn get-menu-config")]
                (when proj-gmc-open
                  (let [proj-gmc-close (find-matching-close @result proj-gmc-open)
                        before (subs @result 0 proj-gmc-open)
                        after (subs @result (inc proj-gmc-close))
                        replaced (str before tmpl-gmc "\n" after)]
                    (reset! result replaced))))))

        ;; Write back if changed
        (when (not= @result project-content)
          (spit project-menu @result)
          (println "  ✓ Updated menu.clj with custom-dropdown-items support")))))))

;; ──────────────────────────────────────────────
;; Routes backward-compatibility
;; ──────────────────────────────────────────────

(defn- extract-routes-with-paths
  "Given a defroutes body string, return a seq of [method+path full-form] pairs."
  [body]
  (let [routes (atom [])]
    (loop [i 0]
      (let [open-idx (str/index-of body "(" i)]
        (if (or (nil? open-idx) (>= open-idx (count body)))
          @routes
          (let [close-idx (find-matching-close body open-idx)
                form (subs body open-idx (inc close-idx))
                method (second (re-find #"\(([A-Z]+)\s" form))
                path (re-find #"\"/?[^\"]*\"" form)
                key (str method " " path)]
            (when path
              (swap! routes conj [key form]))
            (recur (inc close-idx))))))))

(defn- add-routes-backward-compat!
  [target-dir project-name]
  (let [fs-name (str/replace project-name "-" "_")
        template-file (io/file "." "src" "cgen" "routes" "routes.clj")
        project-file (io/file target-dir "src" fs-name "routes" "routes.clj")]
    (when (and (.exists template-file) (.exists project-file))
      (let [template-content (slurp template-file)
            result (atom (slurp project-file))
            changed? (atom false)]
        (doseq [defroutes-name ["open-routes" "password-routes"]]
          (let [tmpl-start (str/index-of template-content (str "(defroutes " defroutes-name))
                tmpl-routes (when tmpl-start
                              (let [tmpl-close (find-matching-close template-content tmpl-start)
                                    tmpl-defroutes (subs template-content tmpl-start tmpl-close)
                                    tmpl-body (subs tmpl-defroutes
                                                    (inc (str/index-of tmpl-defroutes " "
                                                                       (count (str "(defroutes " defroutes-name)))))]
                                (extract-routes-with-paths tmpl-body)))]
            (doseq [[sig route-form] tmpl-routes]
              (when-let [proj-start (str/index-of @result (str "(defroutes " defroutes-name))]
                (let [proj-close (find-matching-close @result proj-start)
                      proj-block (subs @result proj-start (inc proj-close))]
                  (when-not (str/includes? proj-block sig)
                    (let [close-idx (find-matching-close proj-block 0)
                          updated-block (str (subs proj-block 0 close-idx)
                                             "\n  " route-form
                                             (subs proj-block close-idx))]
                      (reset! result (str/replace-first @result proj-block updated-block))
                      (reset! changed? true))))))))
        (when @changed?
          (spit project-file @result)
          (println "  ✓ Added missing home routes to routes.clj"))))))

;; ──────────────────────────────────────────────
;; project.clj update
;; ──────────────────────────────────────────────

(defn- update-project-clj!
  [target-dir project-name]
  (let [f (io/file target-dir "project.clj")]
    (when (.exists f)
      (let [content (slurp f)]
        (let [has-extra-aliases (boolean (re-find #"\"i18n-lint\"" content))
              has-native-access (boolean (re-find #"--enable-native-access=ALL-UNNAMED" content))
              base (-> content
                       ;; Strip any leftover fw-upgrade alias — it lives only in cgen
                       (str/replace #"\s+\"fw-upgrade\" \[\"run\" \"-m\" \".*?upgrade\" \"--\"\]" "")
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
                         (str/replace #"(:dev\s+\{:source-paths\s+\[\"src\" \"dev\"\]\s*\n\s*:main\s+[\w.-]+)"
                                      (str "$1\n                    :jvm-opts [\"--enable-native-access=ALL-UNNAMED\"]"))))
              updated (if has-extra-aliases
                        base
                        (str/replace base
                                     #"(\"gen-handler\" \[\"run\" \"-m\" \".*?\" \"--\"\])"
                                     (str "$1\n             \"i18n-lint\" [\"run\" \"-m\" \"" project-name ".i18n.lint\"]\n"
                                          "             \"clean-demo\" [\"run\" \"-m\" \"" project-name ".tools.clean-demo\" \"--\"]")))]
          (spit f updated)
          (println "  ✓ project.clj updated"))))))

;; ──────────────────────────────────────────────
;; Main
;; ──────────────────────────────────────────────

(defn -main
  "Usage: lein fw-upgrade <target-dir>
   Run from the cgen project root.  Project name auto-detected from
   target's project.clj."
  [& args]
  (let [target-dir (first args)
        cgen-dir "."
        project-name (detect-project-name target-dir)]

    (when-not target-dir
      (println)
      (println "Usage: lein fw-upgrade <target-dir>")
      (println)
      (println "  <target-dir>  Path to the project to upgrade (required)")
      (println)
      (println "Example:")
      (println "  lein fw-upgrade /path/to/project")
      (println)
      (System/exit 1))

    (when-not project-name
      (println)
      (println (format "ERROR: could not detect project name from %s/project.clj" target-dir))
      (println)
      (System/exit 1))

    (let [cgen-dir-f (io/file cgen-dir)
          fs-name (str/replace project-name "-" "_")]

      (println)
      (println "╔══════════════════════════════════════════════╗")
      (println "║       Framework Upgrade Tool                 ║")
      (println "╚══════════════════════════════════════════════╝")
      (println)
      (println (format "  Source:      %s" (.getCanonicalFile cgen-dir-f)))
      (println (format "  Target:      %s" target-dir))
      (println (format "  Project:     %s" project-name))
      (println)

      (println "Step 1: Removing previous framework tools from target...")
      (doseq [fname ["upgrade.clj" "setup.clj"]]
        (let [f (io/file target-dir "src" fs-name "tools" fname)]
          (when (.exists f)
            (.delete f)
            (println (format "  ✓ removed %s" (.getPath f))))))
      (println)

      (println "Step 2: Copying framework source files...")
      (let [copied (atom 0)]
        (doseq [subdir ["src" "dev"]]
          (let [fw-ns-dir (io/file cgen-dir subdir "cgen")]
            (when (.exists fw-ns-dir)
              (doseq [^File f (source-files fw-ns-dir)]
                (let [rel-path (str subdir "/cgen/" (relative-path f fw-ns-dir))]
                  (when (or (not (.startsWith rel-path "src/cgen/handlers/"))
                            (.startsWith rel-path "src/cgen/handlers/home/"))
                    (let [dest-path (str target-dir "/" (replace-ns-in-path rel-path fs-name))]
                      (copy-with-ns-rename (.getPath f) dest-path project-name)
                      (swap! copied inc))))))))
        ;; Copy i18n resource files (en.edn, es.edn)
        (let [i18n-dir (io/file cgen-dir "resources" "i18n")]
          (when (.exists i18n-dir)
            (doseq [^File f (source-files i18n-dir)]
              (let [rel-path (relative-path f i18n-dir)
                    dest-path (str target-dir "/resources/i18n/" rel-path)]
                (io/make-parents (io/file dest-path))
                (io/copy f (io/file dest-path))
                (println (format "  ✓ %s" dest-path))
                (swap! copied inc)))))
        (println (format "  ▶ %d files copied" @copied)))

      (println)
      (println "Step 3: Applying backward-compatibility shims...")
      (add-backward-compat-shims! target-dir project-name)
      (add-menu-backward-compat! target-dir project-name)
      (add-routes-backward-compat! target-dir project-name)

      (println)
      (println "Step 4: Updating project.clj...")
      (update-project-clj! target-dir project-name)

      (println)
      (println "╔══════════════════════════════════════════════╗")
      (println "║           Upgrade Complete!                  ║")
      (println "╚══════════════════════════════════════════════╝")
      (println)
      (println "  Run: lein with-profile dev run")
      (println "  to test the upgraded project.")
      (println))))
