(ns com.linkfluence.inventory.gcs
  (:require [com.linkfluence.inventory.gcs.common :as gcs-common]
            [com.linkfluence.inventory.gcs.compute :as compute]))


; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2020 / Adot 2020

(defn sync!
  [])

(defn start!
  []
  (if-not (or (nil? (gcs-common/get-conf)) (gcs-common/ro?))
    [{:stop (compute/start-loop!)} (compute/start-inventory-update-consumer!)]
    []))

(defn configure!
  "init gcs part"
  [gcs-conf]
  (gcs-common/set-conf gcs-conf)
  (compute/load-inventory!))
