(ns com.linkfluence.dns.api
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.dns.common :as common]
            [com.linkfluence.dns.record :as rec]
            [com.linkfluence.dns.zone :as zo]
            [com.linkfluence.utils :as u]))

;;dns mgt
(defroutes DNS
  (GET "/fsync" [] (do
                      (future
                        (rec/refresh-dns-records!)
                        (log/info "[FSYNC] received dns update notfication"))
                      (u/mk-resp 202 "success" {} "Fsync submitted")))
  (GET "/zones" [] (u/mk-resp 200 "success" {:data (zo/list-zones)}))
  (GET "/zone/exists/:zone" [zone] (if (zo/zone-exists? zone)
                                  (u/mk-resp 200 "success" {:zoneName zone :exists true})
                                  (u/mk-resp 404 "error" {:zoneName zone :exists false})))
  (PUT "/zone/create/:zonename" [zonename zone] (do
                                                  (zo/add-operation zonename "create" zone)
                                                  (u/mk-resp 201 "success" {} "DNS create zone operation submitted")))
  (DELETE "/zone/:zonename" [zonename] (if (zo/zone-exists? zonename)
                                            (do
                                                (zo/add-operation zonename "delete" {})
                                                (u/mk-resp 202 "success" {} "DNS delete zone operation submitted"))
                                                (u/mk-resp 404 "error" {:zoneName zonename :exists false})))
  (GET "/dbs" [] (u/mk-resp 200 "success" {:data (common/list-db)}))
  (GET "/db/exists/:db" [db] (if (common/db-exists? db)
                                  (u/mk-resp 200 "success" {:dbName db :exists true})
                                  (u/mk-resp 404 "error" {:dbName db :exists false})))
  (GET "/records/:db" [db] (if (or (common/db-exists? (zo/zodb->db db)))
                            (u/mk-resp 200 "success" {:data (rec/list-record db)})
                            (u/mk-resp 404 "error" {:dbName db :exists false})))
  (GET "/records/:db/sync" [db] (do (rec/add-operation db "sync" nil)
                                      (u/mk-resp 201 "success" {} "DNS Operation submitted")))
  (POST "/record/:db/create" [db record] (do (rec/add-operation db "add" record)
                                            (u/mk-resp 201 "success" {} "DNS create record operation submitted")))
  (POST "/records/:db/create" [db records] (do (rec/add-operations db "add" records)
                                            (u/mk-resp 201 "success" {} "DNS create records operation submitted")))
  (PUT "/record/:db/update" [db record] (do (rec/add-operation db "update" record)
                                            (u/mk-resp 201 "success" {} "DNS update operation submitted")))
  (PUT "/records/:db/update" [db records] (do (rec/add-operations db "update" records)
                                            (u/mk-resp 201 "success" {} "DNS update operation submitted")))
  (DELETE "/record/:db/delete" [db record] (do (rec/add-operation db "delete" record)
                                            (u/mk-resp 201 "success" {} "DNS Operation submitted"))))
