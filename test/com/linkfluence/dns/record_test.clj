(ns com.linkfluence.dns.record-test
  (:use [clojure.test])
  (:require [com.linkfluence.dns.common :as common]
            [com.linkfluence.dns.record :as record]))

(def conf-test {:sudo false
                :bind-dir "/tmp"
                :store {:file {:bucket "/tmp/store"}
                        :key "dns"}
                :restart false})

(common/configure! conf-test)

(deftest record-specs
  (is (record/check-record  {:type "A" :name "www" :ttl "300" :value "1.2.3.4"})))

(deftest record-exist
    (reset! record/test true)
    (record/update-cache! "db.example.com" "add" {:type "A" :name "www" :ttl "300" :value "1.2.3.4"})
    (is (= (record/exists? "db.example.com" {:type "A" :name "www" :ttl "300" :value "1.2.3.4"}) {:exists true :equals true :conflict false}))
    (is (= (record/exists? "db.example.com" {:type "CNAME" :name "www" :ttl "300" :value "gw"}) {:exists false :equals false :conflict true}))
    (record/update-cache! "db.example.com" "delete" {:type "A" :name "www" :ttl "300" :value "1.2.3.4"})
    (is (= (record/exists? "db.example.com" {:type "A" :name "www" :ttl "300" :value "1.2.3.4"}) {:exists false :equals false :conflict false})))
