(ns com.linkfluence.inventory.api.dhcp
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.dhcp :as dhcp]
            [com.linkfluence.utils :as u]))
;;dhcp mgt
(defroutes DHCP
  (GET "/list" [] (u/mk-resp 200 "success" {:data (dhcp/get-list)}))
  (GET "/leases/:id" [id] (u/mk-resp 200 "success" {:data (dhcp/get-leases id)})))
