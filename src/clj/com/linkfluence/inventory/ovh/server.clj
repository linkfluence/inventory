(ns com.linkfluence.inventory.ovh.server
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ovh.core :as ovh]
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [ovh.server :as server]
            [ovh.vrack :as vrack]
            [ovh.ip :as ip]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]])
  (:import [java.io File]
           [java.util.concurrent LinkedBlockingQueue]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

;;this inventory is based on ovh server name
(def ovh-inventory (atom {}))

(def ovh-conf (atom nil))

(def last-save (atom (System/currentTimeMillis)))
(def item-not-save (atom 0))

(defn ro?
  []
  (:read-only @ovh-conf))

(def ^LinkedBlockingQueue ovh-queue (LinkedBlockingQueue.))

(def ovh-queue-state (atom {}))

(defn get-server
  "Return server"
  [id]
  (get @ovh-inventory id nil))

(defn in-queue?
    [server-name]
    (get @ovh-queue-state (keyword server-name) false))

(defn get-ovh-event-queue-size
  []
  (.size ovh-queue))

(defn load-inventory!
  []
  ;;default use s3
  (when-let [inventory (store/load-map (:store @ovh-conf))]
    (reset! ovh-inventory inventory)))

(defn install-disabled?
    "Indicate if ovh server installa tion is enabled or not"
    []
    (if (some? (:install-disabled @ovh-conf))
        (:install-disabled @ovh-conf)
        false))

(defn save-inventory
  "Save on both local file and s3"
  []
  (when (and (= 0 (.size ovh-queue)) (not (ro?)))
    (store/save-map (:store @ovh-conf) @ovh-inventory))
    (u/fsync "ovh"))

(defn- cached?
  "check if corresponding server-name exist in cache inventory"
  [server-name]
  (not (nil? (get @ovh-inventory (keyword server-name) nil))))

(defn tag-matcher
  "Check if an entity group/resource match a tag"
  [entity tag]
  (= (:value tag) ((keyword (:name tag)) entity)))

(defn- check-vrack
  "Check if corresponding server is into vrack add it if necessary"
  [server-name]
  (when-not (or (:debug @ovh-conf) (ro?))
    (let [vrack (first (server/vrack server-name))]
    (when (nil? vrack)
      (let [res (try
                    (vrack/add-server (:default-vrack @ovh-conf) server-name)
                    (catch Exception e
                        (log/error "Add vrack operation has failed, will retry in 2s")
                        nil))]
        (if (nil? res)
            (do (Thread/sleep 2000)
                (check-vrack server-name))
        (log/info "Add server " server-name " to vrack " (:default-vrack @ovh-conf) " - " (:body res))))))))

(defn- build-details
  []
  {:sshKeyName (:default-ssh-key @ovh-conf)
   :postInstallationScriptLink (:default-post-install-script @ovh-conf)})

(defn get-hard-partition-scheme
  [raid]
  (let [disks-nb (count (:names (first (:disks (first (:controllers raid))))))
        template-key (keyword (str disks-nb "disks"))]
      (if (not (nil? (template-key (:hard-partition-schemes @ovh-conf))))
        (template-key (:hard-partition-schemes @ovh-conf))
        (:default (:hard-partition-schemes @ovh-conf)))))

(defn get-soft-partition-scheme
  [specs]
  (let [disks-nb (:numberOfDisks (first (:diskGroups specs)))
        template-key (keyword (str disks-nb "disks"))]
        (if (not (nil? (template-key (:soft-partition-schemes @ovh-conf))))
          (template-key (:soft-partition-schemes @ovh-conf))
          (:default (:soft-partition-schemes @ovh-conf)))))

(defn- proceed-to-install
  "Send API call to proceed to setup"
  [server-name raid specs]
  (if-not (or (:debug @ovh-conf) (ro?) (install-disabled?))
    (if raid
      (let [res (server/install server-name (get-hard-partition-scheme raid) (:default-hard-template @ovh-conf) (build-details))]
        (log/info "Setup server" server-name " - " (:body res) " - " (:default-hard-template @ovh-conf)) - (get-hard-partition-scheme raid))
      (let [res (server/install server-name (get-soft-partition-scheme specs) (:default-template @ovh-conf) (build-details))]
        (log/info "Setup server" server-name " - " (:body res) " - " (:default-template @ovh-conf) " - " (get-soft-partition-scheme specs))))
    (log/info "Setup server" server-name)))

(defn- mark-as-installed!
  "update installed state"
  [server-name]
  ;;mark as installed
  (inventory/add-inventory-event {:type "resource"
                                  :id server-name
                                  :tags [{:name "state" :value "installed"}]})
  (if-let [desc (server/describe (name server-name))]
    (swap! ovh-inventory assoc-in [(keyword server-name) :os] (:os desc)))
  (swap! ovh-inventory assoc-in [(keyword server-name) :setup] true)
  (save-inventory))


(defn- ovh-region
  [az]
  (let [mt (re-matches #"([a-z-]*)([0-9]+)" (str/lower-case az))
        region (get mt 1)]
  (if-not (nil? region)
    (if (= "-" (str (last region)))
      (str/join (butlast region))
      region)
    az)))

(defn- ovh-short-az
  [az]
  (let [mt (re-matches #"([a-z-]*)([0-9]+)" (str/lower-case az))
        short-az (get mt 2)]
  (if-not (nil? short-az)
    short-az
    az)))

(defn- send-tags-request
  ([server-name desc state]
    (send-tags-request server-name desc state []))
  ([server-name desc state tags]
  (when-not (ro?)
  (inventory/add-inventory-event {:type "resource"
                                  :provider "OVH"
                                  :id server-name
                                  :tags (into []
                                    (concat [{:name "provider" :value "OVH"}
                                         {:name "REGION" :value (ovh-region (:datacenter desc))}
                                         {:name "AZ" :value (:datacenter desc)}
                                         {:name "SHORT_AZ" :value (ovh-short-az (:datacenter desc))}
                                         {:name "publicIp" :value (:ip desc)}
                                         {:name "state" :value state}]
                                         tags))}))))

(defn- delete-server!
  "Delete server from ovh inventory, this is cascaded to main inventory"
  [server-name]
  (when-not (ro?)
  ;;remove from ovh-inventory
  (swap! ovh-inventory dissoc (keyword server-name))
  ;;send delete update to main inventory
  (inventory/add-inventory-event {:type "resource"
                                  :provider "OVH"
                                  :id server-name
                                  :tags []
                                  :delete true}))
  ;;save inventory
  (save-inventory))

(defn- server-bootstrap!
  "bootstrap a server using our API"
  [server-name]
  ;get server description
  (let [desc (server/describe server-name)
        vrack (first (server/vrack server-name))
        raid (server/hardware-raid-profile server-name)
        specs (server/hardware-specifications server-name)
        service-infos (server/service-infos server-name)]
        ;add to vrack if needed
        ;launch install if needed
        (when-not (nil? desc)
          (if (= "none_64" (:os desc))
            ;;first server bootstrap
            (do
              (check-vrack server-name)
              (proceed-to-install server-name raid specs)
              (let [server-cache (merge (assoc desc :setup false
                                                    :creation (:creation service-infos)
                                                    :contactBilling (:contactBilling service-infos)) specs)]
                (send-tags-request server-name desc "install_pending")
                (swap! ovh-inventory assoc (keyword server-name) server-cache)))
            ;;server reinstall
            (if-not (cached? server-name)
              ;;trust ovh api :  server is setup
              (let [server-cache (merge (assoc desc :setup true
                                                    :creation (:creation service-infos)
                                                    :contactBilling (:contactBilling service-infos)) specs)]
                (send-tags-request server-name desc "deployed")
                (swap! ovh-inventory assoc (keyword server-name) server-cache))
              ;;trust local ovh inventory
              (let [server-cache (get @ovh-inventory server-name)]
                (when-not (:setup server-cache)
                  (proceed-to-install server-name raid specs)))))))

        (save-inventory)
        (swap! ovh-queue-state dissoc (keyword server-name)))

(defn- update-server-reverse!
  "Update the reverse DNS of an ovh server"
  [server-name reverse]
  (when-let [desc ((keyword server-name) @ovh-inventory)]
    (let [ip (:ip desc)
          res (ip/set-ip-reverse ip ip reverse)]
      (when (= 200 (:status res))
        (swap! ovh-inventory assoc-in [(keyword server-name) :reverse] reverse)))))

(defn- reboot-server
  "Reboot"
  [server-name]
  (when-not (ro?)
    (server/reboot (name server-name))
    (log/info "[OVH] rebooting server" server-name)))

(defn- reinstall-server!
  "launch server reinstallation"
  [server-name]
  (when-not (or (ro?) (= (in-queue? server-name) "bootstrap"))
  ;;remove setup state from inventory
  (swap! ovh-inventory assoc-in [(keyword server-name) :setup] false)
  ;;switch server state
  (send-tags-request
    (name server-name)
    (get-server (keyword server-name))
    "install_pending"
     [{:name "privateIp" :value "" :delete true}
      {:name "FQDN" :value "" :delete true}])
  ;;add server to queue to proceed reinstallation
  (.put ovh-queue [server-name "bootstrap" nil])
  (swap! ovh-queue-state assoc (keyword server-name) "bootstrap")))

(defn- server-operation!
  "Send bootstrap, reverse or delete operation"
  [[server-name action params]]
  (when-not (ro?)
  (condp = action
    "delete" (delete-server! server-name)
    "bootstrap" (server-bootstrap! server-name)
    "reverse" (update-server-reverse! server-name params)
    "reboot" (reboot-server server-name)
    "reinstall" (reinstall-server! server-name)
    "end-setup" (mark-as-installed! server-name)
    (log/info "unknow action for ovh dedicated server handler"))))

(defn end-setup
  "Send event Update inventory when setup is ended"
  [server-name]
  (when-not (ro?)
  (.put ovh-queue [server-name "end-setup" nil])))

(defn reinstall
  "Send event to reinstall server"
  [server-name]
  (when-not (ro?)
  (.put ovh-queue [server-name "reinstall" nil])))

(defn reboot
  "Send reboot event"
  [server-name]
  (when-not (ro?)
  (.put ovh-queue [server-name "reboot" nil])))

(defn update-reverse
  "Send update reverse event"
  [server-name reverse]
  (when-not (ro?)
  (.put ovh-queue [server-name "reverse" reverse])))

(defn custom-reinstall
  "custom install function"
  [server-name template partition-scheme]
  (when-not (:read-only @ovh-conf)
  ;;remove server for further update
  (swap! ovh-inventory dissoc (keyword server-name))
  (server/install server-name template partition-scheme (build-details))))

;;loop to poll ovh api
(defn- start-ovh-loop!
  "get server list and check inventory consistency
  add server to queue if it is absent, remove deleted server"
  []
  (when-not (or (:read-only @ovh-conf) (nil? (:refresh-period @ovh-conf)))
  (let [refresh-period (periodic-seq (t/now) (t/minutes (:refresh-period @ovh-conf)))]
  (log/info "[Refresh] starting OVH refresh loop")
  (chime-at refresh-period
    (fn [_]
      (when-let [slist (server/list-servers)]
          (let [smap (into {} (map (fn [x] {(keyword x) true}) slist))]
            ;;detection of deleted servers
              (doseq [[k v] @ovh-inventory]
                (when-not (k smap)
                  (log/info "Deleting server" (name k) "from ovh inventory")
                  (.put ovh-queue [(name k) "delete" nil]))))
            ;;detection of new server
            (doseq [server-name slist]
              (when-not (or (cached? server-name) (= (in-queue? server-name) "bootstrap"))
                (log/info "Adding server" server-name "to ovh queue")
                (.put ovh-queue [server-name "bootstrap" nil])
                (swap! ovh-queue-state assoc (keyword server-name) "bootstrap")))))))))

;;loop to setup
(defn- start-op-consumer!
  "consum install queue"
  []
  (when-not (:read-only @ovh-conf)
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [ev (.take ovh-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (server-operation! ev)))
      "ovh operation consumer")))


(defn get-server-id-with-ns
  [server-ns]
  (if-let [server (get @ovh-inventory (keyword server-ns) nil)]
    server-ns
    (let [server-ns* (if (.contains server-ns ".")
                          server-ns
                          (str server-ns "."))]
      (reduce (fn [acc x] (if (.startsWith (name x) server-ns*)
                            (reduced (name x))
                            nil)) nil (keys @ovh-inventory)))))

;;return an ovh view of our infrastructure
(defn get-ovh-inventory
  "Return ovh inventory"
  []
  (into [] (map (fn [[k v]] k) @ovh-inventory)))

(defn start!
  []
  (if-not (or (nil? @ovh-conf) (ro?))
    [{:stop (start-ovh-loop!)} (start-op-consumer!)]
    []))

;;init
(defn configure!
  "Main function"
  [conf]
  (reset! ovh-conf conf)
  (log/info "[OVH]" (:store @ovh-conf))
  (load-inventory!))
