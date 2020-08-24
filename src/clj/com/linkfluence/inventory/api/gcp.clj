(ns com.linkfluence.inventory.api.gcp
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.gcp.common :refer [ro?]]
            [com.linkfluence.inventory.gcp.instance :as vm]
            [com.linkfluence.utils :refer [mk-resp]]))

;;aws resource mgt
(defroutes GCP
  (GET "/instance/fsync" [] (if (ro?)
                        (do
                          (future
                            (vm/load-inventory!)
                            (log/info "[FSYNC] received acs ecs update notfication"))
                          (mk-resp 202 "success" {} "Fsync submitted"))
                          (mk-resp 403 "error" {} "Not Read Only")))
  ;;ECS
  (GET "/instance" [] (mk-resp 200 "success" {:data (vm/get-gcpi-inventory)}))
  (GET "/instance/:instance" [instance] (mk-resp 200 "success" {:data (vm/get-instance instance)})))
