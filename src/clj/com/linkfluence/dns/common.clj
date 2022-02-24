(ns com.linkfluence.dns.common
    (:import [java.io File]
             [java.util.concurrent LinkedBlockingQueue])
    (:require [clojure.string :as str]
              [clojure.tools.logging :as log]
              [com.linkfluence.store :as store]
              [com.linkfluence.utils :as utils]
              [clojure.java.shell :as shell]
              [digest]
              [clostache.parser :as template]
              [clojure.spec.alpha :as spec])
    (:use [clojure.java.io]
          [clojure.walk]))

;path of db file
(def zone-path (atom "/etc/bind"))
(def bind-user (atom "bind"))
(def named-conf-local (atom "named.conf.local"))

(def dns-conf (atom nil))

(defn ro?
  []
  (:read-only @dns-conf))

(defn normalize-path
  [path]
  (if (= "/" (str (last path)))
                (str/join (butlast path))
                path))

(defn save-zone
  "save dns zone/conf file"
  [db-name]
  (if-not (= "named.conf.local" db-name)
    (let [filepath (str @zone-path "/" db-name)
          key (str (:key (:store @dns-conf)) "/" db-name)]
            (store/save (assoc (:store @dns-conf) :key key) (slurp filepath))))
    (let [filepath (str @zone-path "/named.conf.local")
          key (str (:key (:store @dns-conf)) "/" @named-conf-local)]
      (store/save (assoc (:store @dns-conf) :key key) (slurp filepath))))

(defn load-zone
  "load dns zone/conf file"
  [db-name]
  (if-not (= "named.conf.local" db-name)
    (let [filepath (str @zone-path "/" db-name)
          key (str (:key (:store @dns-conf)) "/" db-name)]
          (spit
              filepath
              (store/load (assoc (:store @dns-conf) :key key))))
    (let [filepath (str @zone-path "/named.conf.local")
          key (str (:key (:store @dns-conf)) "/" @named-conf-local)]
          (spit
              filepath
              (store/load (assoc (:store @dns-conf) :key key))))))


(defn restart-dns
  "restart dns service"
  [& [op-queue need-restart?]]
  ;;restart only when it is enable in conf and when queue is empty
  (when (and (:restart @dns-conf)
          (or
            (nil? @op-queue)
            (and
              (some? @op-queue)
              need-restart?)))
    (log/info "[DNS] restarting bind9 to update conf")
    (if (:sudo @dns-conf)
        (shell/sh "sudo" "service" "bind9" "restart")
        (shell/sh "service" "bind9" "restart"))))

(defn list-db
  "list db file in the asked path"
  []
  (let [file-list (.listFiles (File. @zone-path))
        zone-pattern #"db\..*|.*\.arpa"]
      (sort file-list)
        (vec (filter
          (fn [val](not (nil? val)))
          (map
            (fn [file] (re-matches zone-pattern (.getName file)))
            file-list)))))

(defn db-exists?
  "check db file existence"
  [db-name]
  (reduce (fn
            [acc x]
            (if acc
               (reduced acc)
               (= x db-name))) false (list-db)))


(defn configure!
  "initialize dns mgt"
  [conf]
  ;initialize path
  (reset! zone-path (normalize-path (get-in conf [:bind-dir] "/etc/bind")))
  ;;init bind user
  (reset! bind-user (get-in conf [:bind-user] "bind"))
  ;;init bind user
  (reset! named-conf-local (get-in conf [:named-conf-local] "named.conf.local"))
  ;;global conf
  (reset! dns-conf conf))
