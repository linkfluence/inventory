(ns com.linkfluence.store.atomic)

;;Namespace dedicated for atomic storage operation (not full inventory snapshot)
;;Aim to support db (pgsql, mysql)
;;Elasticsearch
;;Hbase
;;Consul
(defn atomic-store
  "Save an object atomically"
  [store id content])

(defn atomic-delete
  "Delete an object atomically"
  [store id])

(defn atomic-get
  "get an object atomically"
  [store id])

(defn load
  "Load all objects with a store spec"
  [store])
