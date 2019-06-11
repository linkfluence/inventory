(ns com.linkfluence.store.atomic.consul)

;;es conf
(def conf (atom nil))

(defn default-bucket
  "For consul storer default-bucket is consul root path"
  []
  (:default-bucket @conf))
