(ns com.linkfluence.inventory.acs
  (:require [com.linkfluence.inventory.acs.common :as common]
            [com.linkfluence.inventory.acs.ecs :as ecs]
            [aliyuncs.core :as acs]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017


(defn sync!
  [])

(defn start!
  []
  (if-not (or (nil? (common/get-conf)) (common/ro?))
    [{:stop (ecs/start-loop!)} (ecs/start-inventory-update-consumer!)]
    []))

(defn configure!
  "init acs part"
  [acs-conf]
  (acs/catch-errors? false)
  (common/set-conf acs-conf)
  (ecs/init-queue acs-conf)
  (ecs/load-inventory!))
