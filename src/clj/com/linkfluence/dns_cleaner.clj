(ns com.linkfluence.dns-cleaner
    (:require [com.linkfluence.dns.common :as common]
              [com.linkfluence.dns.record :as record]
              [com.linkfluence.dns.zone :as zone]
              [com.linkfluence.inventory.core :as inventory]))

;;default to tag fqdn
(def conf (atom {:tag "privateIp"}))

(defn check-A-record
    [zone A-record]
    (let [res (inventory/get-resources [{:name (:tag @conf) :value (:value A-record)}])
          res-hidden (inventory/get-hidden-resources [{:name (:tag @conf) :value (:value A-record)}])]
        (if (= 0 (+ (count res) (count res-hidden)))
            (assoc A-record :to-clean true)
            (assoc A-record :to-clean false))))

(defn check-CNAME-record
    [A-records-map CNAME-record]
    (if (get A-records-map (:value CNAME-record) false)
        (assoc CNAME-record :to-clean true)
        (assoc CNAME-record :to-clean false)))

(defn find-records-to-clean
    [zone]
    (if (common/db-exists? (zone/zodb->db zone))
        (let [db (zone/zodb->db zone)
              recs (record/list-record db)
              A-records (filter #(= "A" (:type %)) recs)
              CNAME-records (filter #(= "CNAME" (:type %)) recs)
              A-records (map
                            (partial check-A-record zone)
                            A-records)
              A-records-map (reduce (fn [acc rec]
                                        (if (:to-clean rec)
                                            (assoc acc (:name rec) true)
                                            acc)) {} A-records)
              CNAME-records (map
                                (partial check-CNAME-record A-records-map)
                                CNAME-records)]
              {:CNAME CNAME-records
               :A A-records}
              )
              {:CNAME []
               :A []
               :error "Non Existent zone"}))

(defn clean-records
    [zone records-to-clean]
    (doseq [record records-to-clean]
        (when (:to-clean record)
            (record/add-operation zone "delete" (dissoc record :to-clean)))))

(defn clean-zone
    [zone]
    (let [records-marked (find-records-to-clean zone)]
    (clean-records zone (:CNAME records-marked))
    (clean-records zone (:A records-marked))))

(defn configure!
    [config]
    (reset! conf config))
