(ns com.linkfluence.inventory.gcs
  (:require [com.linkfluence.inventory.gcp.common :as common]
            [com.linkfluence.inventory.gcp.instance :as instance]))


; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2020 / Adot 2020

(defn sync!
  [])

(defn start!
  []
  (if-not (or (nil? (common/get-conf)) (common/ro?))
    [{:stop (instance/start-loop!)} (instance/start-inventory-update-consumer!)]
    []))

(defn configure!
  "init gcs part"
  [gcs-conf]
  (common/set-conf gcs-conf)
  (instance/load-inventory!))
