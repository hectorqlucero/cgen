(ns cgen.image-handling-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [cgen.models.crud :as crud]))

(defn- capture-deleted-images [f]
  (let [atom (atom [])
        old-var (find-var 'cgen.models.crud/safe-delete-upload!)
        old-val (when old-var (var-get old-var))]
    (try
      (intern 'cgen.models.crud 'safe-delete-upload!
              (fn [img] (swap! atom conj img) true))
      (f atom)
      (finally
        (when old-var
          (intern 'cgen.models.crud 'safe-delete-upload! old-val))))))

(deftest apply-file-links-transforms-file-fields
  (let [rows [{:id 1 :name "Test" :imagen "contactos_1.jpg"}
              {:id 2 :name "Test2" :imagen "contactos_2.png"}]
        result (crud/apply-file-links rows [:imagen])]
    (is (= 2 (count result)))
    (is (re-find #"^<img" (:imagen (first result))))
    (is (re-find #"contactos_1.jpg" (:imagen (first result))))
    (is (re-find #"^<img" (:imagen (second result))))
    (is (re-find #"contactos_2.png" (:imagen (second result))))))

(deftest apply-file-links-handles-missing-fields
  (let [rows [{:id 1 :name "Test" :imagen "test.jpg"}
              {:id 2 :name "Test2"}]
        result (crud/apply-file-links rows [:imagen])]
    (is (= 2 (count result)))
    (is (re-find #"^<img" (:imagen (first result))))
    (is (nil? (:imagen (second result))))))

(deftest apply-file-links-handles-multiple-file-fields
  (let [rows [{:id 1 :imagen "a.jpg" :avatar "b.jpg"}]
        result (crud/apply-file-links rows [:imagen :avatar])]
    (is (re-find #"^<img" (:imagen (first result))))
    (is (re-find #"^<img" (:avatar (first result))))
    (is (re-find #"a.jpg" (:imagen (first result))))
    (is (re-find #"b.jpg" (:avatar (first result))))))

(deftest apply-file-links-empty-file-fields
  (let [rows [{:id 1 :imagen "test.jpg"}]
        result (crud/apply-file-links rows [])]
    (is (= rows result))))

(deftest apply-file-links-no-file-fields
  (let [rows [{:id 1 :name "Test"}]
        result (crud/apply-file-links rows [:imagen])]
    (is (= rows result))))

(deftest build-form-delete-deletes-multiple-file-fields
  (capture-deleted-images
   (fn [deleted-atom]
     (with-redefs [crud/select-row (fn [& _] {:id 1 :imagen "img.jpg" :avatar "av.jpg"})
                   crud/cascade-delete-images! (fn [& _] nil)
                   crud/perform-delete (fn [& _] [1])
                   crud/update-count' (fn [v] 1)
                   crud/Delete (fn [& _] [1])
                   crud/Query (fn [& _] [])]
       (is (crud/build-form-delete "test" 1 :conn :default :file-fields [:imagen :avatar]))
       (is (= #{"img.jpg" "av.jpg"} (set @deleted-atom)))))))

(deftest build-form-delete-no-default-file-fields
  (capture-deleted-images
   (fn [deleted-atom]
     (with-redefs [crud/select-row (fn [& _] {:id 1 :imagen "old.jpg" :avatar "av.jpg"})
                   crud/cascade-delete-images! (fn [& _] nil)
                   crud/perform-delete (fn [& _] [1])
                   crud/update-count' (fn [v] 1)
                   crud/Delete (fn [& _] [1])
                   crud/Query (fn [& _] [])]
       (is (crud/build-form-delete "test" 1))
       (is (empty? @deleted-atom))))))

(deftest build-form-delete-no-img-when-delete-fails
  (capture-deleted-images
   (fn [deleted-atom]
     (with-redefs [crud/select-row (fn [& _] {:id 1 :imagen "img.jpg"})
                   crud/cascade-delete-images! (fn [& _] nil)
                   crud/perform-delete (fn [& _] [0])
                   crud/update-count' (fn [v] 0)
                   crud/Delete (fn [& _] [0])
                   crud/Query (fn [& _] [])]
       (is (not (crud/build-form-delete "test" 1 :conn :default :file-fields [:imagen])))
       (is (empty? @deleted-atom))))))

(deftest crud-upload-file-naming-includes-column
  (let [mock-file {:tempfile (java.io.File. "/tmp/test.jpg")
                   :size 1024
                   :filename "photo.jpg"}]
    (with-redefs [io/copy (fn [src dest] nil)
                  io/make-parents (fn [f] nil)]
      (is (re-find #"^test_42_imagen\.jpg$"
                   (crud/crud-upload-file "test" mock-file "42" :imagen "/tmp/uploads/"))))))

(deftest crud-upload-file-naming-different-columns
  (let [mock-file {:tempfile (java.io.File. "/tmp/test.png")
                   :size 2048
                   :filename "pic.png"}]
    (with-redefs [io/copy (fn [src dest] nil)
                  io/make-parents (fn [f] nil)]
      (is (re-find #"^test_99_avatar\.png$"
                   (crud/crud-upload-file "test" mock-file "99" :avatar "/tmp/uploads/"))))))

(deftest crud-upload-file-naming-uses-ext-from-filename
  (let [mock-file {:tempfile (java.io.File. "/tmp/test.gif")
                   :size 512
                   :filename "logo.gif"}]
    (with-redefs [io/copy (fn [src dest] nil)
                  io/make-parents (fn [f] nil)]
      (is (re-find #"^test_7_imagen\.gif$"
                   (crud/crud-upload-file "test" mock-file "7" :imagen "/tmp/uploads/"))))))

(deftest crud-upload-file-rejects-no-extension
  (let [mock-file {:tempfile (java.io.File. "/tmp/test")
                   :size 1024
                   :filename "testfile"}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"must have an extension"
          (crud/crud-upload-file "t" mock-file "1" :f "/tmp/" :file)))))

(deftest crud-upload-file-rejects-wrong-extension
  (let [mock-file {:tempfile (java.io.File. "/tmp/test.exe")
                   :size 1024
                   :filename "virus.exe"}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not allowed"
          (crud/crud-upload-file "t" mock-file "1" :f "/tmp/" :file)))))

(deftest crud-upload-file-pdf-type-accepts-pdf
  (let [mock-file {:tempfile (java.io.File. "/tmp/doc.pdf")
                   :size 1024
                   :filename "doc.pdf"}]
    (with-redefs [io/copy (fn [src dest] nil)
                  io/make-parents (fn [f] nil)]
      (is (re-find #"^t_1_f\.pdf$"
                   (crud/crud-upload-file "t" mock-file "1" :f "/tmp/" :pdf))))))

(deftest crud-upload-file-pdf-type-rejects-image
  (let [mock-file {:tempfile (java.io.File. "/tmp/img.png")
                   :size 1024
                   :filename "img.png"}]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not allowed"
          (crud/crud-upload-file "t" mock-file "1" :f "/tmp/" :pdf)))))

(deftest crud-upload-file-document-type-accepts-docx
  (let [mock-file {:tempfile (java.io.File. "/tmp/report.docx")
                   :size 1024
                   :filename "report.docx"}]
    (with-redefs [io/copy (fn [src dest] nil)
                  io/make-parents (fn [f] nil)]
      (is (re-find #"^t_1_f\.docx$"
                   (crud/crud-upload-file "t" mock-file "1" :f "/tmp/" :document))))))

(deftest build-form-save-strips-zero-size-file-maps-before-regular
  (let [regular-params (atom nil)
        multi-called (atom nil)
        upload-entries (atom nil)]
    (with-redefs [cgen.models.crud/process-regular-form
                  (fn [params table & {:keys [conn]}]
                    (reset! regular-params params)
                    true)
                  cgen.engine.config/get-entity-config (fn [& _] nil)
                  cgen.models.crud/process-multi-upload-form
                  (fn [params table entries & {:keys [conn]}]
                    (reset! multi-called true)
                    (reset! upload-entries entries)
                    {:success true})
                  cgen.models.crud/process-upload-form
                  (fn [& _] {:success true})]
      (let [file-map {:tempfile (java.io.File. "/tmp/x") :size 0 :filename ""}
            file-map2 {:tempfile (java.io.File. "/tmp/y") :size 0 :filename ""}]

        ;; Case 1: single 0-size file upload → falls to process-regular-form with file map stripped
        (testing "single zero-size file upload strips file map from params"
          (crud/build-form-save {:id "1" :name "John" :imagen file-map} "test" :conn :default)
          (is (some? @regular-params) "should reach process-regular-form")
          (is (not (contains? @regular-params :imagen)) "file map key should be stripped")
          (is (= "John" (:name @regular-params)) "non-file params preserved"))

        ;; Case 2: two 0-size file uploads → multi-upload path
        (reset! regular-params nil)
        (reset! multi-called nil)
        (testing "two zero-size file uploads take multi-upload path"
          (crud/build-form-save {:id "2" :name "Jane" :imagen file-map :avatar file-map2} "test" :conn :default)
          (is @multi-called "multi-upload path should be taken")
          (is (= 2 (count @upload-entries)) "both uploads should be in entries"))))))
