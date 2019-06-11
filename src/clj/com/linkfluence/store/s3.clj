(ns com.linkfluence.store.s3
  (:require [com.linkfluence.utils :as utils]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [amazonica.aws.s3 :as s3]
            [amazonica.aws.s3transfer :as s3t]))

(def conf (atom nil))

(defn get
 "retrieve text content from s3"
 ([path]
       (com.linkfluence.store.s3/get (:default-bucket @conf) path true))
 ([path fail-fast]
       (com.linkfluence.store.s3/get (:default-bucket @conf) path fail-fast))
 ([bucket path fail-fast]
   (when (not (nil? @conf))
     (let [creds (select-keys @conf [:access-key :secret-key :endpoint])]
       (try
         (slurp (:input-stream  (if-not (= {} creds)
                                      (s3/get-object creds
                                        :bucket-name bucket
                                        :key path)
                                    (s3/get-object
                                      :bucket-name bucket
                                      :key path))))
          (catch Exception e
            (log/error e bucket path)
            (if fail-fast
              (throw (Exception. (str "Can't load resource" bucket path)))
              "")))))))

(defn put
 "push text content to s3"
([path content]
       (put (:default-bucket @conf) path content))
([bucket path content]
  (when (not (nil? @conf))
    (let [creds (select-keys @conf [:access-key :secret-key :endpoint])
          content-bytes (.getBytes content)
          input-stream (io/input-stream content-bytes)]
      (try
        (if-not (= {} creds)
          (s3/put-object creds
                       :bucket-name bucket
                       :key path
                       :input-stream input-stream
                       :metadata {:content-length (count content-bytes)})
          (s3/put-object
                      :bucket-name bucket
                      :key path
                      :input-stream input-stream
                      :metadata {:content-length (count content-bytes)}))
       (catch Exception e
        (log/info "Can't save state to s3" e bucket path content)))))))

(defn del
 "del object content from s3"
 ([path]
       (del (:default-bucket @conf) path))
 ([bucket path]
  (when (not (nil? @conf))
    (let [creds (select-keys @conf [:access-key :secret-key :endpoint])]
      (try
        (if-not (= {} creds)
          (s3/delete-object creds
                         :bucket-name bucket
                         :key path)
          (s3/delete-object
                        :bucket-name bucket
                        :key path))
       (catch Exception e
        (log/info "Can't delete object from s3" e)))))))

(defn default-bucket
  "For s3 store, a bucket is a bucket"
  []
  (:default-bucket @conf))

(defn configure!
  [s3-conf]
  (reset! conf s3-conf))
