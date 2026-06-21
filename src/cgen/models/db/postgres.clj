(ns cgen.models.db.postgres)

(defn q-opts [] {})

(defn table-sql-name [tname]
  tname)

(defn time-projection [field]
  (str "to_char(" field ", 'HH24:MI') AS " field))

(defn describe-table [table query-fn]
  (let [sql (str
             "SELECT column_name as field, data_type as type, is_nullable as null, column_default as default, '' as extra, '' as privileges, '' as comment, '' as key "
             "FROM information_schema.columns "
             "WHERE table_name = ? "
             "AND table_schema = ANY (current_schemas(true)) "
             "ORDER BY ordinal_position")]
    (query-fn [sql table])))

(defn primary-keys-fallback [table query-fn]
  (let [sql (str "SELECT a.attname as field "
                 "FROM pg_index i "
                 "JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey) "
                 "WHERE i.indrelid = '" table "'::regclass AND i.indisprimary;")]
    (mapv :field (query-fn sql))))

(defn last-insert-id [_t-con]
  nil)

