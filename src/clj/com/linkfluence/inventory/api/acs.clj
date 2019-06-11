(ns com.linkfluence.inventory.api.acs
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.acs.common :refer [ro?]]
            [com.linkfluence.inventory.acs.ecs :as ecs]
            [com.linkfluence.utils :refer [mk-resp]]))

;;aws resource mgt
(defroutes ACS
  (GET "/ecs/fsync" [] (if (ro?)
                        (do
                          (future
                            (ecs/load-inventory!)
                            (log/info "[FSYNC] received acs ecs update notfication"))
                          (mk-resp 202 "success" {} "Fsync submitted"))
                          (mk-resp 403 "error" {} "Not Read Only")))
  ;;ECS
  (GET "/ecs" [] (mk-resp 200 "success" {:data (ecs/get-acs-inventory)}))
  (GET "/ecs/:instance" [instance] (mk-resp 200 "success" {:data (ecs/get-instance instance)}))
  (PUT "/ecs/:instance" [instance name] (do (ecs/rename-request instance name)
                                            (mk-resp 200 "success" {} "Operation submitted"))))
