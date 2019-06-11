(ns com.linkfluence.inventory.internal-test
  (:use [clojure.test])
  (:require [com.linkfluence.inventory.internal :as internal]
            [com.linkfluence.inventory.core :as inventory]))



(def resource {:privateIp "192.168.0.1"
               :privateReverse "gw.private.ns"
               :privateMacAddress "00:00:00:00:00:01"
               :publicReverse "gw.public.ns"
               :publicIp "192.0.0.1"
               :publicMacAddress "00:00:00:00:00:02"
               :cpuName "generic-cpu"
               :memTotal "32GB"
               :datacenter "my_dc"
               :availabilityZone "my_az"
               :type "baremetal"
               :provider "test"
               :meta {:setupDate "20180101"}})

(def resource-update {:publicIp "192.0.0.2" :datacenter "my_2nd_dc"})

(defn get-resource-id
    []
    (first (internal/get-inventory)))

(deftest resource-lifecycle
    (is (nil? (get-resource-id)))
    (swap! inventory/conf assoc :read-only true)
    (#'internal/register-resource! resource)
    (is (not (nil? (get-resource-id))))
    (is (= resource (dissoc (internal/get-resource (get-resource-id)) :resourceId)))
    (#'internal/update-resource! (assoc resource-update :resourceId (get-resource-id)))
    (is (= (merge resource resource-update) (dissoc (internal/get-resource (get-resource-id)) :resourceId)))
    (is (= (internal/search-resource {:name "privateIp" :value "192.168.0.1"}) (name (get-resource-id))))
    (#'internal/delete-resource! (get-resource-id))
    (is (nil? (get-resource-id))))
