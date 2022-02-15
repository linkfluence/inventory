(ns com.linkfluence.inventory.gcp.instance
  (:require [clojure.string :as str]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clj-gcloud.compute.instance :as gci]
            [com.linkfluence.inventory.gcp.common :refer :all]
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u])
  (:import [java.util.concurrent LinkedBlockingQueue]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2020 / Adot 2020 / Jean-baptiste Besselat 2020

;;Handler for ali cloud ECS
(def gcpi-inventory (atom {}))

(def last-save (atom (System/currentTimeMillis)))
(def item-not-save (atom 0))

(def ^LinkedBlockingQueue gcpi-queue (LinkedBlockingQueue.))

(defn load-inventory!
  []
  (when-let [inventory (store/load-map (get-service-store "vm"))]
    (reset! gcpi-inventory inventory)))

(defn save-inventory
  "Save on both local file and s3"
  []
  (when (= 0 (.size gcpi-queue))
    (store/save-map (get-service-store "vm") @gcpi-inventory)
    (u/fsync "gcp/instance")))

(defn- get-instances
  "Retrieve instance"
  [project zone]
  (gci/list-all (client project) project zone))

(defn get-gcpi-inventory
  "Retrieve a list of filtered instance or not"
  ([]
    (into [] (map (fn [[k v]] k) @gcpi-inventory))))

(defn cached?
  "check if corresponding domain exist in cache inventory"
  [instance-id]
  (not (nil? (get @gcpi-inventory (keyword (str instance-id)) nil))))

(defn get-instance
  "Return an instance from inventory"
  [instance-id]
  (get @gcpi-inventory (keyword instance-id)))

(defn manage-instance
  [instance]
  (if-not (= "Terminated" (get-in instance [:status] "Unknown"))
            (if-not (cached? (:id instance))
              ;;add server
              (do
                (log/info "Adding instance" (:id instance)  "to gcpi queue")
                (.put gcpi-queue [instance "lifecycle" nil]))
                ;;update server
                (do
                  (.put gcpi-queue [(assoc instance :update true) "lifecycle" nil])))
            ;;when state is terminated
            (when (cached? (:id instance))
              (log/info "Removing instance" (:id instance))
              (.put gcpi-queue [(assoc instance :delete true) "lifecycle" nil]))))

(defn refresh-instance
  [project zone instance-id]
    (when-let [instance (gci/get (client project) project zone instance-id)]
    (manage-instance instance)))

(defn send-tags-request
  [instance]
  (let [tags (:labels instance)
        iface (first (:networkInterfaces instance))
        data-access (first (:accessConfigs iface))
        public-ip (when (and
                          (some? data-access)
                          (= (:type data-access) "ONE_TO_ONE_NAT"))
                    (:natIP data-access))]
    (if (:delete instance)
      (inventory/add-inventory-event {:type "resource"
                               :provider "GCP"
                               :id (str "gcp-" (:id instance))
                               :tags []
                               :delete true})
      (inventory/add-inventory-event {:type "resource"
                               :provider "GCP"
                               :id (str "gcp-" (:id instance))
                               :tags (filter (fn [x] (not (nil? x))) (into [] (concat [{:name "provider" :value "GCP"}
                                                   {:name "AZ" :value (az (get-in instance [:zone]))}
                                                   {:name "REGION" :value (region (get-in instance [:zone]))}
                                                   {:name "SHORT_AZ" :value (short-az (get-in instance [:zone]))}
                                                   {:name "gcp_service" :value "ce"}
                                                   {:name "instance_type"
                                                    :value (last
                                                              (str/split (get-in instance [:machineType]) #"/"))}
                                                   (when public-ip
                                                      {:name "publicIp" :value public-ip})
                                                   {:name "privateIp" :value (:networkIP iface)}]
                                                    (tags-binder (tags-value-morpher tags)))))}))))

(defn update-gcpi-inventory!
  [instance]
  (let [kid (keyword (str (:id instance)))]
        (if (:delete instance)
          (swap! gcpi-inventory dissoc kid)
          (if (:update instance)
            (do
              (swap! gcpi-inventory assoc-in [kid :labels] (:labels instance))
              (swap! gcpi-inventory assoc-in [kid :machineType] (:machineType instance)))
            (swap! gcpi-inventory assoc kid (dissoc instance :update))))
        (send-tags-request instance)
        (save-inventory)))

;;refresh function (can be invoke manually)
(defn refresh
  [_]
    (log/info "Refreshing GCP instances")
    (let [projects (:projects (get-conf))
          futur-ilist (future
                     (try
                      (mapcat (fn [project]
                                (mapcat (fn [zone] (get-instances (:id project) zone)) (:zones project)))
                                projects)
                        (catch Exception e
                            (log/error "Fail to get instances" e)
                          nil)))]
    (if-let [ilist (deref futur-ilist 60000 nil)]
      (let [imap (into {} (map (fn [x] {(keyword (str (:id x))) true}) ilist))]
        ;;detection of deleted servers
        (doseq [[k v] @gcpi-inventory]
          (when-not (k imap)
            (log/info "Removing instance" k)
            (.put gcpi-queue [(assoc (k @gcpi-inventory) :delete true) "lifecycle" nil])))
        ;;detection of new server
        (doseq [instance ilist]
          (manage-instance instance)))
          (log/warn "Get instance timeout"))))


(defn start-inventory-update-consumer!
    "consum gcp instance inventory queue"
    []
    (u/start-thread!
        (fn [] ;;consume queue
          (when-let [[instance op params] (.take gcpi-queue)]
            ;; extract queue and pids from :radarly and dissoc :radarly data
            (when (= "lifecycle" op)
                (update-gcpi-inventory! instance))))
        "gcp instance inventory consumer"))

(defn start-loop! []
  (let [refresh-period (periodic-seq (t/now) (t/minutes (:refresh-period (get-conf))))]
    (log/info "[Refresh] starting refresh gcpi loop")
    (chime-at refresh-period
        refresh)))
