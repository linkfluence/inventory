(ns com.linkfluence.dns
    (:import [java.io File])
    (:require [com.linkfluence.dns.common :as common]
              [com.linkfluence.dns.record :as record]
              [com.linkfluence.dns.zone :as zone]
              [com.linkfluence.inventory.queue :refer [init-queue]]))


(defn start!
  []
  (into [](concat
    (record/start!)
    (zone/start!))))

(defn configure!
  "initialize dns mgt"
  [conf]
  (init-queue
      record/op-queue
      (assoc (or (:record-queue conf) {}) :topic "inventory-dns-record"))
  (init-queue
      zone/op-queue
      (assoc (or (:zone-queue conf) {}) :topic "inventory-dns-zone"))
  (common/configure! conf))
