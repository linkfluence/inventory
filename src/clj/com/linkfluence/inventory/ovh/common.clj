(ns com.linkfluence.inventory.ovh.common
  (:require [clj-time.core :as t]
            [chime :refer [chime-at]]
            [clj-time.periodic :refer [periodic-seq]]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.linkfluence.utils :as u]
            [ovh.core :as ovh]))


(defn start-saver!
  [last-save items-not-saved save-fn]
  (chime-at (periodic-seq (t/now) (t/seconds 5))
                  (fn []
                      (when (u/save? last-save items-not-saved)
                      (save-fn)))))

;;init
(defn configure!
  "Main function"
  [conf]
  (if-not (:read-only conf)
    (ovh/init! (:application_key conf)
               (:application_secret conf)
               (:consumer_key conf))
    (log/info "[OVH] mode set to READ_ONLY, api actions are limited")))
