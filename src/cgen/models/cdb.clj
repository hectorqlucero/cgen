(ns cgen.models.cdb
  (:require
   [clojure.java.io :as io]
   [clojure.string :as st]
   [buddy.hashers :as hashers]
   [clj-time.core :as t]
   [cgen.models.crud :as crud :refer [Insert-multi Query!]]))

(def users-rows
  [{:lastname  "User"
    :firstname "Regular"
    :username  "user@example.com"
    :password  (hashers/derive "user")
    :dob       "1957-02-07"
    :email     "user@example.com"
    :level     "U"
    :active    "T"}
   {:lastname "User"
    :firstname "Admin"
    :username "admin@example.com"
    :password (hashers/derive "admin")
    :dob "1957-02-07"
    :email "admin@example.com"
    :level "A"
    :active "T"}
   {:lastname "User"
    :firstname "System"
    :username "system@example.com"
    :password (hashers/derive "system")
    :dob "1957-02-07"
    :email "system@example.com"
    :level "S"
    :active "T"}])

(def autores-rows
  [{:id 1 :nombre "Gabriel" :apellidos "García Márquez" :biografia "Escritor colombiano, premio Nobel de Literatura 1982." :activo "T"}
   {:id 2 :nombre "Isabel" :apellidos "Allende" :biografia "Escritora chilena, autora de La casa de los espíritus." :activo "T"}
   {:id 3 :nombre "Carlos" :apellidos "Fuentes" :biografia "Escritor mexicano, una de las figuras centrales del boom latinoamericano." :activo "T"}
   {:id 4 :nombre "Jorge Luis" :apellidos "Borges" :biografia "Escritor argentino, maestro del cuento corto y la literatura fantástica." :activo "T"}
   {:id 5 :nombre "Mario" :apellidos "Vargas Llosa" :biografia "Escritor peruano, premio Nobel de Literatura 2010." :activo "T"}
   {:id 6 :nombre "Elena" :apellidos "Poniatowska" :biografia "Escritora y periodista mexicana de origen polaco." :activo "T"}])

(def categorias-rows
  [{:id 1 :nombre "Ficción" :descripcion "Novelas y cuentos de ficción literaria"}
   {:id 2 :nombre "Ciencia" :descripcion "Libros de divulgación científica y tecnología"}
   {:id 3 :nombre "Historia" :descripcion "Libros históricos y de no ficción"}
   {:id 4 :nombre "Poesía" :descripcion "Poesía y literatura lírica"}
   {:id 5 :nombre "Filosofía" :descripcion "Filosofía y ensayo"}])

(def libros-rows
  [{:id 1 :titulo "Cien Años de Soledad" :isbn "9788437604947" :categoria_id 1 :anio_publicacion 1967 :paginas 496 :sinopsis "La obra maestra de García Márquez que narra la historia de la familia Buendía en Macondo." :status "disponible"}
   {:id 2 :titulo "El Aleph" :isbn "9788420633557" :categoria_id 1 :anio_publicacion 1949 :paginas 192 :sinopsis "Colección de cuentos de Borges que exploran el infinito, el tiempo y la identidad." :status "disponible"}
   {:id 3 :titulo "La Casa de los Espíritus" :isbn "9788401320231" :categoria_id 1 :anio_publicacion 1982 :paginas 463 :sinopsis "La saga de la familia Trueba a través de cuatro generaciones." :status "prestado"}
   {:id 4 :titulo "Breve Historia del Tiempo" :isbn "9788474235489" :categoria_id 2 :anio_publicacion 1988 :paginas 256 :sinopsis "Stephen Hawking explica los misterios del universo para el público general." :status "disponible"}
   {:id 5 :titulo "La Guerra y la Paz" :isbn "9788420673843" :categoria_id 3 :anio_publicacion 1869 :paginas 1225 :sinopsis "La obra épica de Tolstói sobre la invasión napoleónica de Rusia." :status "disponible"}
   {:id 6 :titulo "Ficciones" :isbn "9788420633564" :categoria_id 1 :anio_publicacion 1944 :paginas 240 :sinopsis "La colección más famosa de cuentos de Borges." :status "disponible"}
   {:id 7 :titulo "El Principito" :isbn "9788478887191" :categoria_id 4 :anio_publicacion 1943 :paginas 96 :sinopsis "El clásico de Saint-Exupéry sobre un niño de otro planeta." :status "disponible"}
   {:id 8 :titulo "Sapiens" :isbn "9788499926223" :categoria_id 3 :anio_publicacion 2014 :paginas 496 :sinopsis "Una breve historia de la humanidad por Yuval Noah Harari." :status "prestado"}
   {:id 9 :titulo "La Ciudad y los Perros" :isbn "9788466353717" :categoria_id 1 :anio_publicacion 1963 :paginas 408 :sinopsis "La primera novela de Vargas Llosa ambientada en un colegio militar." :status "disponible"}
   {:id 10 :titulo "Cosmos" :isbn "9788408052337" :categoria_id 2 :anio_publicacion 1980 :paginas 384 :sinopsis "Carl Sagan explora el universo y nuestro lugar en él." :status "disponible"}])

(def libros-imagenes-rows
  [{:id 1 :libro_id 1 :imagen "libros_1_portada.png" :descripcion "Portada de Cien Años de Soledad"}
   {:id 2 :libro_id 1 :imagen "libros_1_contraportada.png" :descripcion "Contraportada"}
   {:id 3 :libro_id 1 :imagen "libros_1_ilustracion.png" :descripcion "Ilustración interior"}
   {:id 4 :libro_id 2 :imagen "libros_2_portada.png" :descripcion "Portada de El Aleph"}
   {:id 5 :libro_id 2 :imagen "libros_2_notas.png" :descripcion "Notas del autor"}
   {:id 6 :libro_id 3 :imagen "libros_3_portada.png" :descripcion "Portada de La Casa de los Espíritus"}
   {:id 7 :libro_id 4 :imagen "libros_4_portada.png" :descripcion "Portada de Breve Historia del Tiempo"}
   {:id 8 :libro_id 5 :imagen "libros_5_portada.png" :descripcion "Portada de La Guerra y la Paz"}
   {:id 9 :libro_id 5 :imagen "libros_5_edicion.png" :descripcion "Edición especial"}
   {:id 10 :libro_id 7 :imagen "libros_7_portada.png" :descripcion "Portada de El Principito"}
   {:id 11 :libro_id 7 :imagen "libros_7_dibujo.png" :descripcion "Dibujo del Principito"}
   {:id 12 :libro_id 7 :imagen "libros_7_rosa.png" :descripcion "La rosa del Principito"}
   {:id 13 :libro_id 10 :imagen "libros_10_portada.png" :descripcion "Portada de Cosmos"}
   {:id 14 :libro_id 10 :imagen "libros_10_galaxia.png" :descripcion "Imagen de galaxia"}])

(def libros-autores-rows
  [{:libro_id 1 :autor_id 1 :rol "Autor"}
   {:libro_id 2 :autor_id 4 :rol "Autor"}
   {:libro_id 3 :autor_id 2 :rol "Autor"}
   {:libro_id 4 :autor_id 5 :rol "Traductor"}  ;; Nota: Hawking no es VL, es un ejemplo de rol
   {:libro_id 5 :autor_id 5 :rol "Prólogo"}     ;; VL no escribió Guerra y Paz, ejemplo de rol
   {:libro_id 6 :autor_id 4 :rol "Autor"}
   {:libro_id 7 :autor_id 2 :rol "Traductor"}   ;; Allende no escribió El Principito, demo
   {:libro_id 8 :autor_id 1 :rol "Prólogo"}     ;; Demo
   {:libro_id 9 :autor_id 5 :rol "Autor"}
   {:libro_id 10 :autor_id 6 :rol "Traductor"}]) ;; Demo

(defn ^:private seed-year []
  (str (t/year (t/now))))

(defn miembros-rows []
  (let [y (seed-year)]
    [{:id 1 :nombre "Juan Pérez García" :email "juan@ejemplo.com" :telefono "555-1001" :activo "T" :fecha_registro (str y "-01-15")}
     {:id 2 :nombre "María López Hernández" :email "maria@ejemplo.com" :telefono "555-1002" :activo "T" :fecha_registro (str y "-02-20")}
     {:id 3 :nombre "Carlos Rodríguez Martínez" :email "carlos@ejemplo.com" :telefono "555-1003" :activo "T" :fecha_registro (str y "-03-10")}
     {:id 4 :nombre "Ana Sánchez Morales" :email "ana@ejemplo.com" :telefono "555-1004" :activo "T" :fecha_registro (str y "-04-05")}
     {:id 5 :nombre "Pedro Ramírez Torres" :email "pedro@ejemplo.com" :telefono "555-1005" :activo "F" :fecha_registro (str y "-01-30")}
     {:id 6 :nombre "Sofía Castillo Ortega" :email "sofia@ejemplo.com" :telefono "555-1006" :activo "T" :fecha_registro (str y "-05-12")}]))

(defn prestamos-rows []
  (let [y (seed-year)]
    [{:id 1 :miembro_id 1 :fecha_prestamo (str y "-06-01") :fecha_vencimiento (str y "-06-15") :status "devuelto" :fecha_devolucion (str y "-06-14") :notas "Primer préstamo"}
     {:id 2 :miembro_id 2 :fecha_prestamo (str y "-06-10") :fecha_vencimiento (str y "-06-24") :status "activo" :notas "Préstamo vigente"}
     {:id 3 :miembro_id 3 :fecha_prestamo (str y "-05-20") :fecha_vencimiento (str y "-06-03") :status "vencido" :notas "Libro no devuelto"}
     {:id 4 :miembro_id 4 :fecha_prestamo (str y "-06-15") :fecha_vencimiento (str y "-06-29") :status "activo" :notas nil}]))

(def prestamos-detalle-rows
  [{:id 1 :prestamo_id 1 :libro_id 3 :cantidad 1 :notas nil}
   {:id 2 :prestamo_id 2 :libro_id 1 :cantidad 1 :notas nil}
   {:id 3 :prestamo_id 2 :libro_id 6 :cantidad 1 :notas "Edición de bolsillo"}
   {:id 4 :prestamo_id 3 :libro_id 8 :cantidad 1 :notas "Préstamo vencido"}
   {:id 5 :prestamo_id 4 :libro_id 4 :cantidad 1 :notas nil}
   {:id 6 :prestamo_id 4 :libro_id 10 :cantidad 1 :notas nil}])

(defn audit-log-rows []
  (let [y (seed-year)]
    [{:id 1 :entity "libros" :operation "seed" :data "initial dataset" :user_id 1 :timestamp (str y "-01-01 10:00:00")}
     {:id 2 :entity "miembros" :operation "seed" :data "initial dataset" :user_id 1 :timestamp (str y "-01-01 10:01:00")}
     {:id 3 :entity "prestamos" :operation "seed" :data "initial dataset" :user_id 1 :timestamp (str y "-01-01 10:02:00")}]))

(defn ^:private non-users-seed-plan []
  [{:table "autores" :rows autores-rows}
   {:table "categorias" :rows categorias-rows}
   {:table "libros" :rows libros-rows}
   {:table "libros_imagenes" :rows libros-imagenes-rows}
   {:table "libros_autores" :rows libros-autores-rows}
   {:table "miembros" :rows (miembros-rows)}
   {:table "prestamos" :rows (prestamos-rows)}
   {:table "prestamos_detalle" :rows prestamos-detalle-rows}
   {:table "audit_log" :rows (audit-log-rows)}])

(def ^:private non-users-clear-order
  ["prestamos_detalle"
   "prestamos"
   "libros_imagenes"
   "libros_autores"
   "libros"
   "categorias"
   "autores"
   "miembros"
   "audit_log"])

(defn- normalize-token [s]
  (some-> s str st/trim (st/replace #"^:+" "") st/lower-case))

(def ^:private vendor->subprotocol
  {"mysql"     #(or (= % "mysql") (= % :mysql))
   "postgres"  #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "postgresql" #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "pg"        #(or (= % "postgresql") (= % :postgresql) (= % "postgres") (= % :postgres))
   "sqlite"    #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))
   "sqlite3"   #(or (= % "sqlite") (= % :sqlite) (= % "sqlite3") (= % :sqlite3))})

(defn- choose-conn-key
  "Resolve a user token (e.g., nil, pg, :pg, localdb, mysql) to a key in crud/dbs.
  Prefers exact connection keys (e.g., :pg, :localdb, :main, :default). Falls back to
  the first connection whose subprotocol matches a known vendor token. Defaults to :default."
  [token]
  (let [t (normalize-token token)
        dbs crud/dbs
        keys* (set (keys dbs))
        t->key {"default" :default
                "mysql"   :default
                "main"    :main
                "pg"      :pg
                "postgres" :pg
                "postgresql" :pg
                "local"   :localdb
                "localdb" :localdb
                "sqlite"  :localdb
                "sqlite3" :localdb}
        direct (when (seq t)
                 (some (fn [k] (when (= (name k) t) k)) keys*))
        mapped (get t->key t)
        by-vendor (when (seq t)
                    (let [pred (get vendor->subprotocol t)]
                      (when pred
                        (some (fn [[k v]] (when (pred (:subprotocol v)) k)) dbs))))]
    (or direct mapped by-vendor :default)))

(defn populate-tables
  "Populate a table with rows on the selected connection."
  [table rows & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))
        typed-rows (mapv (fn [row]
                           (crud/build-postvars table-s row :conn conn*))
                         rows)]
    (println (format "[database] Seeding %s on connection %s" table-s (name conn*)))
    (try
      (Query! (str "DELETE FROM " table-s) :conn conn*)
      (Insert-multi (keyword table-s) typed-rows :conn conn*)
      (println (format "[database] Seeded %d rows into %s (%s)"
                       (count typed-rows) table-s (name conn*)))
      (catch Exception e
        (println "[ERROR] Seeding failed for" table-s "on" (name conn*) ":" (.getMessage e))
        (throw e)))))

(defn- clear-table
  [table & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))]
    (Query! (str "DELETE FROM " table-s) :conn conn*)
    (println (format "[database] Cleared %s (%s)" table-s (name conn*)))))

(defn- insert-rows
  [table rows & {:keys [conn]}]
  (let [conn* (or conn :default)
        table-s (name (keyword table))
        typed-rows (mapv (fn [row]
                           (crud/build-postvars table-s row :conn conn*))
                         rows)]
    (when (seq typed-rows)
      (Insert-multi (keyword table-s) typed-rows :conn conn*)
      (println (format "[database] Seeded %d rows into %s (%s)"
                       (count typed-rows) table-s (name conn*))))))

(defn- generate-placeholder-image!
  [filepath & {:keys [red green blue]}]
  (let [img (java.awt.image.BufferedImage. 100 60 java.awt.image.BufferedImage/TYPE_INT_RGB)
        g   (.createGraphics img)
        c   (java.awt.Color. (or red 200) (or green 200) (or blue 200))]
    (.setColor g c)
    (.fillRect g 0 0 100 60)
    (.dispose g)
    (javax.imageio.ImageIO/write img "png" (java.io.File. filepath))))

(defn- generate-placeholder-pdf!
  [filepath]
  (let [f (java.io.File. filepath)]
    (io/make-parents f)
    (spit f (str "PDF placeholder for " (.getName f) "\nThis is a demo PDF file generated by contactos."))
    (println (format "[database]   Created placeholder: %s" (.getName f)))))

(defn- seed-placeholder-images!
  [rows table-name column-name]
  (let [uploads-dir (:uploads crud/config)]
    (doseq [row rows]
      (when-let [filename (get row column-name)]
        (let [filepath (str uploads-dir filename)
              id       (:id row)
              r        (mod (* id 73) 256)
              g        (mod (* id 149) 256)
              b        (mod (* id 97) 256)]
          (io/make-parents filepath)
          (generate-placeholder-image! filepath :red r :green g :blue b)
          (println (format "[database]   Created placeholder: %s" filename)))))))

(defn- seed-placeholder-pdfs!
  [rows table-name column-name]
  (let [uploads-dir (:uploads crud/config)]
    (doseq [row rows]
      (when-let [filename (get row column-name)]
        (let [filepath (str uploads-dir filename)]
          (io/make-parents filepath)
          (generate-placeholder-pdf! filepath))))))

(defn seed-non-users
  "Usage:
   - lein seed-non-users
   - lein seed-non-users pg
   - lein seed-non-users localdb

   Seeds all configured tables except users."
  [& args]
  (let [token (first args)
        conn  (choose-conn-key token)
        dbspec (get crud/dbs conn)
        sp (:subprotocol dbspec)]
    (println (format "[database] Seeding non-user tables on connection: %s (subprotocol=%s)" (name conn) sp))
    (doseq [table non-users-clear-order]
      (clear-table table :conn conn))
    (doseq [{:keys [table rows]} (non-users-seed-plan)]
      (insert-rows table rows :conn conn))
    (println "[database] Generating placeholder images for books...")
    (seed-placeholder-images! libros-rows "libros" :portada)
    (println "[database] Generating placeholder PDFs for books...")
    (seed-placeholder-pdfs! libros-rows "libros" :pdf)
    (seed-placeholder-pdfs! libros-rows "libros" :documento)
    (println "[database] Generating placeholder images for book gallery...")
    (seed-placeholder-images! libros-imagenes-rows "libros_imagenes" :imagen)
    (println "[database] Generating placeholder images for members...")
    (seed-placeholder-images! (miembros-rows) "miembros" :foto)
    (println "[database] Non-user seed completed.")))

(defn database
  "Usage:
   - lein database                 ; seeds default (mysql per config)
   - lein database pg              ; seeds Postgres (:pg)
   - lein database :pg             ; same as above
   - lein database localdb         ; seeds SQLite (:localdb)"
  [& args]
  (let [token (first args)
        conn  (choose-conn-key token)
        dbspec (get crud/dbs conn)
        sp (:subprotocol dbspec)]
    (println (format "[database] Using connection: %s (subprotocol=%s)" (name conn) sp))
    (populate-tables "users" users-rows :conn conn)
    (println "[database] Done.")))
