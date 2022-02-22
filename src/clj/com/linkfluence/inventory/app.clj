(ns com.linkfluence.inventory.app
  (:import [java.util.concurrent LinkedBlockingQueue])
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [clj-http.client :as http]
            [cheshire.core :refer :all]
            [clojure.spec.alpha :as spec]
            [com.linkfluence.inventory.caller :as caller]
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.inventory.queue :as queue :refer [put tke]]))

;; queue for inventory update
(def ^LinkedBlockingQueue inventory-queue (LinkedBlockingQueue.))

;;this the main inventory for apps
(def apps (atom {}))
;;atom for conf
(def conf (atom nil))

(def bulk (atom nil))

(defn ro?
  []
  (:read-only @conf))

;;return queue size
(defn get-event-queue-size
    []
    (.size inventory-queue))


(defn load-inventory!
  []
  ;;applicable if we wan't to store something (ie : not during test)
  (when (:store @conf)
  ;load envs
  (when-let [as (store/load-map (assoc (:store @conf) :key "apps"))]
    (reset! apps as))))

;;store inventory backup
(defn save-inventory
  []
  ;;applicable if we wan't to store something (ie : not during test)
  (when (:store @conf)
  (when-not (ro?)
  ;save apps
  (store/save-map (assoc (:store @conf) :key "apps") @apps))))

 (defn post-event
    [master ev]
    (try
      (http/post
        (str "http://" master "/app/event")
        {:content-type :json :body (generate-string {:event ev})})
      true
        (catch Exception e
          false)))

(defn post-bulk
    [master]
    (try
      (http/post
        (str "http://" master "/app/events")
        {:content-type :json :body (generate-string {:events @bulk})})
      (reset! bulk nil)
      true
        (catch Exception e
          false)))

(defn send-event
    [master event]
    (cond
      (and (= 0 (.size inventory-queue)) (nil? @bulk))
      (loop []
        (when-not (post-event master event)
          (Thread/sleep 60000)
          (recur)))
      (and (= 0 (.size inventory-queue)) (not (nil? @bulk)))
      (do
        (swap! bulk conj event)
        (loop []
          (when-not (post-bulk master)
            (Thread/sleep 60000)
            (recur))))
      :else (if (nil? @bulk)
              (reset! bulk [event])
              (swap! bulk conj event))))

(defn tag-matcher
    "Check if an entity group/resource match a tag"
    [entity tag]
    (if-not (:not tag)
        (= (:value tag) ((keyword (:name tag)) entity))
        (not (= (:value tag) ((keyword (:name tag)) entity)))))

(defn tags-matcher
    "Check if an entity group/resource match the tags array"
    [entity tags]
    (if-not (= 0 (count tags))
        (loop [tgs tags
                m true]
            (if m
                (if-not (= 0 (count tgs))
                    (if (tag-matcher entity (first tgs))
                        (recur (next tgs) true)
                        false)
                    m)
                false))
    true))

;;add event Update
(defn add-inventory-event
  "add an event to update app inventory"
  [ev]
  (when (or
          (not (ro?))
          (not (nil? (get @conf :master nil))))
    (.put inventory-queue ev)))

;;Env mgt
(defn- create-env!
    "Create a new empty environment"
    [env desc]
    (swap! apps assoc (keyword env) {:description desc :app-tags {} :resource-tags {} :apps {}}))

(defn- update-env-tags!
    [env tags type]
    (doseq [tag tags]
        (if-not (:delete tag)
            (swap! apps assoc-in [(keyword env) type (keyword (:name tag))] (:value tag))
            (swap! apps update-in [(keyword env) type] dissoc (keyword (:name tag))))))

(defn- update-env!
    "Tag an environment with default app-tags and resource-tags"
    [env app-tags resource-tags]
    (update-env-tags! env (or app-tags []) :app-tags)
    (update-env-tags! env (or resource-tags []) :resource-tags))

(defn- delete-env!
    "Delete an environment"
    [env]
    (swap! apps dissoc (keyword env)))

;;Env getter
(defn env-exists?
    [env]
    (not (nil? (get @apps (keyword env) nil))))

(defn get-env
    [& [env]]
    (if (nil? env)
        (into [](keys @apps))
        (dissoc (get @apps (keyword env)) :apps)))

(defn get-env-apps
    [env]
    (get-in @apps [(keyword env) :apps]))

(defn- update-app-tags!
    [app env tags type]
    (doseq [tag tags]
        (if-not (:delete tag)
            (if-not (:append tag)
                (swap! apps assoc-in [(keyword env) :apps (keyword app) type (keyword (:name tag))] (:value tag))
                (let [current-value (get-in @apps [(keyword env) :apps (keyword app) type (keyword (:name tag))])
                      updated-value (cond
                                        (nil? current-value) (:value tag)
                                        (or (string? current-value) (number? current-value))
                                            (if-not (= (:value tag) current-value)
                                                [current-value (:value tag)]
                                                current-value)
                                        (or (list? current-value) (vector? current-value))
                                            (if-not (.contains current-value (:value tag))
                                                (into [] (concat current-value [(:value tag)]))
                                                current-value))]
                    (when-not (or (nil? updated-value) (= current-value updated-value))
                        (swap! apps assoc-in [(keyword env) :apps (keyword app) type (keyword (:name tag))] updated-value))))
            (swap! apps update-in [(keyword env) :apps (keyword app) type] dissoc (keyword (:name tag))))))

;;App tagging
(defn- update-app!
    "Create or update an application"
    [app env tags resource-tags]
    (update-app-tags! app env (or tags []) :tags)
    (update-app-tags! app env (or resource-tags []) :resource-tags))

(defn- delete-app!
    "Delete an application"
    [app env]
    (swap! apps update-in [(keyword env) :apps] dissoc (keyword app)))


;;App getter
(defn get-app-resources
    "Return resources associated to this app/env couple"
    [app env]
    (when-let [app-data (get-in @apps [(keyword env) :apps (keyword app)])]
        (let [env-res-tags (get-in @apps [(keyword env) :resource-tags])
              app-res-tags (:resource-tags app-data)]
          (inventory/get-resources (into []
                                    (map
                                        (fn [[k v]]
                                            {:name (name k) :value v})
                                        (merge env-res-tags app-res-tags)))))))

(defn get-app-resources-tag-value
    "Return specific tag of resources associated to this app (ex : FQDN)"
    [app env tag]
    (when-let [app-data (get-in @apps [(keyword env) :apps (keyword app)])]
        (let [env-res-tags (get-in @apps [(keyword env) :resource-tags])
              app-res-tags (:resource-tags app-data)]
              (inventory/get-tag-value-from-resources tag (into []
                                    (map
                                        (fn [[k v]]
                                            {:name (name k) :value v})
                                        (merge env-res-tags app-res-tags)))))))

(defn exists?
    [app env]
    (not (nil? (get-in @apps [(keyword env) :apps (keyword app)] nil))))

(defn get-app
    "Return an app"
    [app env]
    {:tags (get-in @apps [(keyword env) :apps (keyword app) :tags] {})
     :tags-from-env (get-in @apps [(keyword env) :app-tags] {})
     :resource-tags (get-in @apps [(keyword env) :apps (keyword app) :resource-tags] {})
     :resource-tags-from-env (get-in @apps [(keyword env) :resource-tags] {})})

(defn get-app-tags
    "Return tags of a specific app"
    [app env]
    {:tags (get-in @apps [(keyword env) :apps (keyword app) :tags] {})
     :tags-from-env (get-in @apps [(keyword env) :app-tags] {})})

(defn get-app-resource-tags
    "Return tags use to match resource"
    [app env]
    {:resource-tags (get-in @apps [(keyword env) :apps (keyword app) :resource-tags] {})
     :resource-tags-from-env (get-in @apps [(keyword env) :resource-tags] {})})

;;App lifecycle
(defn- app-systemctl
    [app env action]
    (let [resources (get-app-resources-tag-value app env (or (:lifecycle-tag @conf) "FQDN"))]
        (log/info "[app-systemctl] will do" action "on" resources "for" app "on env" env)
        (doseq [resource resources]
            (caller/add-command {:commands [(str "systemctl " action " " app " --no-pager")]
                                 :method :ssh
                                 :hosts resource
                                 :sudo true}))))

(defn submit-action
    [app env action]
    (condp = action
        "start" (app-systemctl app env "start")
        "stop" (app-systemctl app env "stop")
        "restart" (app-systemctl app env "restart")
        "reload" (app-systemctl app env "reload")
        "status" (app-systemctl app env "status")
        nil))

(defn update-app-with-ev!
    [ev]
    (when-not
        (or (nil? (:app ev))
            (str/includes? (name (:app ev)) " ")
            (str/includes? (name (:app ev)) ";")
            (str/includes? (name (:app ev)) "\"")
            (str/includes? (name (:app ev)) "'"))
    (if (:delete ev)
        (delete-app! (:app ev) (:env ev))
        (update-app! (:app ev) (:env ev) (:tags ev) (:resource-tags ev)))
    (when (= 0 (.size inventory-queue))
      (save-inventory)
      (u/fsync "app"))))

(defn update-env-with-ev!
    [ev]
    (if (:create ev)
        (create-env! (:env ev) (:description ev))
        (if (:delete ev)
            (delete-env! (:env ev))
            (update-env! (:env ev) (:app-tags ev) (:resource-tags ev))))
    (when (= 0 (.size inventory-queue))
      (save-inventory)
      (u/fsync "app")))

(defn- update-inventory!
  "generic inventory update"
  [ev]
  (if-not (ro?)
    (condp = (:type ev)
      "app" (update-app-with-ev! ev)
      "env" (update-env-with-ev! ev))
    (when-let [master (get @conf :master nil)]
      (send-event master ev))))

(defn- start-inventory-consumer!
  []
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [ev (.take inventory-queue)]
          (update-inventory! ev)))
      "Inventory apps Consumer"))

(defn start!
  []
  (if (or
        (not (ro?))
        (and (ro?)
              (not (nil? (get @conf :master)))))
    [(start-inventory-consumer!)]
    []))

(defn configure!
  [inventory-conf]
  (reset! conf inventory-conf)
  (load-inventory!))
