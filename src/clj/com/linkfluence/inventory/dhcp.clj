(ns com.linkfluence.inventory.dhcp
  (:import [java.io File]
           [java.util.concurrent LinkedBlockingQueue]
           [org.talamonso.OMAPI Connection]
           [org.talamonso.OMAPI.Exceptions OmapiObjectException OmapiException OmapiConnectionException]
           [org.talamonso.OMAPI.Objects Lease Subnet])
    (:require [clojure.string :as str]
              [clojure.tools.logging :as log])
    (:use [clojure.java.io]))

;@author Jean-Baptiste Besselat
;@Copyright Linkfluence SAS 2017
;;Linkfluence isc-dhcp servers mgt

(def conf (atom nil))

(def c (atom nil))

(defn init-connection!
  "initiate omapi Connection"
  []
  (doseq [[k v] (:server @conf)]
  (try
    (let [^Connection con (Connection. (:host v) (:port v))]
      (.setAuth con "omapi_key" (:omapi_key v))
      (swap! c assoc k con))
    (catch OmapiConnectionException e
      (log/error "[DHCP]" e)))))

(defn close-connection!
  "Close Omapi connection"
  []
  (doseq [[k v] @c]
    (.close v)
    (swap! c dissoc k)))

(defn get-list
  []
  (map (fn [[k v]] (name k)) (:server @conf)))

(defn get-leases
  "Return dhcp lease"
  [dhcp-id]
  (when-let [conn ((keyword dhcp-id) @c)]
  (try
    (let [^Subnet s (Subnet. conn)
          ^Lease la (into [] (.getLeases s (get-in @conf [:server (keyword dhcp-id) :subnet :start]) (get-in @conf [:server (keyword dhcp-id) :subnet :end])))
          la* (filter (fn [^Lease l] (not (nil? l))) la)
          la** (filter (fn [^Lease l] (= 2 (.getState l))) la*)]
           (into [] (map (fn [^Lease l]
                 {
                    :hostname (.getClientHostname l)
                    :macAddress (.getHardwareAddress l)
                    :ipAddress (.getIpAddress l)
                    :state (.getState l)
                    :start (.getStarts l)
                    :end (.getEnds l)
                  }
                  )
                  la**)))
    (catch OmapiException e
      (log/info e)
      []))))

(defn start!
  []
  (if-not (nil? @conf)
    (do
      (init-connection!)
      [{:stop (fn [] (close-connection!))}])
    []))

(defn configure!
  "initiate connection"
  [dhcp-conf]
  (reset! conf dhcp-conf))
