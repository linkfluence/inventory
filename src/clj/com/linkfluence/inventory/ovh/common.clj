(ns com.linkfluence.inventory.ovh.common
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ovh.core :as ovh]))


;;init
(defn configure!
  "Main function"
  [conf]
  (if-not (:read-only conf)
    (ovh/init! (:application_key conf)
               (:application_secret conf)
               (:consumer_key conf))
    (log/info "[OVH] mode set to READ_ONLY, api actions are limited")))
