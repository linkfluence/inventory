(ns com.linkfluence.starter
  (:require [com.linkfluence.inventory.api.core :as api]
            [com.linkfluence.store :as store]
            [com.linkfluence.inventory.ovh.common :as ovh-common]
            [com.linkfluence.inventory.ovh.server :as ovh-server]
            [com.linkfluence.inventory.ovh.cloud :as ovh-cloud]
            [com.linkfluence.inventory.aws :as aws]
            [com.linkfluence.inventory.app :as app]
            [com.linkfluence.inventory.acs :as acs]
            [com.linkfluence.inventory.gcp :as gcp]
            [com.linkfluence.inventory.leaseweb :as lsw]
            [com.linkfluence.inventory.internal :as internal]
            [com.linkfluence.dns :as dns]
            [com.linkfluence.inventory.dhcp :as dhcp]
            [com.linkfluence.inventory.caller :as caller]
            [com.linkfluence.inventory.provision :as provision]
            [com.linkfluence.inventory.deploy :as deploy]
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.dns-cleaner :as dns-cleaner]
            [com.linkfluence.utils :as utils]
            [clojure.tools.logging :as log]
            [clj-time.format :as f]
            [cheshire.generate :refer [add-encoder]])
  (:gen-class))

  ; @author Jean-Baptiste Besselat
  ; @Copyright Linkfluence SAS 2017

(defn set-ro
  "set ro flag, so support only fsync, block all write"
  [kh conf]
  (let [hconf (kh conf)]
    (if-not (nil? (:read-only hconf))
      hconf
      (if-not (nil? (:read-only conf))
        (assoc hconf :read-only (:read-only conf))
        (assoc hconf :read-only false)))))

(defn configure!
  [conf]
  ;;by default utils, storage, main inventory ,api and caller are configured
  (utils/configure! {:fsync (:fsync conf)})
  (when (nil? (:store conf))
    (log/info "store is empty"))
  (store/configure! (set-ro :store conf))
  (inventory/configure! (set-ro :inventory conf))
  (api/configure! (set-ro :api conf))
  (caller/configure! (set-ro :caller conf))
  ;;optional workers
  (when-not (nil? (:provision conf))
    (provision/configure! (set-ro :provision conf)))
  (when-not (nil? (:deploy conf))
    (deploy/configure! (set-ro :deploy conf)))
  ;;OVH common  servers
  (when-not (nil? (:ovh conf))
    (ovh-common/configure! (set-ro :ovh conf)))
  ;;OVH baremetal servers
  (when-not (nil? (:ovh-server conf))
    (ovh-server/configure! (set-ro :ovh-server conf)))
  ;;OVH baremetal servers
  (when-not (nil? (:ovh-cloud conf))
    (ovh-cloud/configure! (set-ro :ovh-cloud conf)))
  (when-not (nil? (:lsw conf))
    (lsw/configure! (set-ro :lsw conf)))
  ;;cloud provider
  (when-not (nil? (:aws conf))
    (aws/configure! (set-ro :aws conf)))
  (when-not (nil? (:acs conf))
    (acs/configure! (set-ro :acs conf)))
  (when-not (nil? (:gcp conf))
    (gcp/configure! (set-ro :gcp conf)))
  ;;inventory for baremetal/vm server internal
  (when-not (nil? (:internal conf))
    (internal/configure! (set-ro :internal conf)))
  ;;bind dns basic management
  (when-not (nil? (:dns conf))
    (dns/configure! (set-ro :dns conf)))
  ;;dhcp basic API
  (when-not (nil? (:dhcp conf))
    (dhcp/configure! (set-ro :dhcp conf)))
  ;;app basic API
  (when-not (nil? (:app conf))
    (app/configure! (set-ro :app conf)))
  (when-not (nil? (:dns-cleaner conf))
    (dns-cleaner/configure! (set-ro :dns-cleaner conf))))
  ;;load conf, etc

(defn shutdown
  [shut-list]
  (doseq [shut shut-list]
      ((:stop shut))))

(defn -main
  "Main function to start inventory/provisionner"
  [conf-file]
  (add-encoder org.joda.time.DateTime
    (fn [dt jg]
      (.writeString jg (f/unparse (f/formatters :date-time) dt))))
  (let [conf (load-file conf-file)]
    (configure! conf)
    (let [shut-list (into [] (filter (fn [x] (not (nil? x)))
                        (concat
                          (inventory/start!)
                          (caller/start!)
                          (when-not (or (nil? (:ovh conf)) (nil? (:ovh-server conf)))
                            (ovh-server/start!))
                          (when-not (or (nil? (:ovh conf)) (nil? (:ovh-cloud conf)))
                            (ovh-cloud/start!))
                          (when-not (nil? (:aws conf))
                            (aws/start!))
                          (when-not (nil? (:internal conf))
                            (internal/start!))
                          (when-not (nil? (:dns conf))
                            (dns/start!))
                          (when-not (nil? (:dhcp conf))
                            (dhcp/start!))
                          (when-not (nil? (:provision conf))
                            (provision/start!))
                          (when-not (nil? (:lsw conf))
                            (lsw/start!))
                          (when-not (nil? (:acs conf))
                            (acs/start!))
                          (when-not (nil? (:gcp conf))
                            (gcp/configure! (set-ro :gcp conf)))
                          (when-not (nil? (:app conf))
                            (app/start!)))))]
                  (.addShutdownHook (Runtime/getRuntime)
                      (proxy [Thread] []
                        (run []
                          (log/info "Exit...")
                          (try
                            (shutdown shut-list)
                            (catch Exception ex
                              (log/error ex "error while exiting")))))))))
