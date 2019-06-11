(ns com.linkfluence.inventory.lsw-test
  (:use [clojure.test])
  (:require [com.linkfluence.inventory.leaseweb :as lsw]
            [com.linkfluence.inventory.core :as inventory]))

(def server (load-string (slurp (clojure.java.io/resource "test/server.clj"))))

(def mock-apiserver (assoc-in
                        (assoc
                        server
                        :id "7654321")
                        [:contract :id]
                        "654321"))

(deftest lsw-refresh-id
    (swap! lsw/test not)
    (swap! lsw/lsw-inventory assoc (keyword (:id (:contract server))) server)
    (is (not (nil? (lsw/get-server (keyword (:id (:contract server)))))))
    (is (= (lsw/get-server-inventory-id-with-ref (:reference (:contract server))) (keyword (:id (:contract server)))))
    (#'lsw/refresh-server-ids mock-apiserver)
    (is (not (nil? (lsw/get-server (keyword "7654321")))))
    (is (#'lsw/cached? "7654321"))
    (is (nil? (lsw/get-server (keyword (:id (:contract server))))))
    (is (#'lsw/cached? "7654321")))
