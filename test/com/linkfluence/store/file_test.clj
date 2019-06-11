(ns com.linkfluence.store.file-test
  (:use [clojure.test])
  (:require [com.linkfluence.store.file :as file]
            [clojure.java.io :as io]))

(def test-path "store.txt")
(def test-bucket "/tmp")

(deftest put-get-del-test
  (file/put test-bucket test-path "my content")
  (is (= "my content" (file/get test-bucket test-path false)))
  (file/del test-bucket test-path)
  (is (= "" (file/get test-bucket test-path false))))
