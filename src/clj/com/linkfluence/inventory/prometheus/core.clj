(ns com.linkfluence.inventory.prometheus.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.util.response :refer [response]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [clojure.tools.logging :as log]
            [clometheus.core :as c]
            [clojure.string :refer [lower-case]]
            [clometheus.txt-format :as txt]
            ;;import api handler
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.utils :as u]))

; @author Jean-Baptiste Besselat
; @Copyright Adot SAS 2020

;;mai api conf
(def conf (atom {}))

(def count-resources-gauge
    (c/gauge
      "inventory_resources_count"
      :description
      "resources count for a specific tag"
      :labels ["inventory_tag" "inventory_tag_value"]))

(defn generate-response
  []
  (doseq [tag (:tags @conf)]
    (let [tags-stats (inventory/get-tags-stats-from-resources tag)]
        (doseq [[k v] tags-stats]
            (c/set! count-resources-gauge v
              :labels {"inventory_tag"
                         (lower-case (name tag))
                       "inventory_tag_value"
                         (lower-case (name k))}))))
  (txt/metrics-response))

(defroutes app-routes
  (GET "/metrics" [] (generate-response)))

(def handler (-> app-routes
                 (wrap-content-type)
                 (wrap-gzip)))

(defn configure!
  [{:keys [host port] :or {host "127.0.0.1" port 8081} :as prom-conf}]
  (reset! conf prom-conf)
  (defonce server (run-jetty #'handler {:port (:port prom-conf) :host (:host prom-conf) :join? false})))
