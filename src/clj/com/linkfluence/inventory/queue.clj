(ns com.linkfluence.inventory.queue
  (:import [java.util.concurrent LinkedBlockingQueue])


(defprotocol IQproto
  "Inventory queue proto"
  (put [q e] "push element to queue")
  (take [q]"get element to queue")
  (size [q]))

(deftype IQLinkedBlockingQueue [^LinkedBlockingQueue q]
  "LinkedBlockingQueue implementation"
  IQProto
  (put [q e] (.put q e))
  (take [q] (.take q))
  (size [q] (.size q)))

(defn iq-linked-blocking-queue
  []
  (->IQLinkedBlockingQueue (LinkedBlockingQueue.)))

(deftype IQKafkaQueue [kafka-spec]
  "kafka implementation"
  IQProto
  (put [q e])
  (take [q])
  (size [q]))

(defn iq-kafka-queue
  [{:keys [bootstrap-servers topic group-id]}]
  (->IQKafkaQueue {:consumer nil
                   :producer nil
                   :admin nil
                   :topic nil
                   :group-id nil
                   :bootstrap-servers nil}))
