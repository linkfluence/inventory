(ns com.linkfluence.inventory.api.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.format-params :refer [wrap-restful-params]]
            [ring.middleware.format-response :refer [wrap-restful-response]]
            [clojure.tools.logging :as log]
            ;;import api handler
            [com.linkfluence.inventory.api.inventory :as inventory]
            [com.linkfluence.inventory.api.ovh :as ovh]
            [com.linkfluence.inventory.api.aws :as aws]
            [com.linkfluence.inventory.api.app :as app]
            [com.linkfluence.inventory.api.acs :as acs]
            [com.linkfluence.inventory.api.gcp :as gcp]
            [com.linkfluence.inventory.api.internal :as internal]
            [com.linkfluence.dns.api :as dns]
            [com.linkfluence.dns-cleaner.api :as dns-cleaner]
            [com.linkfluence.inventory.api.provision :as provision]
            [com.linkfluence.inventory.api.dhcp :as dhcp]
            [com.linkfluence.inventory.api.leaseweb :as lsw]
            [com.linkfluence.inventory.api.deploy :as deploy]
            [com.linkfluence.utils :as u]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2016

;;mai api conf
(def conf (atom {}))

;docker mgt
(defroutes KUBERNETES)

(defroutes app-routes
  (context "/ovh" [] ovh/OVH)
  (context "/aws" [] aws/AWS)
  (context "/app" [] app/APP)
  (context "/acs" [] acs/ACS)
  (context "/gcp" [] gcp/GCP)
  (context "/internal" [] internal/INTERNAL)
  (context "/dns" [] dns/DNS)
  (context "/dns-cleaner" [] dns-cleaner/DNS-CLEANER)
  (context "/dhcp" [] dhcp/DHCP)
  (context "/inventory" [] inventory/INVENTORY)
  (context "/deploy" [] deploy/DEPLOY)
  (context "/provision" [] provision/PROVISION)
  (context "/lsw" [] lsw/LSW)
  (GET "/favicon.ico" [] {:status 204}))

(def handler (-> app-routes
                 (wrap-restful-params :formats [:json-kw])
                 (wrap-restful-response :charset "UTF-8" :formats [:json])
                 (wrap-content-type)
                 (wrap-gzip)))

(defn configure!
  [{:keys [host port] :or {host "127.0.0.1" port 8080} :as api-conf}]
  (reset! conf api-conf)
  (defonce server (run-jetty #'handler {:port port :host host :join? false})))
