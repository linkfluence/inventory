(ns com.linkfluence.dns
    (:import [java.io File]
             [java.util.concurrent LinkedBlockingQueue])
    (:require [com.linkfluence.dns.common :as common]
              [com.linkfluence.dns.record :as record]
              [com.linkfluence.dns.zone :as zone]))


(defn start!
  []
  (into [](concat
    (record/start!)
    (zone/start!))))

(defn configure!
  "initialize dns mgt"
  [conf]
  (common/configure! conf))
