(ns com.linkfluence.store.atomic.hbase)

;;hbase conf
(def conf (atom nil))

;;map to store already-created index
(def created-table (atom {}))

(defn default-bucket
  "For es storer a bucket is a namespace"
  []
  (:default-bucket @conf))
