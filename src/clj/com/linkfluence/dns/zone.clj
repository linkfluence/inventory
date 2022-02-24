(ns com.linkfluence.dns.zone
    (:import [java.io File]
             [java.util.concurrent LinkedBlockingQueue])
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
              [com.linkfluence.inventory.queue :as queue :refer [put tke]])
    (:use [com.linkfluence.dns.common]
          [clojure.java.io]
          [clojure.walk]))

;;Sample of a zone object
;;  {
;;   :type : master/forward/slave
;;   :notify: yes/no boolean
;;   :also-notify : semi-colon separated ip list
;;   :forwarders : semi-colon separated ip list
;;   :forward (optional) only | first
;;   :masters :semi-colon separated ip list
;;   :name: dot separated zone name
;; }

(def zone-cache (atom nil))

(def last-save (atom (System/currentTimeMillis)))
(def item-not-saved (atom 0))
(def op-queue (atom nil))
(defn init-queue
    [queue-spec]
    (reset! op-queue (queue/mk-queue (or queue-spec {}))))

(defn zone->db
  [zone-name]
  (get-in @zone-cache [(keyword zone-name) :db]))

(defn save-named-conf-local
  "save dns zones conf"
  []
  (save-zone "named.conf.local"))

(defn load-named-conf-local
  []
  (load-zone "named.conf.local"))

;;######################################
;; Useful things for specs
;;######################################

(def ip-list "((?:\\s*(?:(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])(?:\\.(?:[01]?\\d\\d?|2[0-4]\\d|25[0-5])){3});)+)")
(def yes-no-pattern #"(yes|no)")
(def zone-types #{"master" "slave" "forward"})
(def forward-types #{"only" "first"})

(defn yes-no?
  [str]
  (let [matches (re-matches yes-no-pattern str)]
    (if matches true
        (do (log/error "Bad allow-notify field - expected : yes or no" str)
          false))))

(defn ip-list?
  [str]
  (let [matches (re-matches (re-pattern ip-list) str)]
    (if matches true
        (do (log/error "Bad ip-list field - expected : [ip-addr;]" str)
          false))))
;;##################################
;;# Spec section
;;##################################

(spec/def ::name string?)
(spec/def ::notify (spec/and string? yes-no?))
(spec/def ::type zone-types)
(spec/def ::forwarders (spec/and string? ip-list?))
(spec/def ::forward forward-types)
(spec/def ::masters (spec/and string? ip-list?))
(spec/def ::also-notify (spec/and string? ip-list?))
(spec/def ::allow-update (spec/and string? (spec/or :none #(= "none" %)
                                                    :ip-list ip-list?)))

(defmulti zone-type :type)
(defmethod zone-type "master" [_]
  (spec/keys :req-un [::name ::type]
              :opt-un [::notify ::allow-update ::also-notify]))
(defmethod zone-type "slave" [_]
  (spec/keys :req-un [::name ::type ::masters]))
(defmethod zone-type "forward" [_]
  (spec/keys :req-un [::name ::type ::forwarders]
                    :opt-un [::forward]))

(spec/def ::zone (spec/multi-spec zone-type :type))

;;##################################
;;# Zone parsing
;;##################################
(def zone-regexp (re-pattern
  (str
    "zone\\s\\\"(.*)\\\"\\s*(?:IN\\s*){0,1}\\{"
    "(?:\\n+\\s*(type)\\s(master|forward|slave);)"
    "(?:\\n+\\s*(?:(file)\\s\\\"(.*)\\\";))?"
    "(?:\\n+\\s*(?:(allow-update)\\s\\{\\s*(\\w*;)?(?:" ip-list ")?\\s*\\};))?"
    "(?:\\n+\\s*(?:(also-notify)\\s+\\{" ip-list "\\s*\\};))?"
    "(?:\\n+\\s*(?:(notify))\\s+(yes|no);)?"
    "(?:\\n+\\s+(forward)\\s(\\w+);)?"
    "(?:\\n+\\s*(?:(forwarders)\\s+\\{" ip-list "\\s+\\};))?"
    "(?:\\n+\\s*(?:(masters)\\s+\\{" ip-list "\\s+\\};))?"
    "\\n*\\s*\\};")))

(defn list-zones
  "list zones parsed from named.conf.local"
  ([] (list-zones false))
  ([force]
  (if (or force (nil? @zone-cache))
    (let [zones-content (slurp (str @zone-path "/named.conf.local"))
          ma (re-matcher zone-regexp zones-content)
          zones (loop [z (remove nil? (rest (re-find ma)))
                       zm {}]
                  (if (empty? z)
                    zm
                    (let [zone-name (first z)
                          zone-map (apply hash-map (map str/trim (rest z)))
                          path-pattern (re-pattern (str @zone-path "/"))
                          db-name (when-let [zone-file (get zone-map "file")]
                                    (str/replace zone-file path-pattern ""))
                          zone-map (if-not (nil? db-name)
                                      (assoc zone-map :db db-name "name" zone-name)
                                      (assoc zone-map :name zone-name))]
                          (recur
                            (remove nil? (rest (re-find ma)))
                            (assoc zm zone-name zone-map)))))
              zones* (keywordize-keys zones)]
              (reset! zone-cache zones*)
              zones*)
        @zone-cache)))

(defn load-zones-file
    []
    (doseq [zone (vals (list-zones))]
        (when-not (db-exists? (:db zone))
            (if (= "master" (:type zone))
                (do
                    (load-zone (:db zone))
                    (try
                    (shell/sh "chown" (str @bind-user ":root") (str @zone-path "/" (:db zone)))
                        (catch Exception e
                            (log/warn "can't set right user to a zone file"))))
                (when (= "slave" (:type zone))
                    ;;create empty file
                    (spit
                        (str @zone-path "/" (:db zone))
                        "")
                    ;;set right user (inventory sould eventually run as root)
                    (try
                    (shell/sh "chown" (str @bind-user ":root") (str @zone-path "/" (:db zone)))
                        (catch Exception e
                            (log/warn "can't set right user to a zone file"))))))))

(defn generate-zones
  []
  ;;generate main zones registry file
  (try
  (spit
    (str @zone-path "/named.conf.local")
    (template/render-resource
      "templates/dns/named.options.local.mustache"
      {:zones (vals (list-zones))}))
      (catch Exception e
        (log/error "Fail to update named.conf.local")))
  ;;ensure that db file of zone are there
  (doseq [zone (vals (list-zones))]
    (log/info "Zone processed"(:name zone))
    (when-not (db-exists? (:db zone))
      (if (= "master" (:type zone))
      (try (spit
        (str @zone-path "/" (:db zone))
        (template/render-resource
          "templates/dns/zone.mustache"
          {:ttl 86400
           :refresh 300}))
           (catch Exception e
             (log/error "Fail to create zone file for zone" (:name zone))))
        (when (= "slave" (:type zone))
        (spit
          (str @zone-path "/" (:db zone))
          "")))))
    ;;save it
    (save-named-conf-local))

(defn zone-exists?
 "check zone existence from name.conf.local"
 [zone-name]
 (if-not (nil? (get (list-zones) (keyword zone-name)))
    true
    false))

(defn zodb->db
  "zone-name or db-name to db-name"
  [zodb-name]
  (if (zone-exists? zodb-name)
    (zone->db zodb-name)
    zodb-name))

(defn sync
    []
    (when-not (nil? @dns-conf)
        (load-named-conf-local)
        (load-zones-file)))

(defn execute-operation!
  "A zone is a map containing the following keys
  - type : master/forward/slave
  - notify: yes/no boolean
  - also-notify : semi-colon separated ip list
  - forwarders : semi-colon separated ip list
  - masters : semi-colon separated ip list
  - name: zone name"
  [op]
  (let [[zodb-name operation zone] op
        db-name (str "db." (:name zone))
        db-file (str @zone-path "/" db-name)]
        (condp = operation
          "create" (do
                          (list-zones)
                          (swap! zone-cache assoc
                            (keyword (:name zone))
                            (if (some? (#{"master" "slave"} (:type zone)))
                              (assoc zone :db db-name :file db-file)
                              zone))
                          (generate-zones)
                          (when (some? (#{"master" "slave"} (:type zone)))
                            (save-zone db-name)))
          "delete" (do
                          (list-zones)
                          (swap! zone-cache dissoc
                            (keyword (:name zone)))
                          (generate-zones))
           "sync" (sync)
            (log/error "op is not valid"))
            (restart-dns op-queue (utils/save? last-save item-not-saved))))

(defn check-zone
    [zone]
    (spec/valid? ::zone zone))

(defn add-operation
  "queue a single operation for a zone request"
  [zone-name op zoe]
  (let [zone (assoc zoe :name zone-name)]
    (cond
        ;;sync
        (= "sync" op)
        (put @op-queue [zone-name op zone])
        ;;zone
        (= "create" op)
        (if (spec/valid? ::zone zone)
            (put @op-queue [zone-name op zone])
            (log/error "Checks failed for operation on zone: , spec result :" (spec/conform ::zone zone)))
        (= "delete" op)
        (put @op-queue [zone-name op zone])
        :else
        nil)))

(defn start-operation-consumer!
  "This function launch a thread which consumes the queue filled by scanner"
  []
  (utils/start-thread!
      (fn [] ;;consume queue
        (when-let [op (tke @op-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (execute-operation! op)))
      "DNS Zone updater consumer"))

(defn start!
  []
  (if-not (or (nil? @dns-conf) (ro?))
    ;start dequeue thread
    (do (sync)
        (restart-dns op-queue)
        (if-not (ro?)
            [(start-operation-consumer!)
             (chime-at (periodic-seq (t/now) (t/seconds 5))
                           (fn []
                               (restart-dns)
                               (utils/fsync "dns")))]
            []))
    []))
