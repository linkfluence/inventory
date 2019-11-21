(ns com.linkfluence.inventory.net
  (:import [org.apache.commons.net.util SubnetUtils SubnetUtils$SubnetInfo])
  (:require [clojure.string :as str]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

(defn get-subnet-info
  ([cidr-address]
    (.getInfo (SubnetUtils. cidr-address)))
  ([network netmask]
    (.getInfo (SubnetUtils. network netmask))))

(defn get-ip
    [^String cidr-address]
    (first (.split cidr-address "/")))

(defn is-in-subnet
  ([cidr-address address]
    (.isInRange (get-subnet-info cidr-address) address))
  ([network netmask address]
    (.isInRange (get-subnet-info network netmask) address)))

(defn get-cidr
  [network netmask]
    (str "/"
      (last (.split
      (.getCidrSignature (get-subnet-info network netmask))
      "/"))))

(defn get-broadcast-addr
  ([cidr-address]
    (.getBroadcastAddress (get-subnet-info cidr-address)))
  ([network netmask]
    (.getBroadcastAddress (get-subnet-info network netmask))))

(defn inc-addr
  [addr]
  (let [splitted-addr (str/split addr #"\.")
        splitted-int-addr (into [] (map (fn [x] (Integer/parseInt x)) splitted-addr))
        ct-addr (count splitted-addr)]
      (if (= ct-addr 4)
        (let [inc-addr-vec
              (loop [tk 3
                     addr-vec splitted-int-addr]
                (if (<= 0 tk)
                  (if (> 255 (get addr-vec tk))
                    (update addr-vec tk inc)
                    (recur (dec tk) (assoc addr-vec tk 0)))
                  addr-vec))]
            (str/join "." inc-addr-vec))
        addr)))

(defn get-next-address
  ([cidr-address address]
    (let [^SubnetUtils$SubnetInfo subnet-info (get-subnet-info cidr-address)
          next-addr (inc-addr address)]
          (if (.isInRange subnet-info next-addr)
            next-addr
            address)))
  ([network netmask address]
    (let [^SubnetUtils$SubnetInfo  subnet-info (get-subnet-info network netmask)
          next-addr (inc-addr address)]
          (if (.isInRange subnet-info next-addr)
            next-addr
            address))))
