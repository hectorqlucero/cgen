(ns cgen.db-test
  (:require [clojure.test :refer [deftest is testing]]
            [cgen.models.db :as db]))

(def mysql-spec {:subprotocol "mysql"})
(def pg-spec    {:subprotocol "postgresql"})
(def sqlite-spec {:subprotocol "sqlite"})

(deftest time-projection-test
  (testing "formats time fields per vendor"
    (is (= "TIME_FORMAT(start_time, '%H:%i') AS start_time"
           (db/time-projection mysql-spec "start_time" "TIME")))
    (is (= "to_char(start_time, 'HH24:MI') AS start_time"
           (db/time-projection pg-spec "start_time" "time")))
    (is (= "strftime('%H:%M', start_time) AS start_time"
           (db/time-projection sqlite-spec "start_time" "time")))
    (testing "timestamp types are not treated as time"
      (is (= "created_at" (db/time-projection mysql-spec "created_at" "timestamp"))))))

(deftest q-opts-test
  (testing "mysql has entities quoting; others don't"
    (is (contains? (db/q-opts mysql-spec) :entities))
    (is (= {} (db/q-opts pg-spec)))
    (is (= {} (db/q-opts sqlite-spec)))))

(deftest describe-table-sqlite-mapping-test
  (testing "sqlite PRAGMA rows are mapped to describe format"
    (let [rows [{:name "id" :type "INTEGER" :notnull 1 :dflt_value nil :pk 1}
                {:name "name" :type "TEXT" :notnull 0 :dflt_value nil :pk 0}]
          qfn (fn [_] rows)
          desc (db/describe-table sqlite-spec "people" qfn)]
      (is (= [{:field "id" :type "INTEGER" :null "NO" :default nil :extra "" :privileges "" :comment "" :key "PRI"}
              {:field "name" :type "TEXT" :null "YES" :default nil :extra "" :privileges "" :comment "" :key nil}]
             (vec desc))))))

(deftest primary-keys-tests
  (testing "pks from describe :key present (mysql-style)"
    (let [desc [{:field "id" :key "PRI"} {:field "name"}]
          ks (db/primary-keys mysql-spec "people" desc (fn [_] []))]
      (is (= ["id"] ks))))
  (testing "pks from postgres pg_index when describe lacks key"
    (let [desc [{:field "id"} {:field "name"}]
          qfn (fn [_]
                [{:field "id"}])
          ks (db/primary-keys pg-spec "people" desc qfn)]
      (is (= ["id"] ks))))
  (testing "no pks when none present and non-pg vendor"
    (let [desc [{:field "id"} {:field "name"}]
          ks (db/primary-keys sqlite-spec "people" desc (fn [_] []))]
      (is (= [] ks)))))


