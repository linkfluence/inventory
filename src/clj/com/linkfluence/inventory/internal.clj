(ns com.linkfluence.inventory.internal
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.queue :as queue :refer [init-queue put tke]]))

;@author Jean-Baptiste Besselat
;@Copyright Linkfluence SAS 2018
;@Description : provider/handler for self hosted server

;Resource structure
;{
; :cpuName
; :memTotal
; :privateIp
; :privateMacAddress
; :privateReverse
; :publicIp
; :publicMacAddress
; :publicReverse
; :datacenter (where the serveur is)
; :availabilityZone (short az equivalent)
; :type (vm, container, baremetal)
; :meta (add what you want there)
;}
(def internal-conf (atom {}))

(defn ro?
  []
  (:read-only @internal-conf))

(def internal-inventory (atom {}))

(def internal-queue (atom nil))

(def last-save (atom (System/currentTimeMillis)))
(def items-not-saved (atom 0))

(defn new-uid
  [provider]
  (let [base-id (if (and
                        (not (nil? provider))
                        (<= (count provider) 5))
                    (str/lower-case provider)
                    "r")]
    (loop [uid (u/random-string 16)]
    (if (nil? ((keyword (str base-id "-" uid)) @internal-inventory))
      (str base-id "-" uid)
      (recur (u/random-string 16))))))

;;return queue size
(defn get-event-queue-size
  []
  (.size internal-queue))

;;add event Update
(defn add-inventory-event
  [ev]
  (when-not (ro?)
  (.put internal-queue ev)))

(defn load-inventory!
  []
  (when (:store @internal-conf)
    (when-let [inventory (store/load-map (:store @internal-conf))]
        (reset! internal-inventory inventory))))

(defn save-inventory
  "Save on both local file and s3"
  []
  (when-not (ro?)
    (if (u/save? last-save items-not-saved)
    (when (:store @internal-conf)
        (store/save-map (:store @internal-conf) @internal-inventory)
        (u/reset-save! last-save items-not-saved)
        (u/fsync "internal"))
    (swap! items-not-saved inc))))

(defn remove-empty-tags
    [tags]
    (into [] (remove (fn [x] (nil? (:value x))) tags)))

(defn- send-tags-request
  [uid desc state]
  (inventory/add-inventory-event {:type "resource"
                                  :provider (or (:provider desc) (:company @internal-conf))
                                  :id uid
                                  :tags (remove-empty-tags
                                        [{:name "provider" :value (or (:provider desc) (:company @internal-conf))}
                                         {:name "AZ" :value (when (:datacenter desc) (str (:datacenter desc) (:availabilityZone desc)))}
                                         {:name "SHORT_AZ" :value (:availabilityZone desc)}
                                         {:name "REGION" :value (:datacenter desc)}
                                         {:name "publicIp" :value (:publicIp desc)}
                                         {:name "privateIp" :value (:privateIp desc)}
                                         {:name "state" :value state}])}))

(defn- delete-resource!
  [uid]
  (log/info "[DELETE] internal resource " uid " deleted")
  ;;send delete update to main inventory
  (inventory/add-inventory-event {:type "resource"
                                  :provider (:provider (get @internal-inventory (keyword uid)))
                                  :id uid
                                  :tags []
                                  :delete true})
  ;;save inventory
  (swap! internal-inventory dissoc (keyword uid))
  (save-inventory))

(defn- update-resource!
  [resource]
  (let [resource-update (into {} (remove (fn [[k v]] (nil? v)) resource))
        {:keys [resourceId privateIp privateReverse publicIp publicReverse memTotal publicMacAddress privateMacAddress datacenter]} resource-update]
  (when-not (and (nil? resourceId) (nil? (get-in @internal-inventory (keyword resourceId) nil)))
    (let [updated-resource (merge-with
                            (fn [r l] (if (nil? l) r l))
                            (get @internal-inventory (keyword resourceId) {})
                                resource-update)]
    (swap!
      internal-inventory
      assoc
        (keyword resourceId)
        updated-resource)
      (send-tags-request resourceId updated-resource nil)
      (save-inventory)))))

(defn- register-resource!
  [{:keys [privateIp
           privateMacAddress
           privateReverse
           publicIp
           publicMacAddress
           publicReverse
           cpuName
           memTotal
           datacenter
           availabilityZone
           type
           provider
           meta]
    ;;DEfault value
    :or {provider (:company @internal-conf)
         datacenter "internal"
         availabilityZone "1"
         cpuName "generic-CPU"
         memTotal "N/A"
         meta {}
         type "baremetal"
         privateReverce "N/A"
         publicReverse "N/A"
         publicMacAddress "N/A"
         privateMacAddress "N/A"}
    :as resource}]
  (let [uid (new-uid provider)
        res {:privateIp privateIp
             :privateReverse privateReverse
             :privateMacAddress privateMacAddress
             :publicReverse publicReverse
             :publicIp publicIp
             :publicMacAddress publicMacAddress
             :cpuName cpuName
             :memTotal memTotal
             :datacenter datacenter
             :availabilityZone availabilityZone
             :type type
             :meta meta
             :provider provider
             :resourceId uid}]
      (swap! internal-inventory assoc (keyword uid) res)
      (send-tags-request uid res "provisioned")
      (save-inventory)))

(defn execute-operation!
  [[op args]]
  (condp = op
    "register" (register-resource! args)
    "delete" (delete-resource! (:uid args))
    "update" (update-resource! args)
    (log/info "unknow action for ovh handler")))

(defn get-inventory
  "Retrieve a list of filtered internal resource or not"
  []
    (into [] (map (fn [[k v]] k) @internal-inventory)))

(defn get-resource
  "Retrieve a list of filtered internal resource or not"
  [uid]
    (get @internal-inventory (keyword uid) nil))

(defn search-resource
    "Search resource with a tag"
    [tag]
    (reduce (fn [_ [k v]]
                (when (= ((keyword (:name tag)) v) (:value tag))
                    (reduced (name k))))
            nil
            @internal-inventory))

(defn- start-op-consumer!
  "consum install queue"
  []
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [ev (.take internal-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (execute-operation! ev)))
      "internal consumer"))

(defn start!
  []
  (if-not (nil? @internal-conf)
    [(start-op-consumer!)]
    []))

(defn configure!
  [conf]
  (reset! internal-conf conf)
  (load-inventory!))
