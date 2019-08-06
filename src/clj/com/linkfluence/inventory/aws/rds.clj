(ns com.linkfluence.inventory.aws.rds
  (:require [chime :refer [chime-at]]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.core :as inventory]
            [clojure.tools.logging :as log]
            [amazonica.aws.rds :as rds]
            [com.linkfluence.inventory.aws.common :refer :all])
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def aws-inventory (atom {}))

(def ^LinkedBlockingQueue aws-queue (LinkedBlockingQueue.))

;;load and store inventory
(defn load-inventory!
  []
  (when-let [asg-inventory (store/load-map (get-service-store "rds"))]
    (reset! aws-inventory asg-inventory)))

(defn save-inventory
  "Save on both local file and s3"
  []
  (when (= 0 (.size aws-queue))
    (store/save-map (get-service-store "rds") @aws-inventory)
    (u/fsync "aws/rds")))

(defn cached?
  "check if corresponding domain exist in cache inventory"
  [rds-id]
  (not (nil? (get @aws-inventory (keyword rds-id) nil))))

(defn send-tags-request
  [instance]
  (let [tags (get-tags-from-entity-map instance)]
    (if (:delete instance)
      (inventory/add-inventory-event {:type "resource"
                               :provider "AWS"
                               :id (:unique-dbinstance-identifier instance)
                               :tags []
                               :delete true})
      (inventory/add-inventory-event {:type "resource"
                               :provider "AWS"
                               :id (:unique-dbinstance-identifier instance)
                               :tags (filter (fn [x] (not (nil? x))) (into [] (concat [{:name "provider" :value "AWS"}
                                                   {:name "AZ" :value (:availability-zone instance)}
                                                   {:name "REGION" :value (name (:region instance))}
                                                   {:name "SHORT_AZ" :value (short-az (:availability-zone instance))}
                                                   {:name "FQDN" :value (get-in instance [:endpoint :address])}
                                                   {:name "APP" :value (:engine instance)}
                                                   {:name "APP_VERSION" :value (:engine-version instance)}
                                                   {:name "aws_service" :value "rds"}
                                                   {:name "dbname" :value (:dbname instance)}
                                                   {:name "dbinstance-identifier" :value (:dbinstance-identifier instance)}]
                                                    (tags-binder tags))))}))))

(defn update-aws-inventory!
  "update rds inventory"
  [instance]
  (let [krds (keyword (:unique-dbinstance-identifier instance))]
    (if (:delete instance)
      ;;handle deletion
      (do
        (swap! aws-inventory dissoc krds)
        (send-tags-request instance))
      (let [tags (try (:tag-list (rds/list-tags-for-resource
                        (get-service-endpoint (keyword (:region instance)) "rds")
                        :resource-name (:dbinstance-arn instance)))
                    (catch Exception e
                      nil))
          instance* (assoc instance :tags tags)]
          (if (:update instance*)
            (do
              (when (not (nil? tags))
                (swap! aws-inventory assoc-in [krds :tags] tags))
              (swap! aws-inventory assoc-in [krds :engine-version] (:engine-version instance))
              (swap! aws-inventory assoc-in [krds :availability-zone] (:availability-zone instance))
              (swap! aws-inventory assoc-in [krds :secondary-availability-zone] (:secondary-availability-zone instance))
              (swap! aws-inventory assoc-in [krds :backup-retention-period] (:backup-retention-period instance))
              (swap! aws-inventory assoc-in [krds :dbinstance-class] (:dbinstance-class instance))
              (send-tags-request instance*))
            (swap! aws-inventory assoc krds (date-hack instance*)))
          (send-tags-request instance*)))
  (save-inventory)))

(defn get-db-instances-from-region
  [region]
  (try
    (map
      (fn [x]
        (assoc
          x
          :unique-dbinstance-identifier
          (str "aws-rds-" (name region) "-" (:dbinstance-identifier x))
          :region region))
      (:dbinstances
        (rds/describe-db-instances
          (get-service-endpoint region "rds"))))
  (catch Exception e
    (log/error "Failed to retrieve aws rds instances" (name region))
      (throw (Exception. "Failed to retrieve aws rds instances")))))

(defn get-db-instances
  [regs]
  (loop [regions regs
         db-instances []]
    (if-let [region (first regions)]
      (if-not (nil? db-instances)
        (if-let [region-db-instances (get-db-instances-from-region (first regions))]
          (recur (rest regions) (concat db-instances region-db-instances))
          nil)
        nil)
      db-instances)))

(defn get-db-instances-filtered-by-tags
  [tags]
  (get-entities-filtered-by-tags @aws-inventory tags))

(defn get-aws-inventory
  "Retrieve a list of filtered instance or not"
  ([]
    (into [] (map (fn [[k v]] k) @aws-inventory)))
  ([tags]
    (into [] (map
      (fn [[k v]] k)
        (get-db-instances-filtered-by-tags tags)))))

(defn get-db-instance
  "Return a db instance from inventory"
  [db-id]
  (get @aws-inventory (keyword db-id)))

(defn get-db-instance-tags
  "Return tag maps from a db instance in inventory"
  [db-id]
  (if-let [db-instance (get @aws-inventory (keyword db-id) nil)]
    (get-tags-from-entity-map db-instance)
    {}))

(defn start-inventory-update-consumer!
  "consum aws inventory queue"
  []
    (u/start-thread!
      (fn [] ;;consume queue
        (when-let [instance (.take aws-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (update-aws-inventory! instance)))
      "aws rds inventory consumer"))

;loop to poll aws api
(defn start-loop!
  "get rds instances/endpoints list and check inventory consistency
  add rds instance to queue if it is absent, remove deleted rds instance"
  []
  (let [refresh-period (periodic-seq (fuzz) (t/minutes (:refresh-period (get-conf))))]
  (log/info "[Refresh] starting refresh AWS loop")
  (chime-at refresh-period
    (fn [_]
        (when-let [db-instances (try
                                  (get-db-instances (keys (:regions (get-conf))))
                                  (catch Exception e
                                    nil))]
          (let [dbimap (into {} (map (fn [x] {(keyword (:unique-dbinstance-identifier x)) true}) db-instances))]
            ;;remove deleted db instances
            (doseq [[k v] @aws-inventory]
              (when-not (k dbimap)
                (log/info "Removing db-instance" k)
                (.put aws-queue (assoc (k @aws-inventory) :delete true))))
            ;;add
            (doseq [instance db-instances]
              (if (cached? (:unique-dbinstance-identifier instance))
                ;;update
                (do
                  (.put aws-queue (assoc instance :update true)))
                ;;add
                (do
                  (log/info "Adding db-instance" (:unique-dbinstance-identifier instance)  "to aws queue")
                  (.put aws-queue instance))))))))))
