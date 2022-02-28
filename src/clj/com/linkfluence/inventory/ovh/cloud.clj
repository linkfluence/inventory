(ns com.linkfluence.inventory.ovh.cloud
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ovh.cloud :as cloud]
            [com.linkfluence.inventory.caller :as caller]
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [ovh.cloud :as cloud]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [com.linkfluence.inventory.queue :as queue :refer [init-queue put tke]])
  (:import [java.io File]
           [java.util.concurrent LinkedBlockingQueue]))

(def ovh-conf (atom nil))

(def ovh-inventory (atom {}))

(def last-save (atom (System/currentTimeMillis)))
(def items-not-saved (atom 0))

(defn ro?
  []
  (:read-only @ovh-conf))

(def ovh-queue (atom nil))

(defn get-ovh-event-queue-size
  []
  (.size ovh-queue))

(defn load-inventory!
  []
  ;;default use s3
  (when-let [inventory (store/load-map (:store @ovh-conf))]
    (reset! ovh-inventory inventory)))

(defn save-inventory
  "Save on both local file and s3"
  []
  (when (not (ro?))
  (if (u/save? last-save items-not-saved)
    (do
        (store/save-map (:store @ovh-conf) @ovh-inventory)
        (u/reset-save! last-save items-not-saved)
        (u/fsync "ovh/cloud"))
    (swap! items-not-saved inc))))

(defn- cached?
  "check if corresponding instance exist in cache inventory"
  [instance]
  (not (nil? (get @ovh-inventory (keyword (:id instance)) nil))))

(defn- ovh-region
  [az]
  (let [mt (re-matches #"([a-z-]*)([0-9]+)" (str/lower-case az))
        region (get mt 1)]
  (if-not (nil? region)
    (if (= "-" (str (last region)))
      (str/join (butlast region))
      region)
    az)))

(defn- ovh-short-az
  [az]
  (let [mt (re-matches #"([a-z-]*)([0-9]+)" (str/lower-case az))
        short-az (get mt 2)]
  (if-not (nil? short-az)
    short-az
    az)))

(defn- get-address
  [instance version type]
    (let [addresses (:ipAddresses instance)]
      (:ip
        (first
          (filter
            (fn [x] (and (= type (:type x)) (= version (:version x))))
            addresses)))))

(defn get-instance
  "Return ovh instance"
  [id]
  (get @ovh-inventory (keyword id) nil))

(defn- send-tags-request
  [instance]
  (when-not (ro?)
  (inventory/add-inventory-event {:type "resource"
                                  :provider "OVH-CLOUD"
                                  :id (str "ovh-" (:id instance))
                                  :tags [{:name "provider" :value "OVH-CLOUD"}
                                         {:name "Name" :value (:name instance)}
                                         {:name "REGION" :value (ovh-region (str/lower-case (:region instance)))}
                                         {:name "AZ" :value (str/lower-case (:region instance))}
                                         {:name "SHORT_AZ" :value (ovh-short-az (str/lower-case (:region instance)))}
                                         {:name "publicIp" :value (get-address instance 4 "public")}
                                         {:name "publicIpv6" :value (get-address instance 6 "public")}
                                         {:name "privateIp" :value (get-address instance 4 "private")}
                                         {:name "instance_type" :value (first (str/split (:planCode instance) #"\."))}
                                         {:name "state" :value "installed"}]})))

(defn- delete-instance!
  "Delete server from ovh inventory, this is cascaded to main inventory"
  [instance]
  (when-not (ro?)
  ;;remove from ovh-inventory
  (swap! ovh-inventory dissoc (keyword (:id instance)))
  ;;send delete update to main inventory
  (inventory/add-inventory-event {:type "resource"
                                  :provider "OVH-CLOUD"
                                  :id (str "ovh-" (:id instance))
                                  :tags []
                                  :delete true}))
  ;;save inventory
  (save-inventory))

(defn- mk-bootstrap-cmd
  [host-id]
  (vec
    (concat (:post-install-optional-cmd @ovh-conf)
          [ "sudo ip link set ens4 up"
            "sudo dhclient ens4"
            "echo 'auto ens4' | sudo tee /etc/network/interfaces.d/ens4.cfg"
            "echo 'iface ens4 inet dhcp' | sudo tee -a /etc/network/interfaces.d/ens4.cfg"
            (str "echo '" host-id "' | sudo tee /etc/resource-id")
            "echo 'OVH-CLOUD' | sudo tee /etc/provider-id"
            (str "curl http://" (:inventory-host @ovh-conf) "/provision/v1/start/" host-id "/ovh-cloud")])))

(defn- instance-bootstrap!
  [instance]
  (swap! ovh-inventory assoc (keyword (:id instance)) instance)
  (save-inventory)
  (send-tags-request instance)
  (when-not (get @ovh-conf :only-inventory false)
    (let [host-id (str "ovh-" (:id instance))
        inventory-host (:inventory-host @ovh-conf)]
        (caller/add-command {:commands (mk-bootstrap-cmd host-id)
                             :user (:post-install-user @ovh-conf)
                             :method :ssh
                             :hosts (get-address instance 4 "public")}))))

(defn- update-instance!
  [instance]
  (swap! ovh-inventory assoc-in [(keyword (:id instance)) :flavorId] (:flavorId instance))
  (swap! ovh-inventory assoc-in [(keyword (:id instance)) :planCode] (:planCode instance))
  (swap! ovh-inventory assoc-in [(keyword (:id instance)) :name] (:name instance))
  (save-inventory)
  (send-tags-request instance))

(defn bootstrap
     [instance-id]
     (when-let [instance (get @ovh-inventory (keyword instance-id))]
         (put @ovh-queue [instance "bootstrap" nil])))

(defn- reboot-instance
  [instance-id]
  (cloud/reboot-instance (:project @ovh-conf) instance-id "hard"))

(defn reboot
  "Send reboot event"
  [instance-id]
  (when-not (ro?)
  (put @ovh-queue [instance-id "reboot" nil])))

(defn- rename-instance!
    [instance-id instance-name]
    (if-let [instance (get-instance instance-id)]
        (do
            (log/info "Set name" instance-name "to instance" instance-id)
            (cloud/rename-instance (:project-id @ovh-conf) (name instance-id) instance-name)
            (swap! ovh-inventory assoc-in [(keyword instance-id) :name] instance-name)
            (save-inventory))
        (log/warn "Instance" instance-id "not found")))

(defn rename
    "Send rename event"
    [instance-id name]
    (when-not (ro?)
        (put @ovh-queue [instance-id "rename" name])))

(defn- instance-operation!
  "Send bootstrap, reverse or delete operation"
  [[instance action params]]
  (when-not (ro?)
  (condp = action
    "delete" (delete-instance! instance)
    "bootstrap" (instance-bootstrap! instance)
    "reboot" (reboot-instance instance)
    "rename" (rename-instance! instance params)
    "update" (update-instance! instance)
    (log/info "unknow action for ovh cloud handler"))))

(defn get-service-state
    [region-state service]
    (reduce
        (fn [_ x] (when (= service (:name x)) (reduced (:status x))))
        nil
        (:services region-state)))

(defn refresh [_]
      (log/info "Refreshing OVH CLOUD")
      (when-let [slist (cloud/describe-instances (:project-id @ovh-conf))]
          (let [smap (into {} (map (fn [x] {(keyword (:id x)) true}) slist))]
          ;;detection of deleted instances
            (let [regions-state (atom {})
                  regions-instance-service-state (atom {})]
              (doseq [[k v] @ovh-inventory]
                (when-not (k smap)
                  ;;two case either region is into maintenance (so don't delele instance)
                  (let [region (:region (k @ovh-inventory))]
                    (when (nil? (get @regions-state region))
                        (let [region-state (cloud/region-state (:project-id @ovh-conf) region)]
                        (swap!
                            regions-instance-service-state
                            assoc
                                region
                                (get-service-state region-state "instance"))
                        (swap!
                            regions-state
                            assoc
                                region
                                (:status region-state))))
                    ;;not into maintenance delete instance
                    (if (and (= "UP" (get @regions-state region))
                            (= "UP" (get @regions-instance-service-state region)))
                        (do
                            (log/info "Deleting instance" (name k) "from ovh inventory")
                            (put @ovh-queue [v "delete" nil]))
                        (log/info
                            "Ignoring instance missing since region" region "is not up "
                            "(region: " (get @regions-state region) "/instance:"(get @regions-instance-service-state region) ")")))))))
            ;;detection of new instance
            (doseq [instance slist]
              (if (and (not (cached? instance)) (= "ACTIVE" (:status instance)))
                (do
                  (log/info "Adding instance" (:id instance) "to ovh-cloud queue")
                  (put @ovh-queue [instance "bootstrap" nil]))
                (when (= "ACTIVE" (:status instance))
                  (put @ovh-queue [instance "update" nil]))))))

;;loop to poll ovh api
(defn- start-ovh-loop!
  "get instance list and check inventory consistency
  add instance to queue if it is absent, remove deleted instance"
  []
  (when-not (or (:read-only @ovh-conf) (nil? (:refresh-period @ovh-conf)))
  (let [refresh-period (periodic-seq (t/now) (t/minutes (:refresh-period @ovh-conf)))]
  (log/info "[Refresh] starting OVH CLOUD refresh loop")
  (chime-at refresh-period
    refresh))))

;;loop to setup
(defn- start-op-consumer!
  "consum install queue"
  []
  (when-not (:read-only @ovh-conf)
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [ev (tke @ovh-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (instance-operation! ev)))
      "ovh cloud operation consumer")))

;;return an ovh view of our infrastructure
(defn get-ovh-inventory
  "Return ovh inventory"
  []
  (into [] (map (fn [[k v]] k) @ovh-inventory)))

(defn start-saver!
[]
(chime-at (periodic-seq (t/now) (t/seconds 5))
              (fn []
                  (when (u/save? last-save items-not-saved)
                  (save-inventory)))))

(defn start!
  []
  (if-not (or (nil? @ovh-conf) (ro?))
    [{:stop (start-ovh-loop!)} (start-op-consumer!) (start-saver!)]
    []))


;;init
(defn configure!
 "Main function"
 [conf]
 (reset! ovh-conf conf)
 (log/info "[OVH]" (:store @ovh-conf))
 (load-inventory!))
