(ns cgen.db-vendor-test
  (:require [clojure.test :refer [deftest is testing]]
            [cgen.models.db :as db]
            [clojure.string :as str]
            [clojure.java.jdbc :as j]))

(def mysql-spec {:subprotocol "mysql"})
(def pg-spec    {:subprotocol "postgresql"})
(def sqlite-spec {:subprotocol "sqlite"})

(deftest describe-table-mysql-call-test
  (testing "mysql describe-table delegates and passes DESCRIBE SQL"
    (let [captured (atom nil)
          rows     [{:field "id" :key "PRI"}]
          qfn      (fn [sql]
                     (reset! captured sql)
                     rows)
          out      (db/describe-table mysql-spec "people" qfn)]
      (is (= rows out))
      (is (= (str "DESCRIBE " "people") @captured)))))

(deftest describe-table-postgres-call-test
  (testing "postgres describe-table delegates and uses parameter vector"
    (let [captured (atom nil)
          rows     [{:field "id"}]
          qfn      (fn [arg]
                     (reset! captured arg)
                     rows)
          out      (db/describe-table pg-spec "people" qfn)]
      (is (= rows out))
      (is (vector? @captured))
      (is (= "people" (second @captured)))
      (is (str/includes? (first @captured) "information_schema.columns")))))

(deftest last-insert-id-tests
  (testing "last-insert-id for mysql and sqlite via jdbc stub"
    (with-redefs [j/query (fn [_ _] [{:id 101}])]
      (is (= 101 (db/last-insert-id {} mysql-spec)))
      (is (= 101 (db/last-insert-id {} sqlite-spec))))
    (testing "postgres returns nil"
      (is (nil? (db/last-insert-id {} pg-spec))))))


