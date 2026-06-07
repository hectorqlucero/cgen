(ns cgen.models.export
  (:require
   [clojure.data.csv :as csv]
   [clj-pdf.core :refer [pdf]]))

(defn rows->csv
  "Converts rows and fields (array-map) to a CSV string.
   fields maps field-keywords to column labels."
  [rows fields]
  (let [header (mapv val fields)
        rows-data (mapv (fn [row]
                          (mapv #(str (get row (key %) "")) fields))
                        rows)
        all-rows (into [header] rows-data)
        sw (java.io.StringWriter.)]
    (csv/write-csv sw all-rows)
    (.toString sw)))

(defn rows->pdf
  "Converts rows and fields (array-map) to a PDF byte array.
   title is the document title."
  [title rows fields]
  (let [header (mapv val fields)
        rows-data (mapv (fn [row]
                          (mapv #(str (get row (key %) "")) fields))
                        rows)
        table-data (into [header] rows-data)
        table-opts {:header-color [0 102 204]
                    :font-size 8
                    :cell-padding 2}
        baos (java.io.ByteArrayOutputStream.)]
    (pdf
     [{:title title
       :size :letter
       :orientation :landscape}
      (into [:table table-opts] table-data)]
     baos)
    (.toByteArray baos)))
