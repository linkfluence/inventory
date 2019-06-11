(ns com.linkfluence.store.s3-test
  (:use [clojure.test])
  (:require [com.linkfluence.store.s3 :as s3]
            [clojure.java.io :as io]))

(def test-path "store.txt")
(def test-bucket "linkfluence.test")
(reset! com.linkfluence.store.s3/conf {})

(deftest put-get-del-test
  (s3/put test-bucket test-path "my content")
  (is (= "my content" (s3/get test-bucket test-path false)))
  (s3/del test-bucket test-path)
  (is (= "" (s3/get test-bucket test-path false))))
