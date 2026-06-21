(ns cgen.hooks.libros
  (:require [clojure.string :as str]))

(defn- valid-isbn-13?
  "Validates ISBN-13 check digit."
  [isbn]
  (let [digits (->> isbn
                    (re-seq #"\d")
                    (mapv parse-long)
                    (remove nil?))]
    (when (= (count digits) 13)
      (let [sum (reduce + (map-indexed (fn [i d]
                                         (* d (if (even? i) 1 3)))
                                       digits))]
        (zero? (mod sum 10))))))

(defn- valid-isbn-10?
  "Validates ISBN-10 check digit."
  [isbn]
  (let [chars (re-seq #"[\dX]" (str/upper-case isbn))
        digits (mapv (fn [c]
                       (if (= c "X") 10 (parse-long c)))
                     chars)]
    (when (= (count digits) 10)
      (let [sum (reduce + (map-indexed (fn [i d] (* d (inc i))) digits))]
        (zero? (mod sum 11))))))

(defn- clean-isbn
  "Remove hyphens and spaces from ISBN."
  [isbn]
  (when isbn
    (str/replace isbn #"[-\s]" "")))

(defn before-save
  "Validates ISBN format before saving a book."
  [params]
  (if-let [isbn (:isbn params)]
    (let [clean (clean-isbn isbn)]
      (if (or (valid-isbn-13? clean) (valid-isbn-10? clean))
        (assoc params :isbn clean)
        (throw (ex-info "ISBN inválido. Debe ser un ISBN-10 o ISBN-13 válido."
                        {:field :isbn :value isbn}))))
    params))

(defn before-load [params] params)

(defn after-load [rows _params] rows)

(defn after-save [_entity-id _params]
  {:success true})

(defn before-delete [_entity-id]
  {:success true})

(defn after-delete [_entity-id]
  {:success true})
