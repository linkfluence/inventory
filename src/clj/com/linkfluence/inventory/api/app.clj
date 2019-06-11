(ns com.linkfluence.inventory.api.app
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.app :as app]
            [com.linkfluence.utils :as u]))

(defn put-events
  ([evs]
    (put-events evs "Operation submitted"))
  ([evs msg]
    (if-not (app/ro?)
      (do
        (doseq [ev evs]
          (app/add-inventory-event ev))
        (u/mk-resp 202 "success" {} msg))
      (u/mk-resp 403 "error" {} "Read Only"))))

(defn put-event
  ([ev]
    (put-event ev "Operation submitted"))
  ([ev msg]
    (if-not (app/ro?)
      (do
        (app/add-inventory-event ev)
        (u/mk-resp 202 "success" {} msg))
      (u/mk-resp 403 "error" {} "Read Only"))))

;;deploy specific routes
(defroutes APP
     (GET "/fsync" [] (do
                      (future
                        (app/load-inventory!)
                        (log/info "[FSYNC] received app update notfication"))
                      (u/mk-resp 202 "success" {} "Fsync submitted")))
    (GET "/env" [] (u/mk-resp 200 "success" {:data (app/get-env)}))
    (GET "/env/:env" [env] (u/mk-resp 200 "success" {:data (app/get-env env)}))
    (GET "/env/:env/apps" [env] (u/mk-resp 200 "success" {:data (app/get-env-apps env)}))
    (GET "/env/:env/app/:app" [env app] (u/mk-resp 200 "success" {:data (app/get-app app env)}))
    (GET "/env/:env/app/:app/tags" [env app] (u/mk-resp 200 "success" {:data (app/get-app-tags app env)}))
    (GET "/env/:env/app/:app/resource-tags" [env app] (u/mk-resp 200 "success" {:data (app/get-app-resource-tags app env)}))
    (GET "/env/:env/app/:app/resources" [env app] (if-let [app-data (app/get-app-resources app env)]
                                                    (u/mk-resp 200 "success" {:data app-data})
                                                    (u/mk-resp 404 "error" {} "App not found")))
    (POST "/env/:env/app/:app/action/:action" [env app action] (if-let [app-data (app/get-app-resources app env)]
                                            (do
                                                (app/submit-action app env action)
                                                (u/mk-resp 200 "success" {} "request submited"))
                                            (u/mk-resp 404 "error" {} "App not found")))
    (GET "/env/:env/app/:app/resources/tag/:tag" [env app tag] (if-let [app-data (app/get-app-resources-tag-value app env tag)]
                                                                    (u/mk-resp 200 "success" {:data app-data})
                                                                    (u/mk-resp 404 "error" {} "App not found")))
    (POST "/new/env" [name description] (put-event {:type "env" :create true :env name :description description}))
    (POST "/new/env/:env/app" [env name tags resource-tags] (if (app/env-exists? env)
                                                                    (put-event {:type "app" :env env :app name :tags tags :resource-tags resource-tags})
                                                                    (u/mk-resp 404 "error" {} "Env not found, cannot create app")))
    (PUT "/env/:env" [env app-tags resource-tags] (put-event {:type "env" :env env :app-tags app-tags :resource-tags resource-tags}))
    (PUT "/env/:env/app/:app" [env app tags resource-tags] (if (and (app/env-exists? env) (app/exists? app env))
                                                                    (put-event {:type "app" :env env :app app :tags tags :resource-tags resource-tags})
                                                                    (u/mk-resp 404 "error" {} "Env or app not found, cannot update app")))
    (DELETE "/env/:env" [env] (if (app/env-exists? env)
                                        (put-event {:type "env" :env env :delete true})
                                        (u/mk-resp 404 "error" {} "Env not found, cannot delete app")))
    (DELETE "/env/:env/app/:app" [env app] (if (and (app/env-exists? env) (app/exists? app env))
                                                                    (put-event {:type "app" :env env :app app :delete true})
                                                                    (u/mk-resp 404 "error" {} "Env or app not found, cannot delete app"))))
