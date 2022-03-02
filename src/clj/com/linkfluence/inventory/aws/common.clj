(ns com.linkfluence.inventory.aws.common
  (:require [clojure.string :as str]
            [clj-time.core :as t]
            [cheshire.core :as json]
            [com.linkfluence.utils :as u])
(:import [java.time Instant Duration]))

;;##########################################
;; Tag matching
;;##########################################

(def conf (atom nil))

(defn set-conf
  [config]
  (reset! conf config))

(defn get-conf
  []
  @conf)

(defn ro?
  []
  (:read-only @conf))

(defn get-service-store
  [service]
  (assoc (:store @conf) :key (str (:key (:store @conf)) "-" service)))

(defn aws-region
  [az]
  (let [mt (re-matches #"([a-z]{2}-[a-z]+-[0-9]{1})[a-z]*" (str/lower-case az))
          region (get mt 1)]
    (if-not (nil? region)
      region
      az)))

(defn short-az
  [az]
  (str (last az)))

(defn get-service-endpoint
 [region service]
 (assoc
   (get-in @conf [:regions region])
   :endpoint (name region)))


(defn tag-matcher
  "Check if an entity match a tag"
  [entity tag]
  (= (:value tag) ((keyword (:name tag)) entity)))

(defn tags-matcher
  "Check if an entity match the tags array"
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

(defn tags-binder
  [tags]
  (u/tags-binder (:tags-binding (get-conf)) tags))

(defn get-tags-from-entity-map
  "return tags of an instance object"
  [entity]
  (reduce (fn [acc x] (assoc acc (keyword (:key x)) (:value x))) {} (:tags entity)))

(defn generic-entity-matcher
  "Is an entity match the match arg using result of matching fn"
  [entity match matching-fn]
  (= match (matching-fn entity)))

(defn get-entities-filtered-by-tags
  [entities tags]
  (into {} (filter
    (fn [[k v]]
      (tags-matcher (get-tags-from-entity-map v) tags))
     entities)))

 (defn get-entities-tag-list
   [entities]
   (into []
     (keys
       (reduce (fn [acc [k v]]
                 (reduce
                   (fn [accu [ku vu]]
                       (assoc accu (name ku) true))
                   acc
                   (get-tags-from-entity-map v)))
                {}
                entities))))

(defn get-tag-value-from-entities
 [entities tag]
 (into []
   (keys
     (reduce (fn [acc val] (assoc acc val true)) {}
       (filter
           (fn [x] (not (nil? x)))
           (map
             (fn [[k v]] ((keyword tag) (get-tags-from-entity-map v)))
             entities))))))


(defn fuzz
  []
  (let [fu (+ 2 (rand-int (* 30 (:refresh-period @conf))))]
    (.plusSeconds (Instant/now) fu)))

(defn date-hack
  [data]
  (json/parse-string (json/generate-string data) true))
