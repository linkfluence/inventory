(ns com.linkfluence.inventory.gcp.common
  (:require [clojure.string :as str]
            [clj-time.core :as t]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clj-gcloud.compute.instance :as instance]
            [com.linkfluence.utils :as u]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2020 / Adot 2020 / Jean-baptiste Besselat 2020

;;gcs conf
(def conf (atom nil))
(def client-store (atom {}))

(defn set-conf
  [config]
  (doseq [project (:projects config)]
    (swap! client-store
        assoc
          (keyword (str (:id project) "-compute-instance"))
          (instance/init {:json-path (:creds-json-path project)
                         :application-name "inventory"})))
  (reset! conf config))

(defn client
  [project service]
  (get @client-store (keyword (str project "-" service))))

(defn get-service-store
  [service]
  (assoc (:store @conf) :key (str (:key (:store @conf)) "-" service)))

(defn get-conf
  []
  @conf)

(defn tags-binder
  [tags]
  (u/tags-binder (:tags-binding (get-conf)) tags))

(defn tags-value-morpher
  [tags]
  (u/tags-value-morpher (:tags-value-morphing (get-conf)) tags))

(defn az
  [zone]
  (last (str/split zone #"/")))

(defn short-az
  [zone]
  (str (last (az zone))))

(defn region
  [zone]
  (str/join "" (butlast (butlast (az zone)))))

(defn ro?
  []
  (:read-only @conf))
