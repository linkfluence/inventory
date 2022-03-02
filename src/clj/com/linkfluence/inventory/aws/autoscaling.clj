(ns com.linkfluence.inventory.aws.autoscaling
  (:require [chime.core :as chime :refer [chime-at]]
            [clojure.string :as str]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.core :as inventory]
            [clojure.tools.logging :as log]
            [amazonica.aws.autoscaling :as asg]
            [com.linkfluence.inventory.aws.common :refer :all]
            [com.linkfluence.inventory.queue :as queue :refer [put tke]])
(:import [java.time Instant Duration]))

(def aws-inventory (atom {}))

(def last-save (atom (System/currentTimeMillis)))
(def items-not-saved (atom 0))

(def aws-asg-queue (atom nil))


;;load and store inventory
(defn load-inventory!
  []
  (when-let [asg-inventory (store/load-map (get-service-store "asg"))]
    (reset! aws-inventory asg-inventory)))

(defn save-inventory
  "Save on both local file and s3"
  []
  (if (u/save? last-save items-not-saved)
    (do
        (store/save-map (get-service-store "asg") @aws-inventory)
        (u/reset-save! last-save items-not-saved)
        (u/fsync "aws/asg"))
    (swap! items-not-saved inc)))

(defn cached?
  "check if corresponding domain exist in cache inventory"
  [asg-id]
  (not (nil? (get @aws-inventory (keyword asg-id) nil))))

;;AWS API call for ec2 autoscalinggroup
(defn get-asgs
  "return all asg"
  [region]
  (try
  (loop [{:keys [next-token auto-scaling-groups]}
    (asg/describe-auto-scaling-groups (get-service-endpoint region "autoscaling"))]
    (if (nil? next-token)
    auto-scaling-groups
    (let [asg-desc-result
      (asg/describe-auto-scaling-groups
        (get-service-endpoint region "autoscaling") :next-token next-token)
        new-next-token
        (:next-token asg-desc-result)
        asgs
        (:auto-scaling-groups asg-desc-result)]
        (recur {:next-token
          new-next-token
          :auto-scaling-groups
          (concat auto-scaling-groups asgs)}))))
            (catch Exception e
              (log/error "Failed to retrieve aws asg for region" (name region))
                (throw (Exception. "Failed to retrieve aws instances")))))

(defn update-aws-inventory!
  [asg]
  (let [k-asg-id (keyword (:id asg))]
        (if (:delete asg)
          (swap! aws-inventory dissoc k-asg-id)
          (if (:update asg)
            (do
              (swap! aws-inventory assoc-in [k-asg-id :max-size] (:max-size asg))
              (swap! aws-inventory assoc-in [k-asg-id :min-size] (:min-size asg))
              (swap! aws-inventory assoc-in [k-asg-id :desired-capacity] (:desired-capacity asg))
              (swap! aws-inventory assoc-in [k-asg-id :instances] (:instances asg))
              (swap! aws-inventory assoc-in [k-asg-id :tags] (:tags asg))
              (swap! aws-inventory assoc-in [k-asg-id :launch-configuration-name] (:launch-configuration-name asg)))
            (swap! aws-inventory assoc k-asg-id (dissoc
                                                  (date-hack asg)
                                                   :update))))
        (save-inventory)))

(defn get-asgs-filtered-by-tags
  [tags]
  (get-entities-filtered-by-tags @aws-inventory tags))

(defn get-aws-inventory
  ([]
    (into [] (map (fn [[k v]] k) @aws-inventory)))
  ([tags]
    (into [] (map
      (fn [[k v]] k)
        (get-asgs-filtered-by-tags tags)))))

(defn get-asg-tags-list
  "get tag list extracted from all asg"
  []
  (get-entities-tag-list @aws-inventory))

(defn get-tag-value-from-asgs
  [tag]
  (get-tag-value-from-entities @aws-inventory tag))

(defn get-asg
  "Return an asg from inventory"
  [asg-name]
  (get @aws-inventory (keyword asg-name)))

(defn set-desired-capacity
  "update an asg desired capacity"
  [asg-name size]
  (if-not (ro?)
    (let [asg-data (get-asg asg-name)
        region (keyword (first (:availability-zone asg-data)))]
        (if (and
              (>= size (:min-size asg-data))
              (<= size (:max-size asg-data)))
      (try
        (asg/set-desired-capacity
          (get-service-endpoint region "autoscaling")
          :auto-scaling-group-name (:auto-scaling-group-name asg-data)
          :desired-capacity size)
          {:success "Update successful"}
          (catch Exception e
            (log/error "Fail to set asg desired capacity" e)
            {:error "An exception occured while setting desired capacity"}))
            (do
              (log/error "Size check fail")
              {:error "An exception occured while setting desired capacity"}))))
      {:error "aws read-only mode"})

(defn start-inventory-update-consumer!
  []
  "consum aws asg inventory queue"
  []
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [asg (tke @aws-asg-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (update-aws-inventory! asg)))
      "aws asg inventory consumer"))

;loop to poll aws api
(defn start-loop!
  "get autoscaling group list and check inventory consistency
  add instance to queue if it is absent, remove deleted instance"
  []
  (let [refresh-period (chime/periodic-seq (fuzz) (Duration/ofMinutes (:refresh-period (get-conf))))]
    (log/info "[ASG] starting refresh AWS asg loop")
    (chime-at refresh-period
    (fn [_]
      (try
      (loop [regions (keys (:regions (get-conf)))
             asgmap {}]
      (if-let [region (first regions)]
        (when-let [asglist (get-asgs region)]
          (let [asgmap* (merge (into {} (map (fn [x] {(keyword (str (name region) "-" (:auto-scaling-group-name x))) true}) asglist)) asgmap)]
            ;;detection of new asg
            (doseq [asg asglist]
              (let [asg-id (str (name region) "-" (:auto-scaling-group-name asg))]
                (if-not (cached? asg-id)
                  ;;add asg
                  (do
                    (log/info "Adding asg" asg-id  "to aws asg queue")
                    (put @aws-asg-queue (assoc asg :id asg-id)))
                    ;;update asg
                  (do
                    (put @aws-asg-queue (assoc asg :update true :id asg-id))))))
                    (recur (rest regions) asgmap*)))
            ;;detection of deleted asg
            (doseq [[k v] @aws-inventory]
              (when-not (k asgmap)
                (log/info "Removing asg" k)
                (put @aws-asg-queue (assoc (k @aws-inventory) :delete true))))))
                (catch Exception e
                  (log/error "Failed to retrieve aws ASG")))
                ))))
