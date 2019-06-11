(ns com.linkfluence.store.atomic.es)

;;es conf
(def conf (atom nil))

;;map to store already-created index
(def created-index (atom {}))

(defn default-bucket
  "For es storer a bucket is an index"
  []
  (:default-bucket @conf))

(defn index
  [])

(defn delete
  [])
