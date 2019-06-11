(ns com.linkfluence.store.atomic.pg)

;;pg conf
(def conf (atom nil))
;;map to store already-created index
(def created-table (atom {}))

(defn default-bucket
  "For pg storer a bucket is a db"
  []
  (:default-bucket @conf))
