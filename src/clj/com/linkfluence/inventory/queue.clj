(ns com.linkfluence.inventory.queue
  (:require [gregor.core :as kafka]
            [clojure.edn :as edn]
            [cheshire.core :refer :all])
  (:import [java.util.concurrent LinkedBlockingQueue]))

(def queue-default-conf (atom {:type :linked-blocking-queue}))

(defn configure!
    [queue-spec]
    (reset! queue-default-conf queue-spec))

(defprotocol IQProto
  "Inventory queue proto"
  (put [q e] "push element to queue")
  (tke [q] "get element to queue")
  (size [q])
  (close [q]))

(deftype IQLinkedBlockingQueue [^LinkedBlockingQueue q]
  IQProto
  (put [q e] (.put q e))
  (tke [q] (.take q))
  (size [q] (.size q))
  (close [q] nil))

(defn iq-linked-blocking-queue
  []
  (->IQLinkedBlockingQueue (LinkedBlockingQueue.)))

(deftype IQKafkaQueue [q]
  IQProto
  (put [q e] (kafka/send (:producer q) (:topic q) (generate-string e)))
  (tke [q] (if (= 0 (.size (:buffer q)))
              (do
                (let [records (kafka/poll (:consumer q))]
                        (doseq [el records]
                            (.put (:buffer q) (parse-string (:value el) true))
                        (kafka/commit-offsets! (:consumer q))))
                (.take (:buffer q)))
                (.take (:buffer q))))
  (size [q] -1)
  (close [q] (do
               (kafka/close (:consumer q)
               (kafka/close (:producer q))))))

(defn iq-kafka-queue
  [{:keys [bootstrap-servers topic group-id]}]
  (->IQKafkaQueue {:consumer (kafka/consumer bootstrap-servers group-id [topic]
                                {"auto.offset.reset" "earliest"
                                 "enable.auto.commit" "false"})
                   :producer (kafka/producer bootstrap-servers)
                   :admin (kafka/admin bootstrap-servers)
                   :buffer (LinkedBlockingQueue.)
                   :topic topic
                   :group-id group-id
                   :bootstrap-servers bootstrap-servers}))

(defmulti mk-queue
   (fn [x] (:type x)))

(defmethod mk-queue :kafka
    [params]
    (iq-kafka-queue params))

(defmethod mk-queue :linked-blocking-queue
    [_]
    (iq-linked-blocking-queue))

(defmethod mk-queue :default
    [_]
    (iq-linked-blocking-queue))

(defn init-queue
    [op-queue queue-spec]
    (reset! op-queue (mk-queue (or queue-spec {}))))
