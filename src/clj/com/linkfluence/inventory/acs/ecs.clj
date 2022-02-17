(ns com.linkfluence.inventory.acs.ecs
  (:require [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.core :as inventory]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [com.linkfluence.inventory.acs.common :refer :all]
            [aliyuncs.ecs.instance :as ecsi])
  (:import [java.util.concurrent LinkedBlockingQueue]))

;;Handler for ali cloud ECS
(def acs-inventory (atom {}))

(def last-save (atom (System/currentTimeMillis)))
(def item-not-saved (atom 0))

(def acs-queue (atom nil))

(defn load-inventory!
  []
  (when-let [inventory (store/load-map (get-service-store "ecs"))]
    (reset! acs-inventory inventory)))

(defn save-inventory
  "Save on both local file and s3"
  []
  (if (u/save? last-save item-not-saved)
    (do
        (store/save-map (get-service-store "ecs") @acs-inventory)
        (u/fsync "acs/ecs"))
    (swap! item-not-saved inc)))

(defn get-acs-inventory
  "Retrieve a list of filtered instance or not"
  ([]
    (into [] (map (fn [[k v]] k) @acs-inventory))))

(defn cached?
  "check if corresponding domain exist in cache inventory"
  [instance-id]
  (not (nil? (get @acs-inventory (keyword instance-id) nil))))


(defn- get-instances
  "Retrieve instance"
  [region]
    (loop [instances []
           page 1]
          (let [cpil (:instances (ecsi/describe-instances (get-client region) {:page page :page-size 20}))]
            (if-not (= 0 (count cpil))
                (recur (concat instances cpil) (inc page))
                instances))))

(defn get-instance
  "Return an instance from inventory"
  [instance-id]
  (get @acs-inventory (keyword instance-id)))


(defn manage-instance
  [instance]
  (if-not (= "Deleted" (get-in instance [:status] "Running"))
            (if-not (cached? (:instanceId instance))
              ;;add server
              (do
                (log/info "Adding instance" (:instanceId instance)  "to acs queue")
                (put @acs-queue [instance "lifecycle" nil]))
                ;;update server
                (do
                  (put @acs-queue [(assoc instance :update true) "lifecycle" nil])))
            ;;when state is terminated
            (when (cached? (:instanceId instance))
              (log/info "Removing instance" (:instanceId instance))
              (put @acs-queue [(assoc instance :delete true) "lifecycle" nil]))))

(defn refresh-instance
  [region instance-id]
    (when-let [instance (first
                            (:instances
                                (ecsi/describe-instance (get-client region) instance-id)))]
    (manage-instance instance)))

(defn rename-request
    [instance-id instance-name]
    (when-let [instance (get @acs-inventory (keyword instance-id) nil)]
        (put @acs-queue [instance-id "rename" instance-name])))

(defn rename-acs-instance!
    [instance-id instance-name]
    (when-let [instance (get @acs-inventory (keyword instance-id) nil)]
        (log/info "Renaming acs instance" instance-id "to" instance-name)
        (let [region (:regionId instance)]
            (ecsi/rename-instance (get-client region) instance-id instance-name)
            (refresh-instance region instance-id))))

(defn send-tags-request
  [instance]
  (let [tags (get-tags-from-entity-map instance)]
    (if (:delete instance)
      (inventory/add-inventory-event {:type "resource"
                               :provider "ACS"
                               :id (:instanceId instance)
                               :tags []
                               :delete true})
      (inventory/add-inventory-event {:type "resource"
                               :provider "ACS"
                               :id (:instanceId instance)
                               :tags (filter (fn [x] (not (nil? x))) (into [] (concat [{:name "provider" :value "ACS"}
                                                   {:name "AZ" :value (get-in instance [:zoneId])}
                                                   {:name "REGION" :value (get-in instance [:regionId])}
                                                   {:name "SHORT_AZ" :value (short-az (get-in instance [:zoneId]))}
                                                   {:name "acs_service" :value "ecs"}
                                                   {:name "instance_type" :value (get-in instance [:instanceType])}
                                                   (when (first (:publicIpAddress instance))
                                                      {:name "publicIp" :value (first (:publicIpAddress instance))})
                                                   {:name "privateIp" :value (:primaryIpAddress (first (:networkInterfaces instance)))}]
                                                    (tags-binder tags))))}))))

(defn update-acs-inventory!
  [instance]
  (let [kid (keyword (:instanceId instance))]
        (if (:delete instance)
          (swap! acs-inventory dissoc kid)
          (if (:update instance)
            (do
              (swap! acs-inventory assoc-in [kid :tags] (:tags instance))
              (swap! acs-inventory assoc-in [kid :instanceType] (:instanceType instance)))
            (swap! acs-inventory assoc kid (dissoc (date-hack instance) :update))))
        (send-tags-request instance)
        (save-inventory)))

;;refresh function (can be invoke manually)
(defn refresh
  [_]
    (log/info "Refreshing ACS")
    (let [futur-ilist (future
                     (try
                      (mapcat (fn [region] (get-instances region)) (keys (:regions (get-conf))))
                        (catch Exception e
                            (log/error "Fail to get instances" e)
                          nil)))]
    (if-let [ilist (deref futur-ilist 60000 nil)]
      (let [imap (into {} (map (fn [x] {(keyword (:instanceId x)) true}) ilist))]
        ;;detection of deleted servers
        (doseq [[k v] @acs-inventory]
          (when-not (k imap)
            (log/info "Removing instance" k)
            (put @acs-queue [(assoc (k @acs-inventory) :delete true) "lifecycle" nil])))
        ;;detection of new server
        (doseq [instance ilist]
          (manage-instance instance)))
          (log/warn "Get instance timeout"))))

(defn start-inventory-update-consumer!
    "consum acs inventory queue"
    []
    (u/start-thread!
        (fn [] ;;consume queue
          (when-let [[instance op params] (take @acs-queue)]
            ;; extract queue and pids from :radarly and dissoc :radarly data
            (when (= "lifecycle" op)
                (update-acs-inventory! instance))
            (when (= "rename" op)
                (rename-acs-instance! instance params))))
        "acs ecs inventory consumer"))

(defn start-loop!
  []
  (let [refresh-period (periodic-seq (t/now) (t/minutes (:refresh-period (get-conf))))]
    (log/info "[Refresh] starting refresh acs loop")
    (let [stop-fn-refresh (chime-at refresh-period
        refresh)
          stop-fn-save (chime-at (periodic-seq (t/now) (t/seconds 5)))]
        (fn []
            (stop-fn-refresh)
            (stop-fn-save)))))
