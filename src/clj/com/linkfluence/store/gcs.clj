(ns com.linkfluence.store.gcs
  (:require [clj-gcloud.storage.object :as object]
            [clj-gcloud.storage.core :as storage]
            [clojure.tools.logging :as log]))

(def conf (atom nil))
(def client (atom nil))

(defn get
  ([path]
    (com.linkfluence.store.gcs/get (:default-bucket @conf) path true))
  ([path fail-fast]
    (com.linkfluence.store.gcs/get (:default-bucket @conf) path fail-fast))
  ([bucket path fail-fast]
    (when (not (nil? @conf))
      (let [content (try
                      (object/get-string @client bucket path)
                        (catch Exception e
                          (log/error e)
                          (if fail-fast
                            (throw (Exception. (str "Can't load resource" path)))
                            "")))]
            content))))

(defn put
  ([path content]
    (put (:default-bucket @conf) path content))
  ([bucket path content]
    (when (not (nil? @conf))
        (try
         (object/put-string @client bucket path content)
         (catch Exception e
          (log/info "Can't save state to gcs"))))))

(defn del
 ([path]
   (del (:default-bucket @conf) path))
 ([bucket path]
   (when (not (nil? @conf))
       (try
        (object/delete @client bucket path)
        (catch Exception e
         (log/info "Can't delete from gcs"))))))

(defn default-bucket
  "For gcs store, a bucket is a bucket"
  []
  (:default-bucket @conf))

(defn configure!
  [gcs-conf]
  (reset! conf gcs-conf)
  (reset! client (storage/init {:json-path (:creds-json-path @conf)})))
