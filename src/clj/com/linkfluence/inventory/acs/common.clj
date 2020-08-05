(ns com.linkfluence.inventory.acs.common
  (:require [aliyuncs.core :as acs]
            [clojure.string :as str]
            [clj-time.core :as t]
            [cheshire.core :as json]
            [com.linkfluence.utils :as u]))

(def conf (atom nil))

(def client-map (atom {}))

(defn set-conf
  [config]
  (reset! conf config))

(defn get-conf
  []
  @conf)

(defn ro?
  []
  (:read-only @conf))

(def tags-binder
  (partial u/tags-binder (:tags-binding (get-conf))))

(defn date-hack
  [data]
  (json/parse-string (json/generate-string data) true))

(defn short-az
  [az]
  (str (last az)))

(defn get-tags-from-entity-map
    "return tags of an instance object"
    [entity]
    (reduce (fn [acc x] (assoc acc (keyword (:tagKey x)) (:tagValue x))) {} (:tags entity)))

(defn get-service-store
  [service]
  (assoc (:store @conf) :key (str (:key (:store @conf)) "-" service)))

(defn get-client
  [region]
  (if-let [region-client (get @client-map region)]
    region-client
    (when-let [creds-map (get-in @conf [:regions region])]
      (let [{:keys [name access-key secret-key]} creds-map
            region-client* (acs/mk-acs-client name access-key secret-key)]
            (swap! client-map assoc region region-client*)
            region-client*))))
