(ns com.linkfluence.dns-cleaner.api
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.dns-cleaner :as dns-cleaner]
            [com.linkfluence.utils :as u]))

;;dns mgt
(defroutes DNS-CLEANER
    ;;route to display the clean
    (GET "/show/:zone" [zone] (u/mk-resp 200 "success" {:data (dns-cleaner/find-records-to-clean zone)}))
    ;;route to run the clean
    (POST "/clean/:zone" [zone] (do
                      (future
                        (dns-cleaner/clean-zone zone)
                        (log/info "[DNS-CLEANER] received dns cleaner call for zone" zone))
                      (u/mk-resp 202 "success" {} "Clean submitted"))))
