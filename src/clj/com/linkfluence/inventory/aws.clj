(ns com.linkfluence.inventory.aws
  (:require [clojure.string :as str]
            [chime.core :as chime :refer [chime-at]]
            [com.linkfluence.inventory.aws.common :as aws-common]
            [com.linkfluence.inventory.aws.autoscaling :as asg]
            [com.linkfluence.inventory.aws.ec2 :as ec2]
            [com.linkfluence.inventory.aws.rds :as rds]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.aws.elasticache :as elasticache]
            [com.linkfluence.inventory.queue :refer [init-queue]])
(:import [java.time Instant Duration]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

(defn start-saver!
  [last-save items-not-saved save-fn]
  (chime-at (chime/periodic-seq (chime/now) (Duration/ofSeconds 5))
                  (fn [_]
                      (when (u/save? last-save items-not-saved)
                      (save-fn)))))

(defn sync!
  [])

(defn start!
  []
  (if-not (or (nil? (aws-common/get-conf)) (aws-common/ro?))
    [{:stop (ec2/start-loop!)} (ec2/start-inventory-update-consumer!)
     (start-saver! ec2/last-save ec2/items-not-saved ec2/save-inventory)
     {:stop (asg/start-loop!)} (asg/start-inventory-update-consumer!)
     (start-saver! asg/last-save asg/items-not-saved asg/save-inventory)
     {:stop (rds/start-loop!)} (rds/start-inventory-update-consumer!)
     (start-saver! rds/last-save rds/items-not-saved rds/save-inventory)
     {:stop (elasticache/start-loop!)} (elasticache/start-inventory-update-consumer!)
     (start-saver! elasticache/last-save elasticache/items-not-saved elasticache/save-inventory)]
    []))

(defn configure!
  "init aws part"
  [aws-conf]
  (aws-common/set-conf aws-conf)
  (ec2/load-inventory!)
  (init-queue ec2/aws-queue (:ec2-queue aws-conf))
  (asg/load-inventory!)
  (init-queue asg/aws-asg-queue (:asg-queue aws-conf))
  (rds/load-inventory!)
  (init-queue rds/aws-queue (:rds-queue aws-conf))
  (elasticache/load-inventory!)
  (init-queue elasticache/aws-queue (:elasticache-queue aws-conf)))
