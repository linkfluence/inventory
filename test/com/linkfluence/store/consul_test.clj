(ns com.linkfluence.store.consul-test
  (:use [clojure.test])
  (:require [com.linkfluence.store.consul :as consul]))

(def test-path "store.txt")
(def test-bucket "/tmp")
(def map-test {:foo ["bar" "baz" {:foo "bar"}]})
(reset! com.linkfluence.store.consul/conf {:hosts ["172.17.0.2"]})

;;this test need consul dev docker to be running :
;;docker run -d --name=dev-consul -e CONSUL_BIND_INTERFACE=eth0 consul

(deftest put-get-del-test
;;string content
  (consul/put test-bucket test-path "my content")
  (is (= "my content" (consul/get test-bucket test-path false)))
  (consul/del test-bucket test-path)
  (is (= "" (consul/get test-bucket test-path false)))
;;map content
  (consul/put test-bucket test-path map-test)
  (is (= map-test (consul/get test-bucket test-path false)))
  (consul/del test-bucket test-path)
  (is (= "" (consul/get test-bucket test-path false))))
