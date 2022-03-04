(ns com.linkfluence.store
  (:require [com.linkfluence.utils :as utils]
            [com.linkfluence.store.s3 :as s3]
            [com.linkfluence.store.oss :as oss]
            [com.linkfluence.store.gcs :as gcs]
            [com.linkfluence.store.file :as fi]
            [com.linkfluence.store.consul :as consul]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clj-yaml.core :as yaml])
  (:import [java.util.concurrent LinkedBlockingQueue]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

;This namespace is dedicated to datastore which will support:
; - yml save into file
; - yml save into s3
; - jsons save into db (h2 or pg)

;; Handler ovh, aws, gce, call generic store-yml or save function with their save conf to store data.
;; They call load-yaml, and load with their store conf to load data

(def ^LinkedBlockingQueue store-queue (LinkedBlockingQueue.))

(def store-op (atom {}))

(def conf (atom {}))

;;##############################################
;; YAML Tools
;;##############################################

(defn yaml->map
  [st]
  (yaml/parse-string st))

(defn map->yaml
  [mp]
  (yaml/generate-string mp :dumper-options {:flow-style :block :indent 2}))

;;##############################################
;;save a string somewhere
;;##############################################
(defmulti save-to (fn [method params key data] method))

(defmethod save-to :file [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (fi/default-bucket))
        path key]
      (fi/put buck path data)))

(defmethod save-to :s3 [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (s3/default-bucket))
        path key]
      (s3/put buck path data)))

(defmethod save-to :oss [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (oss/default-bucket))
        path key]
      (oss/put buck path data)))

(defmethod save-to :gcs [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (gcs/default-bucket))
        path key]
      (gcs/put buck path data)))

(defmethod save-to :consul [_ params key data]
    (let [buck (if-not (nil? (:bucket params))
                  (:bucket params)
                  (consul/default-bucket))
          path key]
        (consul/put buck path data)))

(defn save
 [store data]
 (doseq [store-type (keys (dissoc store :key))]
    (save-to store-type (store-type store) (:key store) data)))


;;##############################################
;; Save a map somewhere
;;##############################################
(defmulti save-map-to (fn [method params key data] method))

(defmethod save-map-to :file [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (fi/default-bucket))
        path (str key ".yml")]
      (fi/put buck path (map->yaml data))))

(defmethod save-map-to :s3 [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (s3/default-bucket))
        path (str key ".yml")]
      (s3/put buck path (map->yaml data))))

(defmethod save-map-to :oss [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (oss/default-bucket))
        path (str key ".yml")]
      (oss/put buck path (map->yaml data))))

(defmethod save-map-to :gcs [_ params key data]
  (let [buck (if-not (nil? (:bucket params))
                (:bucket params)
                (gcs/default-bucket))
        path (str key ".yml")]
      (gcs/put buck path (map->yaml data))))

(defmethod save-map-to :consul [_ params key data]
    (let [buck (if-not (nil? (:bucket params))
                  (:bucket params)
                  (consul/default-bucket))
          path key]
        (consul/put buck path data)))

(defn save-map
 [store data]
 (doseq [store-type (keys (dissoc store :key))]
    (save-map-to store-type (store-type store) (:key store) data)))


;;##############################################
;; Load a string from somewhere
;;##############################################
(defmulti load-from (fn [method store] method))

(defmethod load-from :file [_ store]
  (let [fil (:file store)
        buck (if-not (nil? (:bucket fil))
                (:bucket fil)
                (fi/default-bucket))
        path (:key store)]
      (fi/get buck path true)))

(defmethod load-from :s3 [_ store]
  (let [s3 (:s3 store)
        buck (if-not (nil? (:bucket s3))
                (:bucket s3)
                (s3/default-bucket))
        path (:key store)]
      (s3/get buck path true)))

(defmethod load-from :oss [_ store]
  (let [oss (:oss store)
        buck (if-not (nil? (:bucket oss))
                (:bucket oss)
                (oss/default-bucket))
        path (:key store)]
      (oss/get buck path true)))

(defmethod load-from :gcs [_ store]
  (let [gcs (:gcs store)
        buck (if-not (nil? (:bucket gcs))
                (:bucket gcs)
                (gcs/default-bucket))
        path (:key store)]
      (gcs/get buck path true)))

 (defmethod load-from :consul [_ store]
   (let [consul (:consul store)
        buck (if-not (nil? (:bucket consul))
                (:bucket consul)
                (consul/default-bucket))
        path (:key store)]
      (consul/get buck path true)))

(defn load
 [store]
 (loop [stores (:order @conf)]
   (if-not (nil? (get store (first stores) nil))
     (load-from (first stores) store)
     (recur (rest stores)))))

;;##############################################
;; Load a map from somewhere
;;##############################################

(defmulti load-map-from (fn [method store] method))

(defmethod load-map-from :file [_ store]
  (let [fil (:file store)
        buck (if-not (nil? (:bucket fil))
                (:bucket fil)
                (fi/default-bucket))
        path (str (:key store) ".yml")]
      (yaml->map (fi/get buck path (:fail-fast fil)))))

(defmethod load-map-from :s3 [_ store]
  (let [s3 (:s3 store)
        buck (if-not (nil? (:bucket s3))
                (:bucket s3)
                (s3/default-bucket))
        path (str (:key store) ".yml")]
      (yaml->map (s3/get buck path (:fail-fast s3)))))

(defmethod load-map-from :oss [_ store]
  (let [oss (:oss store)
        buck (if-not (nil? (:bucket oss))
                (:bucket oss)
                (oss/default-bucket))
        path (str (:key store) ".yml")]
      (yaml->map (oss/get buck path (:fail-fast oss)))))

(defmethod load-map-from :gcs [_ store]
  (let [gcs (:gcs store)
        buck (if-not (nil? (:bucket gcs))
                (:bucket gcs)
                (gcs/default-bucket))
        path (str (:key store) ".yml")]
      (yaml->map (gcs/get buck path (:fail-fast gcs)))))

(defmethod load-map-from :consul [_ store]
    (let [consul (:consul store)
         buck (if-not (nil? (:bucket consul))
                 (:bucket consul)
                 (consul/default-bucket))
         path (:key store)
         res (consul/get buck path (:fail-fast consul))]
       (if (= res "")
            nil
            res)))

(defn load-map
 [store]
 (loop [stores (:order @conf)]
   (if-not (nil? (get store (first stores) nil))
     (load-map-from (first stores) store)
     (if-not (= (count stores) 0)
       (recur (rest stores))
       {}))))

(defn configure!
  [store-conf]
  (reset! conf store-conf)
  (when (:s3 store-conf)
    (s3/configure! (:s3 store-conf)))
  (when (:oss store-conf)
    (oss/configure! (:oss store-conf)))
  (when (:gcs store-conf)
    (gcs/configure! (:gcs store-conf)))
  (when (:file store-conf)
    (fi/configure! (:file store-conf)))
  (when (:consul store-conf)
     (consul/configure! (:consul store-conf))))
