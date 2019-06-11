(ns com.linkfluence.inventory.api.provision
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.provision :as provision]
            [com.linkfluence.utils :as u]))

;;provision specific
(defroutes PROVISION
  (GET "/:id" [id] (do (provision/add-provision id :default)
                                 (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/:id/finish" [id] (do (provision/end-provision id)
                              (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/v1/start/:id" [id] (do (provision/add-provision id :default)
                                 (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/v1/start/:id/:type" [id type] (do
                              (provision/add-provision id type)
                              (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/v1/finish/:id" [id] (do (provision/end-provision id)
                               (u/mk-resp 200 "success" {} "Operation submitted"))))
