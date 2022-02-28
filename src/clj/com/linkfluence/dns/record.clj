(ns com.linkfluence.dns.record
    (:import [java.io File])
    (:require [chime :refer [chime-at]]
              [clj-time.core :as t]
              [clj-time.periodic :refer [periodic-seq]]
              [clojure.string :as str]
              [clojure.tools.logging :as log]
              [com.linkfluence.store :as store]
              [com.linkfluence.utils :as utils]
              [clojure.java.shell :as shell]
              [digest]
              [clostache.parser :as template]
              [clojure.spec.alpha :as spec]
              [com.linkfluence.dns.zone :refer [zodb->db]]
              [com.linkfluence.inventory.queue :as queue :refer [put tke]])
    (:use [com.linkfluence.dns.common]
          [clojure.java.io]
          [clojure.walk]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

; Bind basic record mgt
; Record structure :
; {
;  :name "bidule"
;  :type "A"
;  :ttl "300"
;  :value "10.2.0.3" (possibly an array)
; }
;
;;for testing
(def test (atom false))

;In memory view of db files
;Structure db_file -> record_name
(def records-map (atom {}))

(def serials-map (atom {}))

;;###########################################
;;USefull predicate
;;###########################################
;;We select only recort-type that interest us
(def record-types #{"A" "AAAA" "PTR" "CNAME" "MX" "NS" "TXT" "SPF" "SSHFP"})

(def ip-pattern #"^([01]?\d\d?|2[0-4]\d|25[0-5])(\.(?:[01]?\d\d?|2[0-4]\d|25[0-5])){3}$")

(defn ip?
  [str]
  (let [matches (re-matches ip-pattern str)]
      (if matches
        true
        (do
          (log/error "Bad ip addr" str)
          false))))

(defn parsable-int?
  [string]
  (try
    (Integer/parseInt string)
    true
    (catch Exception e
      false)))

;;##################################
;;# Spec section
;;##################################

;;Specs for record
(spec/def ::IP (spec/and string? ip?))
(spec/def ::IPs (spec/or :single-ip ::IP
                         :multiple-ips (spec/coll-of ::IP)))

(spec/def ::type record-types)
(spec/def ::ttl (spec/or :ttl-as-int int?
                         :ttl-as-string parsable-int?))
(spec/def ::name string?)
(spec/def ::value (spec/or :single-value string?
                           :multiple-values (spec/coll-of string?)))
(spec/def ::record
  (spec/keys :req-un [::type ::ttl ::name ::value]))

(spec/def ::del-record
  (spec/keys :req-un [::type ::name]))

(def last-save (atom (System/currentTimeMillis)))
(def items-not-saved (atom 0))
(def op-queue (atom nil))


(defn is-multiline?
  "indicate if is a multi-line record"
  [line]
  (let [record-pattern  #"\("
        matches (re-matches record-pattern line)]
    (if matches
      true
      false)))

(defn is-serial?
  "return the serial of zone"
  [line]
  (let [record-pattern  #"[\s\t]*([0-9]*)[\s\t]*;[\s\t]*Serial"
        matches (re-matches record-pattern line)]
    (if matches
      (Integer/parseInt (get matches 1))
      nil)))

(defn is-record?
  "Check if the line is a record"
  [line]
  (let [record-pattern  #"([^\s\t]*)[\s\t]*([0-9]*)[\s\t]*IN[\s\t]*([^\s\t]*)[\s\t]*([^\s\t]*.*)"
        matches (re-matches record-pattern line)]
    (if matches
      {:name (get matches 1)
       :ttl (get matches 2)
       :type (get matches 3)
       :value (get matches 4)}
      nil)))

(defn update-serial-from-db-file
  "update serial line"
  [db-name]
  (let [filepath (str @zone-path "/" db-name)]
     (with-open [rdr (reader filepath)]
        (with-open [wrt (writer (str filepath ".tmp") :append true)]
          (loop [line (.readLine rdr)]
            (when line
              (if-let [line-rec (is-serial? line)]
                (let [serial-line (str "         " (get @serials-map db-name 0) "   ; Serial")]
                    (.write wrt serial-line)
                    (.newLine wrt))
                (do (.write wrt line)
                    (.newLine wrt)))
              (recur (.readLine rdr))))))
  (.renameTo (file (str filepath ".tmp")) (file filepath))))

(defn update-serial!
  "basically inc serial"
  [db-name]
  (if-let [serial (get @serials-map db-name nil)]
    (swap! serials-map assoc db-name (inc serial)))
  (update-serial-from-db-file db-name))

(defn update-cache!
  "update the atom cache
  * dbname
  * action : add/update/delete
  * record"
  [db-name action record]
  (let [record-key (str (:name record) "_" (:type record))
        vec-path [db-name record-key]
        crecord (get-in @records-map vec-path nil)]
      (condp = action
      ;add a records (do not replace if already exist)
      "add"  (if crecord
          (if (string? (:value crecord))
            (if-not (= (:value crecord) (:value record))
              (let [ovalue (:value record)
                    nrecord (assoc record :value [(:value crecord) ovalue])]
                    (swap! records-map assoc-in vec-path nrecord))
              record))
            (swap! records-map assoc-in vec-path record))
      ; update a record (replace if already exist)
      "update" (swap! records-map assoc-in vec-path record)
      ; del records (only one value record)
      "delete" (when (string? (:value record))
                  (if (string? (:value crecord))
                    (swap! records-map assoc db-name (dissoc (get @records-map db-name) record-key))
                    (let [u-value (vec (remove (into #{} [(:value record)]) (:value crecord)))
                          remaining (count u-value)]
                      (if (> remaining 0)
                        (swap! records-map assoc-in (conj vec-path :value) u-value)
                        (swap! records-map assoc db-name (dissoc (get @records-map db-name) record-key)))))))))



(defn append-record-from-db-file
  "add a record to a db file"
  [db-name record]
  (try
  (with-open [wrtr (writer (str @zone-path "/" db-name) :append true)]
    (.write wrtr (str (:name record) " " (:ttl record) " IN " (:type record) " " (:value record)))
    (.newLine wrtr))
    (catch Exception e
      (log/error "Error while writing record"))))

(defn delete-record-from-db-file
  "delete a record to a db file"
  [db-name record]
  (let [filepath (str @zone-path "/" db-name)]
     (with-open [rdr (reader filepath)]
        (with-open [wrt (writer (str filepath ".tmp") :append true)]
          (loop [line (.readLine rdr)]
            (when line
              (if-let [line-rec (is-record? line)]
                (when-not (and (= (:name line-rec) (:name record))
                               (= (:type line-rec) (:type record))
                               (or (= (:value record) (:value line-rec)) (nil? (:value record))))
                    (do (.write wrt line)
                        (.newLine wrt)))
                (do (.write wrt line)
                    (.newLine wrt)))
              (recur (.readLine rdr))))))
  (.renameTo (file (str filepath ".tmp")) (file filepath))))

(defn update-db-file
  "update the db-file
  * dbname
  * action : add/update/delete
  * record"
  [db-name action record]
  (when (db-exists? db-name)
    (condp = action
      "add" (append-record-from-db-file db-name record)
      "update" (do
                (delete-record-from-db-file db-name (dissoc record :value))
                (append-record-from-db-file db-name record))
      "delete" (delete-record-from-db-file db-name record))))

(defn list-record
  "List interesting records
  if db already exist in cache then send cache
  else read file and save it to cache"
  [zodb-name]
  (when-not @test
  (let [db-name (zodb->db zodb-name)
        records (if-let [lrecords (get @records-map db-name nil)]
    lrecords
    (do
      (with-open [rdr (clojure.java.io/reader (str @zone-path "/" db-name))]
        (doseq [line (line-seq rdr)]
          (cond
            (is-record? line) (let [record (is-record? line)]
                                (when-let [type (record-types (:type record))]
                                  (update-cache! db-name "add" record)))
            (is-serial? line) (let [serial (is-serial? line)]
                                (swap! serials-map assoc db-name serial)))))
       (get @records-map db-name nil)))]
       (into [] (map (fn [[k v]] v) records)))))

(defn exists?
  "indicate if a record exist"
  [db-name record]
  ;load cache
  (list-record db-name)
  (let [ac-switch #(condp = %
                        "A" "CNAME"
                        "CNAME" "A"
                        "")
        reg-record (get-in @records-map [db-name (str (:name record) "_" (:type record))] nil)
        reg-conflict-record (get-in @records-map [db-name (str (:name record) "_" (ac-switch (:type record)))] nil)]
        {:exists (not (nil? reg-record))
         :equals (if (not (nil? reg-record))
                  (= (:value record) (:value reg-record))
                  false)
         :conflict (and
                     (or (= "A" (:type record))
                         (= "CNAME" (:type record)))
                     (some? reg-conflict-record))}))

(defn execute-operation!
  "A record is a map containint the following keys
  - name
  - type
  - ttlvalue
  - value"
  [op]
  (let [[zodb-name operation record] op]
  (if (db-exists? zodb-name)
        ;load records if is not already the case
        (do
        (when-not (contains? @records-map zodb-name)
          (list-record zodb-name))
        (let [rec-state (exists? zodb-name record)]
          (if (not
                    (and
                        (= operation "add")
                        (or (:equals rec-state)
                            (:conflict rec-state))))
            (do
              (update-cache! zodb-name operation record)
              (update-db-file zodb-name operation record)
              (update-serial! zodb-name)
              (save-zone zodb-name)
              (swap! items-not-saved inc))
              (log/info "[DNS] record" record "has this state" rec-state)))
        ;;Wait for eventual batched events
        (Thread/sleep 2000)
        (when (utils/save? last-save items-not-saved)
          (restart-dns op-queue true)
          (utils/reset-save! last-save items-not-saved)
          (utils/fsync "dns")))
          (log/error "DB does not exist : do nothing" zodb-name))))

(defn sync-zone-records
  [db-name]
  (try
  (let [file-path (str @zone-path "/" db-name)
        key (str (get-in @dns-conf [:store :key]) "/" db-name)
        local-md5 (digest/md5 (try (slurp file-path) (catch Exception e "")))
        saved-content (store/load (assoc (:store @dns-conf) :key key))
        saved-md5 (digest/md5 saved-content)]
        (if (= saved-md5 local-md5)
          {:db-name db-name :change false}
          (do
            (spit file-path saved-content)
            (swap! records-map dissoc db-name)
            (list-record db-name)
            {:db-name db-name :change true})))
            (catch Exception e
              {:db-name db-name :change false})))


(defn refresh-dns-records!
  []
  (when-not (nil? @dns-conf)
    (let [changes (map sync-zone-records (:sync-zone @dns-conf))
        must-restart (reduce (fn [acc x] (or acc (:change x))) false changes)]
        (when must-restart
          (restart-dns)))))

(defn check-record
  "Check Record structure"
  [rec]
    (if (spec/valid? ::record rec)
      (if (= (:type rec) "A")
        ;;value is a vector
        (spec/valid? ::IPs (:value rec))
      true)
      false))

(defn add-operation
  "queue a single operation for a record request"
  [zodb-name op record]
  (log/info "[DNS] received dns record update:" op "on zone/db" zodb-name "with record specs" record)
  (cond
    ;;sync
    (= "sync" op)
    (put @op-queue [(zodb->db zodb-name) op record])
    (= "delete" op)
    (if (and
          (map? record)
          (spec/valid? ::del-record record))
        (put @op-queue [(zodb->db zodb-name) op record])
        (log/error "Checks failed for operation on record: map :"(map? record) "- check :" (spec/conform ::del-record record)))
    ;;record
    (some? (#{"add" "update"} op))
    (if (and
          (map? record)
          (check-record record))
      (put @op-queue [(zodb->db zodb-name) op record])
      (log/error "Checks failed for operation on record: map :"(map? record) "- check :" (spec/conform ::record record)))))

(defn add-operations
  "bulk add operation"
  [db-name op records]
  (doseq [record records]
    (add-operation db-name op record)))

(defn start-operation-consumer!
  "This function launch a thread which consumes the queue filled by scanner"
  []
  (utils/start-thread!
      (fn [] ;;consume queue
        (when-let [op (tke @op-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (execute-operation! op)))
      "DNS record updater consumer"))

(defn start!
  []
  (if-not (or (nil? @dns-conf) (ro?))
    ;start dequeue thread
    [(start-operation-consumer!)
     (chime-at (periodic-seq (t/now) (t/seconds 5))
                    (fn []
                        (restart-dns)
                        (utils/fsync "dns")))]
    []))
