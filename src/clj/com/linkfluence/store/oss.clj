(ns com.linkfluence.store.oss
  (:require [oss.core :as oss]
            [clojure.tools.logging :as log]))

(def conf (atom nil))

(defn get
  ([path]
    (com.linkfluence.store.oss/get (:default-bucket @conf) path true))
  ([path fail-fast]
    (com.linkfluence.store.oss/get (:default-bucket @conf) path fail-fast))
  ([bucket path fail-fast]
    (when (not (nil? @conf))
      (let [client (oss/mk-oss-client (:endpoint @conf) (:access-key @conf) (:secret-key @conf))
            content (try
                      (slurp (:content (oss/get-object client bucket path)))
                        (catch Exception e
                          (log/error e)
                          (if fail-fast
                            (throw (Exception. (str "Can't load resource" path)))
                            "")))]
            (oss/shut-client client)
            content))))

(defn put
  ([path content]
    (put (:default-bucket @conf) path content))
  ([bucket path content]
    (when (not (nil? @conf))
      (let [client (oss/mk-oss-client (:endpoint @conf) (:access-key @conf) (:secret-key @conf))]
        (try
         (oss/put-string client bucket path content)
         (catch Exception e
          (log/info "Can't save state to oss")))
        (oss/shut-client client)))))

(defn del
 ([path]
   (del (:default-bucket @conf) path))
 ([bucket path]
   (when (not (nil? @conf))
     (let [client (oss/mk-oss-client (:endpoint @conf) (:access-key @conf) (:secret-key @conf))]
       (try
        (oss/delete-object client bucket path)
        (catch Exception e
         (log/info "Can't delete to oss")))
       (oss/shut-client client)))))

(defn default-bucket
  "For oss store, a bucket is a bucket"
  []
  (:default-bucket @conf))

(defn configure!
  [oss-conf]
  (reset! conf oss-conf))
