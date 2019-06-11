(ns com.linkfluence.inventory.aws
  (:require [com.linkfluence.inventory.aws.common :as aws-common]
            [com.linkfluence.inventory.aws.autoscaling :as asg]
            [com.linkfluence.inventory.aws.ec2 :as ec2]
            [com.linkfluence.inventory.aws.rds :as rds]
            [com.linkfluence.inventory.aws.elasticache :as elasticache]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

(defn sync!
  [])

(defn start!
  []
  (if-not (or (nil? (aws-common/get-conf)) (aws-common/ro?))
    [{:stop (ec2/start-loop!)} (ec2/start-inventory-update-consumer!)
     {:stop (asg/start-loop!)} (asg/start-inventory-update-consumer!)
     {:stop (rds/start-loop!)} (rds/start-inventory-update-consumer!)
     {:stop (elasticache/start-loop!)} (elasticache/start-inventory-update-consumer!)]
    []))

(defn configure!
  "init aws part"
  [aws-conf]
  (aws-common/set-conf aws-conf)
  (ec2/load-inventory!)
  (asg/load-inventory!)
  (rds/load-inventory!)
  (elasticache/load-inventory!))
