(ns com.linkfluence.inventory.api.inventory
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.utils :as u]))

(defn put-events
  ([evs]
    (put-events evs "Operation submitted"))
  ([evs msg]
    (if-not (inventory/ro?)
      (do
        (doseq [ev evs]
          (inventory/add-inventory-event ev))
        (u/mk-resp 202 "success" {} msg))
      (u/mk-resp 403 "error" {} "Read Only"))))

(defn put-event
  ([ev]
    (put-event ev "Operation submitted"))
  ([ev msg]
    (if-not (inventory/ro?)
      (do
        (inventory/add-inventory-event ev)
        (u/mk-resp 202 "success" {} msg))
      (u/mk-resp 403 "error" {} "Read Only"))))

(defn hide-event
  [id hide type]
    (if-not (inventory/ro?)
      (if-let [resource (inventory/get-resource (keyword id) true)]
        (do
          (inventory/hide-event id hide type)
          (u/mk-resp 202 "success" {} "hidden flag updated"))
      (u/mk-resp 404 "error" {} "Resource not found"))
      (u/mk-resp 403 "error" {} "Read Only")))

(defroutes INVENTORY
  (GET "/fsync" [] (do
                      (future
                        (inventory/load-inventory)
                        (log/info "[FSYNC] received inventory update notfication"))
                      (u/mk-resp 202 "success" {} "Fsync submitted")))
  (GET "/event" [] (u/mk-resp 200 "success" {:data {:size (inventory/get-event-queue-size) }}))
  (POST "/event" [event] (put-event event))
  (POST "/events" [events] (put-events events))

  ;;group
  (GET "/group" [] (u/mk-resp 200 "success" {:data (inventory/list-groups)}))
  (GET "/group/:id" [id] (if-let [group (inventory/get-group (keyword id))]
                            (u/mk-resp 200 "success" {:data group})
                            (u/mk-resp 404 "error" {} "Group not found")))
  (DELETE "/group/:id" [id] (if-let [group (inventory/get-group (keyword id))]
                              (put-event {:type "group" :id (keyword id) :tags [] :delete true} "Deletion submitted")
                              (u/mk-resp 404 "error" {} "Group not found, deletion aborted")))
  (POST "/group/:id/addtags" [id tags] (if (inventory/validate-tags tags)
                                        (put-event {:type "group" :id (keyword id) :tags tags})
                                        (u/mk-resp 400 "error" {:explanation (inventory/explain-tags tags)} "tag array is invalid")))
  (POST "/group/:id/addresources" [id resources]  (do (inventory/add-inventory-event {:type "group" :id (keyword id) :resources resources})
                                            (u/mk-resp 200 "success" {} "Operation submitted")))
  ;;resource
  (GET "/resource" [with-alias] (u/mk-resp 200 "success" {:data (inventory/get-resources [] with-alias)}))
  (GET "/alias" [] (u/mk-resp 200 "success" {:data (inventory/get-aliases [])}))
  (DELETE "/resource/:id" [id] (put-event {:type "resource" :id (keyword id) :delete true} "Deletion submitted"))
  (DELETE "/alias/:id" [id] (put-event {:type "alias" :id (keyword id) :delete true} "Deletion submitted"))
  (POST "/resource" [tags with-alias] (u/mk-resp 200 "success" {:data (inventory/get-resources tags with-alias)}))
  (POST "/alias" [tags] (u/mk-resp 200 "success" {:data (inventory/get-aliases tags)}))
  (POST "/exists/alias" [resource-id from-resource tags] (u/mk-resp 200 "success" {:data (inventory/alias-exists? resource-id from-resource tags)}))
  (GET "/resource/:id" [id with-alias] (if-let [resource (inventory/get-resource (keyword id) with-alias)]
                              (u/mk-resp 200 "success" {:data resource})
                              (u/mk-resp 404 "error" {} "Resource not found")))
  (GET "/resource/:id/aliases" [id] (u/mk-resp 200 "success" {:data (inventory/get-resource-aliases (keyword id))}))
  (GET "/alias/:id" [id] (if-let [alias (inventory/get-alias (keyword id))]
                              (u/mk-resp 200 "success" {:data alias})
                              (u/mk-resp 404 "error" {} "Resource not found")))
  (POST "/resource/:id/addtags" [id tags] (if (inventory/validate-tags tags)
                                            (put-event {:type "resource" :id (keyword id) :tags tags})
                                            (u/mk-resp 400 "error" {:explanation (inventory/explain-tags tags)} "tag array is invalid")))
  (POST "/alias/:id/addtags" [id tags] (if (inventory/validate-tags tags)
                                        (put-event {:type "alias" :id (keyword id) :tags tags})
                                        (u/mk-resp 400 "error" {:explanation (inventory/explain-tags tags)} "tag array is invalid")))
  ;;Resource hiding
  (GET "/hidden/resource" [] (u/mk-resp 200 "success" {:data (inventory/get-hidden-resources)}))
  (POST "/hidden/resource" [tags] (u/mk-resp 200 "success" {:data (inventory/get-hidden-resources tags)}))
  (POST "/hide/resource/:id" [id] (hide-event id true "resource"))
  (POST "/unhide/resource/:id" [id] (hide-event id false "resource"))
  ;;Resource hiding
  (GET "/hidden/alias" [] (u/mk-resp 200 "success" {:data (inventory/get-hidden-aliases)}))
  (POST "/hidden/alias" [tags] (u/mk-resp 200 "success" {:data (inventory/get-hidden-aliases tags)}))
  (POST "/hide/alias/:id" [id] (hide-event id true "alias"))
  (POST "/unhide/alias/:id" [id] (hide-event id false "alias"))
  ;;aggregation
  (POST "/agg/tag/resource" [tags with-alias] (u/mk-resp 200 "success" {:data (inventory/get-aggregated-resources tags with-alias)}))
  (POST "/agg/tag/resource/:tag" [tag tags with-alias] (u/mk-resp 200 "success" {:data (inventory/get-tag-value-from-aggregated-resources tag tags with-alias)}))
  ;;tag
  (GET "/tag/resource" [] (u/mk-resp 200 "success" {:data (inventory/get-resource-tags-list)}))
  (GET "/tag/resource/:id" [id] (u/mk-resp 200 "success" {:data (inventory/get-tag-value-from-resources id)}))
  (POST "/tag/resource/:id" [id tags with-alias] (u/mk-resp 200 "success" {:data (inventory/get-tag-value-from-resources id tags with-alias)}))
  (GET "/tag/group/:id" [id] (u/mk-resp 200 "success" {:data (inventory/get-tag-value-from-groups id)}))

  ;;count
  (GET "/count/resource" [] (u/mk-resp 200 "success" {:data {:count (inventory/count-resources)}}))
  (POST "/count/resource" [tags] (u/mk-resp 200 "success" {:data {:count (inventory/count-resources tags)}}))
  (GET "/count/group" [] (u/mk-resp 200 "success" {:data {:count (inventory/count-groups)}}))
  (POST "/count/group" [tags] (u/mk-resp 200 "success" {:data {:count (inventory/count-groups tags)}}))
  (GET "/stats/tag/resource/:tag" [tag] (u/mk-resp 200 "success" {:data (inventory/get-tags-stats-from-resources tag)}))
  (POST "/stats/tag/resource/:tag" [tag tags] (u/mk-resp 200 "success" {:data (inventory/get-tags-stats-from-resources tag tags)}))
  (GET "/save" [] (if-not (inventory/ro?)
                    (do (inventory/save-inventory)
                      (u/mk-resp 200 "success" {} "Operation submitted"))
                      (u/mk-resp 403 "error" {} "Read Only")))
  (POST "/new/group" [name tags] (put-event {:type "group" :id (keyword name) :tags tags}))
  (POST "/new/alias" [resource-id tags from-resource create-only] (put-event {:type "alias"
                                                                  :resource-id (keyword resource-id)
                                                                  :create-only (if-not (nil? create-only) create-only false)
                                                                  :create true
                                                                  :tags tags
                                                                  :from-resource from-resource})))
