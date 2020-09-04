(ns com.linkfluence.inventory.aws.elasticache
  (:require [chime :refer [chime-at]]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.core :as inventory]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [amazonica.aws.elasticache :as elasticache]
            [com.linkfluence.inventory.aws.common :refer :all])
  (:import [java.util.concurrent LinkedBlockingQueue]))


(def aws-inventory (atom {}))

(def ^LinkedBlockingQueue aws-queue (LinkedBlockingQueue.))


(defn load-inventory!
    []
    (when-let [inventory (store/load-map (get-service-store "elasticache"))]
      (reset! aws-inventory inventory)))

(defn save-inventory
    "Save on both local file and s3"
    []
    (when (= 0 (.size aws-queue))
      (store/save-map (get-service-store "elasticache") @aws-inventory)
      (u/fsync "aws/elasticache")))

(defn cached?
    "check if corresponding domain exist in cache inventory"
    [cluster-id]
    (not (nil? (get @aws-inventory (keyword cluster-id) nil))))

(defn cluster-id
    [cluster]
    (str (aws-region (:preferred-availability-zone cluster)) "-" (:cache-cluster-id cluster)))

(defn node-id
    [cluster node]
    (str "aws-cache-" (cluster-id cluster) "-" (:cache-node-id node)))

(defn get-aws-inventory
  "Retrieve a list of filtered instance or not"
  []
    (into [] (map (fn [[k v]] k) @aws-inventory)))

(defn get-cluster
  "Return an instance from inventory"
  [cluster-id]
  (get @aws-inventory (keyword cluster-id)))


(defn send-tags-request
  [cluster]
  (let [nodes (:cache-nodes cluster)]
    (if (:delete cluster)
        (doseq [node nodes]
            (inventory/add-inventory-event {:type "resource"
                               :provider "AWS"
                               :id (node-id cluster node)
                               :tags []
                               :delete true}))
        (doseq [node nodes]
          (let [tags (get-tags-from-entity-map node)]
            (inventory/add-inventory-event {:type "resource"
                               :provider "AWS"
                               :id (node-id cluster node)
                               :tags (filter (fn [x] (not (nil? x)))
                                        (concat
                                                  [{:name "provider" :value "AWS"}
                                                   {:name "AZ" :value (:customer-availability-zone node )}
                                                   {:name "REGION" :value (aws-region (:customer-availability-zone node ))}
                                                   {:name "SHORT_AZ" :value (short-az (:customer-availability-zone node ))}
                                                   {:name "FQDN" :value (get-in node [:endpoint :address])}
                                                   {:name "port" :value (get-in node [:endpoint :port])}
                                                   {:name "APP" :value (get-in cluster [:engine])}
                                                   {:name "APP_VERSION" :value (get-in cluster [:engine-version])}
                                                   {:name "aws_service" :value "elasticache"}
                                                   {:name "instance_type" :value (get-in cluster [:cache-node-type])}
                                                   {:name "cluster_id" :value (get-in cluster [:cache-cluster-id])}]
                                                   (tags-binder tags)))}))))))

(defn- update-aws-inventory!
 [cluster]
 (let [kid (keyword (cluster-id cluster))]
       (if (:delete cluster)
         (swap! aws-inventory dissoc kid)
         (if (:update cluster)
           (do
             (swap! aws-inventory assoc kid (dissoc (date-hack cluster) :update)))
           (swap! aws-inventory assoc kid (dissoc (date-hack cluster) :update))))
       (send-tags-request cluster)
       (save-inventory)))

(defn- get-node-arn
  [account region node]
  (let [addr (get-in node [:endpoint :address])
        id (first (str/split addr #"\."))]
        (str "arn:aws:elasticache:" region ":" account ":cluster:" id)))

(defn- get-node-with-tags
  [account region node]
  (let [arn (get-node-arn account region node)
        tags (try (:tag-list (elasticache/list-tags-for-resource
                      (get-service-endpoint region "elasticache")
                      :resource-name arn))
                      (catch Exception e
                        (log/error "Failed to get tags from nodes" e)
                        []))]
        (assoc node :tags tags :arn arn)))

(defn- add-tags-to-cluster-nodes
  [account region cluster]
  (assoc
    cluster
    :cache-nodes (doall
                    (map
                      (partial get-node-with-tags account region)
                      (:cache-nodes cluster)))))

(defn- get-clusters
    [region]
    (try
      (let [clusters (:cache-clusters (elasticache/describe-cache-clusters
                                    (get-service-endpoint region "elasticache")
                                    :show-cache-node-info true))]
            (if-let [account (:account-id (get-conf))]
              (doall
                (map
                  (partial add-tags-to-cluster-nodes account region)
                  clusters))
              clusters))

      (catch Exception e
        (log/error "Failed to retrieve aws cache-clusters for region " (get-in (get-conf) [:regions region :endpoint]))
          (throw (Exception. "Failed to retrieve aws cache-clusters")))))



(defn start-inventory-update-consumer!
"consum aws inventory queue"
[]
(u/start-thread!
    (fn [] ;;consume queue
      (when-let [cluster (.take aws-queue)]
        ;; extract queue and pids from :radarly and dissoc :radarly data
        (update-aws-inventory! cluster)))
    "aws elasticache inventory consumer"))

(defn- manage-cluster
  [cluster]
  (if (= "available" (get cluster :cache-cluster-status "unknown"))
            (if-not (cached? (cluster-id cluster))
              ;;add server
              (do
                (log/info "Adding cluster" (cluster-id cluster)  "to aws queue")
                (.put aws-queue cluster))
                ;;update server
                (do
                  (.put aws-queue (assoc cluster :update true))))
            ;;when state is terminated
            (when (cached? (cluster-id cluster))
              (log/info "Removing cluster" (cluster-id cluster))
              (.put aws-queue (assoc ((keyword (cluster-id cluster)) @aws-inventory) :delete true)))))

;;refresh function (can be invoke manually)
(defn refresh
  [_]
    (when-let [clist (try
                      (mapcat (fn [region](get-clusters region)) (keys (:regions (get-conf))))
                        (catch Exception e
                          nil))]
      (let [cmap (into {} (map (fn [x] {(keyword (cluster-id x)) true}) clist))]
        ;;detection of deleted servers
        (doseq [[k v] @aws-inventory]
          (when-not (k cmap)
            (log/info "Removing aws elasticache cluster" k)
            (.put aws-queue (assoc (k @aws-inventory) :delete true))))
        ;;detection of new server
        (doseq [cluster clist]
          (manage-cluster cluster)))))


;loop to poll aws api
(defn start-loop!
  "get clusters list and check inventory consistency
  add cluster to queue if it is absent, remove deleted cluster"
  []
  (let [refresh-period (periodic-seq (fuzz) (t/minutes (:refresh-period (get-conf))))]
  (log/info "[Refresh] starting refresh AWS loop")
  (chime-at refresh-period
    refresh)))
