(ns com.linkfluence.inventory.api.leaseweb
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.leaseweb :as lsw]
            [com.linkfluence.utils :as u]))

(defn add-operation
  ([id op params]
    (add-operation id op params "Operation submitted"))
  ([id op params msg]
    (if-not (lsw/ro?)
      (do
        (lsw/add-operation id op params)
        (u/mk-resp 200 "success" {} msg))
      (u/mk-resp 403 "error" {} "Read Only !"))))


(defroutes LSW
  (GET "/fsync" [] (if (lsw/ro?)
                      (do (future
                        (lsw/load-inventory!)
                        (lsw/load-pscheme!)
                        (log/info "[FSYNC] received lsw update notification"))
                      (u/mk-resp 202 "success" {} "Operation submitted"))
                      (u/mk-resp 403 "error" {} "Not read only, sync forbidden")))
  (GET "/event" [] (u/mk-resp 200 "success" {:data {:size (lsw/get-lsw-event-queue-size)}}))
  (POST "/disable-server-install" [] (u/mk-resp 200 "success" {:data (lsw/disable-server-install! true)}))
  (POST "/enable-server-install" [] (u/mk-resp 200 "success" {:data (lsw/disable-server-install! false)}))
  (GET "/install-state" [] (u/mk-resp 200 "success" {:data (lsw/get-install-loop-state)}))
  (POST "/install-state/clean" [] (do  (lsw/clean-install-loop!)
                                            (u/mk-resp 200 "success" {} "Clean Operation Done")))
  (POST "/install-state/clean/:id" [id] (do  (lsw/clean-install-loop! id)
                                           (u/mk-resp 200 "success" {} "Clean Operation Done")))
  (GET "/net" [] (u/mk-resp 200 "success" {:data (lsw/get-current-addr)}))
  (GET "/server" [] (u/mk-resp 200 "success" {:data (lsw/get-lsw-inventory)}))
  (GET "/server-with-ref" [] (u/mk-resp 200 "success" {:data (lsw/get-lsw-inventory-with-ref)}))
  (GET "/server/:id" [id] (if-let [server (lsw/get-server (keyword id))]
                            (u/mk-resp 200 "success" {:data server})
                            (u/mk-resp 404 "error" {} "Server not found")))
  (GET "/server/:id/jobs" [id] (if-let [jobs (lsw/get-server-jobs (keyword id))]
                              (u/mk-resp 200 "success" {:data jobs})
                              (u/mk-resp 404 "error" {} "Server not found : no job list to send")))
  (GET "/server/:id/job/:jobid" [id jobid] (if-let [job (lsw/describe-server-job (keyword id) jobid)]
                              (u/mk-resp 200 "success" {:data job})
                              (u/mk-resp 404 "error" {} "Server not found : can't return a job")))
  (GET "/server-with-ref/:ref" [ref] (if-let [server (lsw/get-server-with-ref ref)]
                            (u/mk-resp 200 "success" {:data server})
                            (u/mk-resp 404 "error" {} "Server not found")))
  (POST "/server/:id/install" [id] (if-let [server (lsw/get-server (keyword id))]
                                    (if-not (:disable-install lsw/lsw-conf)
                                        (add-operation id "reinstall" nil "Re-Installation request submitted")
                                        (u/mk-resp 403 "error" {} "Server not found"))
                                    (u/mk-resp 404 "error" {} "Installation of server is disabled")))
  (GET "/server/:id/install/finish" [id] (if-let [server (lsw/get-server (keyword id))]
                                          (add-operation id "end-setup" nil "Installation end request submitted")
                                          (u/mk-resp 404 "error" {} "Server not found")))
  (POST "/server/:id/privateIp" [id privateIp] (if-let [server (lsw/get-server (keyword id))]
                                      (add-operation id "set-private-ip" privateIp "Set private ip request submitted")
                                      (u/mk-resp 404 "error" {} "Server not found")))
  (POST "/server/:id/custominstall" [id partitionSchema] (if-let [server (lsw/get-server (keyword id))]
                                (add-operation id "custom-reinstall" partitionSchema)
                                (u/mk-resp 404 "error" {} "Server not found")))
  (DELETE "/server/:id" [id] (if-let [server (lsw/get-server (keyword id))]
                                  (add-operation server "delete" nil "Server deletion from inventory submitted")
                                  (u/mk-resp 404 "error" {} "Server not found")))
  (POST "/server/:id/reboot" [id] (if-let [server (lsw/get-server (keyword id))]
                              (add-operation id "reboot" nil "Reboot request submitted")
                              (u/mk-resp 404 "error" {} "Server not found")))
  (GET "/server/:id/install" [id] (if-let [server (lsw/get-server (keyword id))]
                              (if-not (lsw/ro?)
                                (u/mk-resp 200 "success" {:data (lsw/get-install-state (keyword id))})
                                (u/mk-resp 403 "error" {} "Read-only mode !"))
                              (u/mk-resp 404 "error" {} "Server not found")))
  (GET "/pschema" [] (u/mk-resp 200 "success" {:data (lsw/get-lsw-pscheme)}))
  (GET "/pschema/:name" [name] (if-let [schema (lsw/get-partition-schema (keyword name))]
                            (u/mk-resp 200 "success" {:data schema})
                            (u/mk-resp 404 "error" {} "Schema not found")))
  (DELETE "/pschema/:name" [name] (if-let [schema (lsw/get-partition-schema (keyword name))]
                            (if-not (lsw/ro?)
                            (do
                              (u/mk-resp 202 "success" {} "Schema deleted"))
                              (u/mk-resp 403 "error" {} "Read-Only !"))
                            (u/mk-resp 404 "error" {} "Schema not found")))
  (POST "/pschema" [name partitionSchema] (when-not (lsw/ro?)
                                            (lsw/create-partition-schema name partitionSchema)
                                            (u/mk-resp 204 "success" {} "Schema successfully created or updated"))))
