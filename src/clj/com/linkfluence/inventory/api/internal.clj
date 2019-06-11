(ns com.linkfluence.inventory.api.internal
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.internal :as internal]
            [com.linkfluence.utils :as u]))

(defroutes INTERNAL
  (GET "/fsync" [] (do
                      (future
                        (internal/load-inventory!)
                        (log/info "[FSYNC] received internal update notfication"))
                      (u/mk-resp 202 "success" {} "Fsync submitted")))
  (GET "/resource" [] (u/mk-resp 200 "success" {:data (internal/get-inventory)}))
  (POST "/search/resource" [tag] (if-not (nil? tag)
                                    (u/mk-resp 200 "success" {:data (internal/search-resource tag)})
                                    (u/mk-resp 400 "error" {} "no tag send")))
  (GET "/resource/:id" [id] (if-let [res (internal/get-resource id)]
                              (u/mk-resp 200 "success" {:data res})
                              (u/mk-resp 404 "error" {} "Resource not found")))
  (POST "/resource" [resource] (do
                                  (internal/add-inventory-event ["register" resource])
                                  (u/mk-resp 202 "success" {} "Create operation submitted")))
  (PUT "/resource/:id" [id privateIp privateReverse publicIp publicReverse
                        memTotal privateMacAddress publicMacAddress datacenter availabilityZone meta]
                          (if-let [res (internal/get-resource id)]
                              (do
                                (internal/add-inventory-event
                                  ["update" {:resourceId id
                                             :privateIp privateIp
                                             :privateReverse  privateReverse
                                             :publicMacAddress publicMacAddress
                                             :publicIp publicIp
                                             :publicReverse publicReverse
                                             :memTotal memTotal
                                             :privateMacAddress privateMacAddress
                                             :datacenter datacenter
                                             :availabilityZone availabilityZone
                                             :meta meta}])
                                (u/mk-resp 202 "success" {} "Update operation submitted"))
                              (u/mk-resp 404 "error" {} "Resource not found")))
  (DELETE "/resource/:id" [id] (if-let [res (internal/get-resource id)]
                                  (do
                                    (internal/add-inventory-event ["delete" {:uid id}])
                                    (u/mk-resp 200 "success" {} "Delete operation Submitted"))
                                  (u/mk-resp 404 "error" {} "Resource not found")))                          )
