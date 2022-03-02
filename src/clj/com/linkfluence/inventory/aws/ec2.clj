(ns com.linkfluence.inventory.aws.ec2
  (:require [chime.core :as chime :refer [chime-at]]
            [clojure.string :as str]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.core :as inventory]
            [clojure.tools.logging :as log]
            [amazonica.aws.ec2 :as ec2]
            [com.linkfluence.inventory.aws.common :refer :all]
            [com.linkfluence.inventory.queue :as queue :refer [put tke]])
    (:import [java.time Instant Duration]))


(def aws-inventory (atom {}))

(def last-save (atom (System/currentTimeMillis)))
(def items-not-saved (atom 0))

(def aws-queue (atom nil))

(defn load-inventory!
  []
  (when-let [inventory (store/load-map (get-service-store "ec2"))]
    (reset! aws-inventory inventory)))

(defn save-inventory
  "Save on both local file and s3"
  []
  (if (u/save? last-save items-not-saved)
    (do
        (store/save-map (get-service-store "ec2") @aws-inventory)
        (u/reset-save! last-save items-not-saved)
        (u/fsync "aws/ec2"))
    (swap! items-not-saved inc)))

(defn cached?
  "check if corresponding domain exist in cache inventory"
  [instance-id]
  (not (nil? (get @aws-inventory (keyword instance-id) nil))))

;;AWS API call for ec2 instances
(defn get-reservations
  "Retrieve instances reservations"
  ([region]
    (try
      (:reservations (ec2/describe-instances (get-service-endpoint region "ec2")))
      (catch Exception e
        (log/error "Failed to retrieve aws instances for region " (get-in (get-conf) [:regions region :endpoint]))
          (throw (Exception. "Failed to retrieve aws instances")))))
  ([region filters]
    (try
      (:reservations (ec2/describe-instances (get-service-endpoint region "ec2") :filters filters))
      (catch Exception e
        (log/error "Failed to retrieve aws instances" (get-in (get-conf) [:regions region :endpoint]))
          (throw (Exception. "Failed to retrieve aws instances"))))))

(defn extract-instances
  "Extract instances from a reservations list"
  [reservations]
  (if-not (nil? reservations)
    (into [] (reduce (fn [acc x] (concat (:instances x) acc)) [] reservations))
    nil))

(defn get-instances
  "Retrieve instance"
  ([region]
    (extract-instances (get-reservations region)))
  ([region filters]
    (extract-instances (get-reservations region filters))))

(defn send-tags-request
  [instance]
  (let [tags (get-tags-from-entity-map instance)]
    (if (:delete instance)
      (inventory/add-inventory-event {:type "resource"
                               :provider "AWS"
                               :id (:instance-id instance)
                               :tags []
                               :delete true})
      (inventory/add-inventory-event {:type "resource"
                               :provider "AWS"
                               :id (:instance-id instance)
                               :tags (filter (fn [x] (not (nil? x))) (into [] (concat [{:name "provider" :value "AWS"}
                                                   {:name "AZ" :value (get-in instance [:placement :availability-zone])}
                                                   {:name "REGION" :value (aws-region (get-in instance [:placement :availability-zone]))}
                                                   {:name "SHORT_AZ" :value (short-az (get-in instance [:placement :availability-zone]))}
                                                   {:name "aws_service" :value "ec2"}
                                                   {:name "instance_type" :value (get-in instance [:instance-type])}
                                                   (when (:public-ip-address instance)
                                                      {:name "publicIp" :value (:public-ip-address instance)})
                                                   {:name "privateIp" :value (:private-ip-address instance)}]
                                                    (tags-binder tags))))}))))

(defn update-aws-inventory!
  [instance]
  (let [kid (keyword (:instance-id instance))]
        (if (:delete instance)
          (swap! aws-inventory dissoc kid)
          (if (:update instance)
            (do
              (swap! aws-inventory assoc-in [kid :tags] (:tags instance))
              (swap! aws-inventory assoc-in [kid :instance-type] (:instance-type instance)))
            (swap! aws-inventory assoc kid (dissoc (date-hack instance) :update))))
        (send-tags-request instance)
        (save-inventory)))


(defn get-instances-filtered-by-tags
  [tags]
  (get-entities-filtered-by-tags @aws-inventory tags))

(defn get-instances-filtered-by-az
  "Return instances which match az"
 [az]
 (into {} (filter
   (fn [[k v]]
     (generic-entity-matcher v az (fn [e] (:availability-zone (:placement e)))))
    @aws-inventory)))

(defn get-aws-inventory
  "Retrieve a list of filtered instance or not"
  ([]
    (into [] (map (fn [[k v]] k) @aws-inventory)))
  ([tags]
    (into [] (map
      (fn [[k v]] k)
        (get-instances-filtered-by-tags tags)))))

(defn get-aws-inventory-by-az
  "Retrieve a list of filtered instance or not"
  ([]
    (into [] (map (fn [[k v]] k) @aws-inventory)))
  ([az]
    (if-not (nil? az)
      (into [] (map
        (fn [[k v]] k)
          (get-instances-filtered-by-az az)))
      (get-aws-inventory-by-az))))

(defn get-instance-tags-list
  "get tag list extracted from all instances"
  []
  (get-entities-tag-list @aws-inventory))

(defn get-tag-value-from-instances
  [tag]
  (get-tag-value-from-entities @aws-inventory tag))

(defn get-stats-generic
 ([st]
   (get-stats-generic st nil))
 ([st tags]
   (let [inv (if (nil? tags)
               @aws-inventory
               (get-instances-filtered-by-tags tags))]
   (into {}
     (reduce (fn [acc val]
       (if-not (nil? val)
         (assoc acc val (inc (get acc val 0)))
         (assoc acc "not_defined" (inc (get acc "not_defined" 0))))) {}
           (map
             (fn [[k v]] (st v))
               inv))))))

(defn get-instance-ami-stats
  ([]
    (get-stats-generic :image-id))
  ([tags]
    (get-stats-generic :image-id tags)))


(defn get-instance-type-stats
  ([]
    (get-stats-generic :instance-type))
  ([tags]
    (get-stats-generic :instance-type tags)))

(defn get-az-stats
  []
  (get-stats-generic (fn [v] (:availability-zone (:placement v)))))

(defn get-subnet-stats
  []
  (get-stats-generic :subnet-id))

(defn get-state-stats
  []
  (get-stats-generic (fn [v] (:name (:state v)))))

(defn get-instance
  "Return an instance from inventory"
  [instance-id]
  (get @aws-inventory (keyword instance-id)))

(defn get-instance-tags
  "Return tag maps from an instance in inventory"
  [instance-id]
  (if-let [instance (get @aws-inventory (keyword instance-id) nil)]
    (get-tags-from-entity-map instance)
    {}))

(defn start-inventory-update-consumer!
  "consum aws inventory queue"
  []
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [instance (tke @aws-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (update-aws-inventory! instance)))
      "aws ec2 inventory consumer"))

(defn manage-instance
  [instance]
  (if-not (= "terminated" (get-in instance [:state :name] "running"))
            (if-not (cached? (:instance-id instance))
              ;;add server
              (do
                (log/info "Adding instance" (:instance-id instance)  "to aws queue")
                (put @aws-queue instance))
                ;;update server
                (do
                  (put @aws-queue (assoc instance :update true))))
            ;;when state is terminated
            (when (cached? (:instance-id instance))
              (log/info "Removing instance" (:instance-id instance))
              (put @aws-queue (assoc instance :delete true)))))

(defn refresh-instance
  [region instance-id]
    (when-let [instance (first
      (extract-instances
        (:reservations
          (ec2/describe-instances
            (get-service-endpoint region "ec2")
            :filters [{:name "instance-id" :values [instance-id]}]))))]
    (manage-instance instance)))

;;refresh function (can be invoke manually)
(defn refresh
  [_]
    (when-let [ilist (try
                      (mapcat (fn [region](get-instances region)) (keys (:regions (get-conf))))
                        (catch Exception e
                          nil))]
      (let [imap (into {} (map (fn [x] {(keyword (:instance-id x)) true}) ilist))]
        ;;detection of deleted servers
        (doseq [[k v] @aws-inventory]
          (when-not (k imap)
            (log/info "Removing instance" k)
            (put @aws-queue (assoc (k @aws-inventory) :delete true))))
        ;;detection of new server
        (doseq [instance ilist]
          (manage-instance instance)))))

;loop to poll aws api
(defn start-loop!
  "get instances list and check inventory consistency
  add instance to queue if it is absent, remove deleted instance"
  []
  (let [refresh-period (chime/periodic-seq (fuzz) (Duration/ofMinutes (:refresh-period (get-conf))))]
  (log/info "[Refresh] starting refresh AWS loop")
  (chime-at refresh-period
    refresh)))
