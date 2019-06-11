(ns com.linkfluence.inventory.api.deploy
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.deploy :as deploy]
            [com.linkfluence.utils :as u]))

;;deploy action definition
; update : update both base and app
; app : deploy/update only app
; base : deploy/update base environment


;;deploy specific routes
(defroutes DEPLOY
  (GET "/resource/:id" [id] (do (deploy/deploy-resource id)
                                 (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/resource/:id/update" [id] (do (deploy/update-resource id)
                                 (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/resource/:id/app" [id] (do (deploy/deploy-app-resource id)
                                (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/resource/:id/base" [id] (do (deploy/deploy-base-resource id)
                                (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/resource/:id/finish" [id] (do (deploy/end-deployment id)
                                    (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/group/:id" [id] (do (deploy/deploy-group id)
                             (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/group/:id/update" [id] (do (deploy/update-group id)
                             (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/group/:id/app" [id] (do (deploy/deploy-app-group id)
                            (u/mk-resp 200 "success" {} "Operation submitted")))
  (GET "/group/:id/base" [id] (do (deploy/deploy-base-group id)
                             (u/mk-resp 200 "success" {} "Operation submitted"))))
