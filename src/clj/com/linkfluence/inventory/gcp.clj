(ns com.linkfluence.inventory.gcp
  (:require [clj-time.core :as t]
            [chime :refer [chime-at]]
            [clj-time.periodic :refer [periodic-seq]]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.gcp.common :as common]
            [com.linkfluence.inventory.gcp.instance :as instance]
            [com.linkfluence.inventory.queue :refer [init-queue]]))


; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2020 / Adot 2020

(defn start-saver!
  [last-save items-not-saved save-fn]
  (chime-at (periodic-seq (t/now) (t/seconds 5))
                  (fn []
                      (when (u/save? last-save items-not-saved)
                      (save-fn)))))

(defn sync!
  [])

(defn start!
  []
  (if-not (or (nil? (common/get-conf)) (common/ro?))
    [{:stop (instance/start-loop!)} (instance/start-inventory-update-consumer!)
    (start-saver! instance/last-save instance/items-not-saved instance/save-inventory)]
    []))

(defn configure!
  "init gcs part"
  [gcs-conf]
  (common/set-conf gcs-conf)
  (init-queue instance/gcpi-queue (:instance-queue gcs-conf))
  (instance/load-inventory!))
