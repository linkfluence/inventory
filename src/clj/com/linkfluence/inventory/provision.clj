(ns com.linkfluence.inventory.provision
  (:import [java.util.concurrent LinkedBlockingQueue])
  (:require [com.linkfluence.inventory.caller :as caller]
            [com.linkfluence.utils :as utils]
            [com.linkfluence.inventory.core :as inventory]
            [clojure.tools.logging :as log]
            [com.linkfluence.inventory.queue :as queue :refer [put tke]]))

;@author Jean-Baptiste Besselat
;@Copyright Linkfluence SAS 2017

;;platform agnostic resource provionning send an ansible-playbook commnad which bootstrap server env

;;queue for host to provision
(def ^LinkedBlockingQueue provision-queue (LinkedBlockingQueue.))

;;provsioner conf
(def provision-conf (atom nil))

(defn ro?
  []
  (:read-only @provision-conf))

(defn mk-provision-specs
  [host type]
  {:host host :type (if type (keyword type) :default)})

(defn add-provision
  "Add host to provision"
  [host & [type]]
  ;;check that resource is in inventory retrieve eventual private ip or fqdn to send caller request
  (when-not (ro?)
  (when-let [host-tags (inventory/get-resource host)]
    (inventory/add-inventory-event {:type "resource"
                                    :id host
                                    :tags [{:name "state" :value "provision_pending"}]})
    (log/info "[PROVISION] provisioning resource" host)
    (let [public-ip (:publicIp host-tags)
          fqdn (:FQDN host-tags)
          private-ip (:privateIp host-tags)]
          (cond
            (not (nil? private-ip)) (.put provision-queue (mk-provision-specs private-ip type))
            (not (nil? fqdn)) (.put provision-queue (mk-provision-specs fqdn type))
            (not (nil? public-ip)) (.put provision-queue (mk-provision-specs public-ip type))
            :else (.put provision-queue (mk-provision-specs host type)))))))

(defn end-provision
  "Update inventory and mark resource as provisioned"
  [host]
  (when-not (ro?)
  (when-let [host-tags (inventory/get-resource host)]
    (inventory/add-inventory-event {:type "resource"
                                    :id host
                                    :tags [{:name "state" :value "provisioned"}]}))))


(defn build-provision-request
  "Send a provision request to caller"
  [provision-specs]
  (when-not (ro?)
  (caller/add-command
    (assoc
      (get-in @provision-conf [(:type provision-specs) :command])
      :hosts (:host provision-specs)))))

(defn start-provision-consumer!
  "Start provision consumer"
  []
  (when-not (ro?)
  (utils/start-thread!
    (fn [] ;;consume queue
      (when-let [provision-specs (.take provision-queue)]
        (build-provision-request provision-specs)))
    "Provisioner consumer")))

(defn start!
  []
  (if-not (or (nil? @provision-conf) (ro?))
    [(start-provision-consumer!)]
    []))

(defn configure!
  [conf]
  (reset! provision-conf conf))
