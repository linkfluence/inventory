(ns com.linkfluence.inventory.api.ovh
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.ovh.server :as server]
            [com.linkfluence.inventory.ovh.cloud :as cloud]
            [com.linkfluence.utils :as u]))


;;ovh resource mgt
(defroutes OVH
  (GET "/fsync" [] (if (server/ro?)
                    (do
                      (future
                        (server/load-inventory!)
                        (log/info "[FSYNC] received ovh update notification"))
                      (u/mk-resp 202 "success" {} "Operation submitted"))
                      (u/mk-resp 403 "error" {} "Not read only, sync forbidden")))
  (GET "/cloud/fsync" [] (if (cloud/ro?)
                    (do
                      (future
                        (cloud/load-inventory!)
                        (log/info "[FSYNC] received ovh-cloud update notification"))
                      (u/mk-resp 202 "success" {} "Operation submitted"))
                      (u/mk-resp 403 "error" {} "Not read only, sync forbidden")))
  (GET "/server-id-with-ns/:server-ns" [server-ns] (u/mk-resp 200 "success" {:data (server/get-server-id-with-ns server-ns)}))
  (GET "/server" [] (u/mk-resp 200 "success" {:data (server/get-ovh-inventory)}))
  (GET "/server/:id" [id] (if-let [server (server/get-server (keyword id))]
                            (u/mk-resp 200 "success" {:data server})
                            (u/mk-resp 404 "error" {} "Server not found")))
  (POST "/server/:id/install" [id] (if-not (server/ro?)
                                      (do (server/reinstall id)
                                        (u/mk-resp 200 "success" {} "Operation submitted"))
                                      (u/mk-resp 403 "error" {} "Read Only !")))
  (GET "/server/:id/install/finish" [id] (if-not (server/ro?)
                                          (do (server/end-setup id)
                                            (u/mk-resp 200 "success" {} "Operation submitted"))
                                          (u/mk-resp 403 "error" {} "Read Only !")))
  (POST "/server/:id/reboot" [id] (if-not (server/ro?)
                                    (do (server/reboot id)
                                      (u/mk-resp 200 "success" {} "Operation submitted"))
                                      (u/mk-resp 403 "error" {} "Read Only !")))
  (POST "/server/:id/reverse" [id reverse] (if-not (server/ro?)
                                        (do (server/update-reverse id reverse)
                                          (u/mk-resp 200 "success" {} "Operation submitted"))
                                        (u/mk-resp 403 "error" {} "Read Only !")))
  (GET "/cloud/instance" [] (u/mk-resp 200 "success" {:data (cloud/get-ovh-inventory)}))
  (GET "/cloud/instance/:id" [id] (u/mk-resp 200 "success" {:data (cloud/get-instance id)}))
  (PUT "/cloud/instance/:id" [id name] (if-not (server/ro?)
                                      (do (cloud/rename id name)
                                        (u/mk-resp 200 "success" {} "Operation submitted"))
                                      (u/mk-resp 403 "error" {} "Read Only !")))
  (POST "/cloud/instance/:id/bootstrap" [id] (if-not (cloud/ro?)
                                    (do (cloud/bootstrap id)
                                      (u/mk-resp 200 "success" {} "Operation submitted"))
                                      (u/mk-resp 403 "error" {} "Read Only !")))
  (POST "/cloud/instance/:id/reboot" [id] (if-not (cloud/ro?)
                                    (do (cloud/reboot id)
                                      (u/mk-resp 200 "success" {} "Operation submitted"))
                                      (u/mk-resp 403 "error" {} "Read Only !"))))
