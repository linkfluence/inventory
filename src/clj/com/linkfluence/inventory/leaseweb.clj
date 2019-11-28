(ns com.linkfluence.inventory.leaseweb
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.linkfluence.inventory.core :as inventory]
            [com.linkfluence.inventory.caller :as caller] ;; to set ssh keys
            [clojure.java.shell :as shell]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [com.linkfluence.inventory.net :as net]
            [leaseweb.v2.core :as lsw]
            [leaseweb.v2.server :as server]
            [leaseweb.v2.pnet :as pnet]
            [chime :refer [chime-at]]
            [clj-time.core :as t]
            [clj-time.periodic :refer [periodic-seq]]
            [clojure.spec.alpha :as spec]
            [clj-yaml.core :as yaml])
  (:import [java.io File]
           [java.util.concurrent LinkedBlockingQueue]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2018

;;this inventory is based on lsw server name
(def test (atom false))

(def lsw-inventory (atom {}))

(def lsw-pscheme (atom {}))

(def lsw-conf (atom nil))

(def lsw-api-client (atom nil))

(defn ro?
  []
  (:read-only @lsw-conf))

(def current-address (atom ""))

;;contain list of server to be setup
(def monitored-install (atom {}))

(defn disable-server-install!
  [state]
  (swap! lsw-conf assoc :disable-install state)
  (log/info "[LSW] installation disabled : " (:disable-install @lsw-conf))
  state)

(defn get-install-loop-state
  []
  {:monitoredInstall @monitored-install
   :installCount (count @monitored-install)})

(def ^LinkedBlockingQueue lsw-queue (LinkedBlockingQueue.))

(defn get-lsw-event-queue-size
  []
  (.size lsw-queue))

(defn load-inventory!
  []
  ;;default use s3
  (when-not @test
  (when-let [inventory (store/load-map (assoc (:store @lsw-conf) :key (str (:key (:store @lsw-conf)) "-inventory")))]
    (reset! lsw-inventory inventory))))

(defn load-pscheme!
  []
  ;;default use s3
  (when-not @test
  (when-let [pscheme (store/load-map (assoc (:store @lsw-conf) :key (str (:key (:store @lsw-conf)) "-pscheme")))]
    (reset! lsw-pscheme pscheme))))

(defn save-pscheme
  "Save on both local file and s3"
  []
  (when-not (:read-only @lsw-conf)
  (when (= 0 (.size lsw-queue))
    (store/save-map (assoc (:store @lsw-conf) :key (str (:key (:store @lsw-conf)) "-pscheme")) @lsw-pscheme)
    (u/fsync "lsw"))))

(defn save-inventory
  "Save on both local file and s3"
  []
  (when (and (= 0 (.size lsw-queue)) (not (:read-only @lsw-conf)) (not @test))
    (store/save-map (assoc (:store @lsw-conf) :key (str (:key (:store @lsw-conf)) "-inventory")) @lsw-inventory)
    (u/fsync "lsw")))

(defn- cached?
  "check if corresponding server-name exist in cache inventory
   args:
     server-id : a string of the server identifier"
  [server-id]
    (let [sidkey (if (keyword? server-id) server-id (keyword (str server-id)))]
    (if-let [server (get @lsw-inventory sidkey nil)]
        true
        false)))

(defn get-server-with-ref
    "Return server"
    [ref]
    (when (some? ref)
    (when-let [server (reduce (fn [acc [k v]]
                                (when (= ref (get-in v [:contract :reference]))
                                    (reduced v))) nil @lsw-inventory)]
            server)))

(defn get-server-inventory-id-with-ref
    "Return server"
    [ref]
    (when-let [server-id (reduce (fn [acc [k v]]
                                (when (= ref (get-in v [:contract :reference]))
                                    (reduced k))) nil @lsw-inventory)]
            server-id))

(defn- fix-missing-reference
    [server id]
    (let [server* (server/describe @lsw-api-client (:id server))]
        (swap!
            lsw-inventory
            assoc-in
            [id :contract :reference]
            (get-in server* [:contract :reference])))
        (save-inventory))

(defn get-server
  "Return server"
  [id]
  (when-let [server (get @lsw-inventory id nil)]
    (when (nil? (get-in server [:contract :reference]))
        (fix-missing-reference server id))
        server))

(defn get-server-jobs
    "Return server jobs"
    [id]
    (if-not (ro?)
    (when-let [server (get @lsw-inventory id nil)]
        (server/list-jobs @lsw-api-client (:id server)))
        {:error "this inventory is ro for leaseweb"}))

(defn describe-server-job
    "Return a server job full description"
    [id job-id]
    (if-not (ro?)
    (when-let [server (get @lsw-inventory id nil)]
        (server/describe-job @lsw-api-client (:id server) job-id))
        {:error "this inventory is ro for leaseweb"}))


(defn delete-server!
  "delete"
  [server-id]
  (when-not (:read-only @lsw-conf)
    (let [server-to-delete (get @lsw-inventory (keyword server-id))]
        (swap! lsw-inventory dissoc (keyword server-id))
        ;;send delete update to main inventory
        (inventory/add-inventory-event {:type "resource"
                                  :id (get-in server-to-delete [:contract :reference])
                                  :tags []
                                  :delete true})
        (save-inventory))))

(defn- reboot-server
  "Reboot"
  [server-id]
  (let [sidkey (if (keyword? server-id) server-id (keyword (str server-id)))]
  (when-not (:read-only @lsw-conf)
    (when (nil? (sidkey @monitored-install))
        (when-let [server (get @lsw-inventory (keyword server-id))]
        (log/info "[LSW] rebooting server :" (name server-id) "/" (:id server))
        (server/reboot @lsw-api-client (:id server)))))))

(defn add-operation
  [server-name operation params]
  (when-not (ro?)
    (.put lsw-queue [server-name operation params])))




(defn get-current-addr
  []
  @current-address)

(defn set-private-ip
  "set private ip of server"
  [server-id ip]
  (let [sidkey (keyword (if (keyword? server-id) server-id (str server-id)))]
  (swap! lsw-inventory assoc-in [sidkey :privateIp] ip)
  (inventory/add-inventory-event {:type "resource"
                    :id (get-in @lsw-inventory [sidkey :contract :reference] (str "lsw-" (name server-id)))
                    :tags [{:name "privateIp" :value ip}]})
  (save-inventory)))

  (defn is-address-already-used?
    [address]
    (let [lsw-inventory-state (reduce (fn [acc [k v]] (if (= (:privateIp v) address)
                                  (reduced true)
                                  false)) false @lsw-inventory)
          main-inventory-state (inventory/get-resources [{:name "provider" :value "LSW"}
                                                         {:name "privateIp" :value "privateIp"}])]
          (log/info "IP Duplicate check: - lsw side:" lsw-inventory-state "- inventory-side:" main-inventory-state)
          ;; first check that ip is not set in lsw inventory
          (if lsw-inventory-state
              true
              ;; in not check that it is not in main inventory
              (if (= 0 (count main-inventory-state))
                ;; check that we can't ping this address
                  (let [pingres (shell/sh "ping" "-c" "2" address)]
                    (log/info "Ip duplicate check : ping output" (:out pingres))
                    (if (= 1 (:exit pingres))
                        false
                        true))
                  (let [[ref res] (first main-inventory-state)
                        server-id (get-server-inventory-id-with-ref (name ref))]
                      (set-private-ip server-id (:privateIp res))
                      true)))))

(defn get-available-address
  [server-id]
  (let [sidkey (keyword (if (keyword? server-id) server-id (str server-id)))]
  (if-let [private-ip (get-in @lsw-inventory [sidkey :privateIp])]
    private-ip
    (let [n (:net @lsw-conf)
        final-addr (loop [addr (net/get-next-address (:network n) (:netmask n) @current-address)]
                      (if (is-address-already-used? addr)
                        (recur (net/get-next-address (:network n) (:netmask n) addr))
                        addr))]
      (swap! lsw-inventory assoc-in [(keyword server-id) :privateIp] final-addr)
      (save-inventory)
      (reset! current-address final-addr)))))

(defn- configure-debian-if
  [ifname server-id n]
  [(str "echo \"auto " ifname "\" >> /etc/network/interfaces")
   (str "echo \"iface " ifname " inet static\" >> /etc/network/interfaces")
   (str "echo 'address " (get-available-address server-id) "' >> /etc/network/interfaces")
   (str "echo 'netmask " (:netmask n) "' >> /etc/network/interfaces")
   (str "echo 'broadcast " (net/get-broadcast-addr (:network n) (:netmask n)) "' >> /etc/network/interfaces")
   (str "ifconfig " ifname " down")
   (str "ifup " ifname)
   (str "ifconfig " ifname " up")
   "sleep 20"])

(defn- configure-netplan-if
  [ifname server-id n]
  (let [nfile (str "/etc/netplan/02-" ifname ".yaml")
        netplan-conf {:network {
                       :version 2
                       :ethernets {
                         (keyword ifname) {
                           :dhcp4 "no"
                           :addresses [
                           (str
                             (get-available-address server-id)
                             (net/get-cidr (:network n) (:netmask n)))
                           ]}}}}]
  [(str "echo \"" (yaml/generate-string netplan-conf)"\" > " nfile)
   (str "netplan apply")
   "sleep 20"]))

(defn- configure-red-hat-if
  [ifname server-id n]
  [(str "echo \"DEVICE=" ifname "\" >> /etc/sysconfig/network-scripts/ifcfg-" ifname)
   (str "echo 'BOOTPROTO=none' >> /etc/sysconfig/network-scripts/ifcfg-" ifname)
   (str "echo 'ONBOOT=yes' >> /etc/sysconfig/network-scripts/ifcfg-" ifname)
   (str "echo 'IPADDR=" (get-available-address server-id) "' >> /etc/sysconfig/network-scripts/ifcfg-" ifname)
   (str "echo 'NETMASK=" (:netmask n) "' >> /etc/sysconfig/network-scripts/ifcfg-" ifname)
   (str "echo 'BROADCAST=" (net/get-broadcast-addr (:network n) (:netmask n)) "' >> /etc/sysconfig/network-scripts/ifcfg-" ifname)
   (str "echo 'USERCTL=no' >> /etc/sysconfig/network-scripts/ifcfg-" ifname)
   (str "ifconfig " ifname " down")
   (str "ifup " ifname)
   (str "ifconfig " ifname " up")
   "sleep 20"])

(defn- configure-if
  [ifname server-id n]
  (condp  = (get @lsw-conf :base-distrib :debian)
      :debian (configure-debian-if ifname server-id n)
      :red-hat (configure-red-hat-if ifname server-id n)
      :netplan (configure-netplan-if ifname server-id n)
      []))

(defn- choose-if
    [ifs]
    (concat
        ["IF_NAME=\"\""]
        (vec
            (mapcat (fn [iface]
                [(str "ip link show " iface)
                 (str "if [ $? -eq 0 -a \"$IF_NAME\" == \"\" ]; then IF_NAME=" iface ";fi")])
                 ifs))))

(defn- mk-commands
  [server-id]
  (let [n (:net @lsw-conf)
        ifs (get-in @lsw-conf [:net :interface-name] "eth1")
        ifname (if (string? ifs) ifs "$IF_NAME")
        sidkey (keyword (if (keyword? server-id) server-id (str server-id)))
        server-ref (get-in @lsw-inventory [sidkey :contract :reference] (str "lsw-" (name sidkey)))]
  (concat
    (get @lsw-conf :post-install-optional-cmd [])
    ["[ ! -d /root/.ssh ] && mkdir /root/.ssh"
     (str "echo \"" (slurp (:public-keys @lsw-conf)) "\" >> /root/.ssh/authorized_keys")
     "passwd --lock root"
     (str "echo "
        server-ref
         " > /etc/resource-id")
     (str "echo LSW > /etc/provider-id")]
     (if (get-in @lsw-conf [:net :configure-if] true)
        (if (string? ifs)
            (configure-if ifname server-id n)
            (concat
                (choose-if ifs)
                (configure-if ifname server-id n)))
        [])
    [(str "while ! ping -c 2 " (:inventory-host @lsw-conf) "; do echo 'Network still unreachable waiting...';sleep 5; done")
     (str "curl http://" (:inventory-host @lsw-conf) "/lsw/server/" (name sidkey) "/install/finish")
     (str "curl http://" (:inventory-host @lsw-conf) "/provision/" server-ref)
     (str "echo postinstall-ended > /root/postinstall-flag")])))

(defn clean-install-loop!
  "Reset install loop in case of issue"
  ([]
      (reset! monitored-install {}))
  ([id]
      (let [sidkey (if (keyword? id) id (keyword (str id)))]
        (swap! monitored-install dissoc sidkey))))


(defn- mark-as-installed!
  "update installed state"
  [server-id]
  (let [sidkey (keyword (if (keyword? server-id) server-id (str server-id)))]
  (when-not (:read-only @lsw-conf)
  ;;mark as installed
  (inventory/add-inventory-event {:type "resource"
                                  :id (get-in @lsw-inventory [sidkey :contract :reference])
                                  :tags [{:name "state" :value "installed"}]})
  (swap! lsw-inventory assoc-in [sidkey :setup] true)
  (swap! monitored-install dissoc sidkey)
  (save-inventory))))

(defn- lsw-region
  [az]
  (let [mt (re-matches #"([a-z-]*)([0-9]+)" (str/lower-case az))
        region (get mt 1)]
  (if-not (nil? region)
    (if (= "-" (str (last region)))
      (str/join (butlast region))
      region)
    az)))

(defn- update-inventory
  [server-id reinstall]
  (when-not (:read-only @lsw-conf)
  (let [skey (keyword (if (keyword? server-id) server-id (str server-id)))
        server (get @lsw-inventory skey)
        az (get-in server [:location :site])
        suite (get-in server [:location :suite])
        tags (if reinstall
                  [{:name "state" :value "install_pending"}
                   {:name "privateIp" :value "" :delete true}
                   {:name "FQDN" :value "" :delete true}]
                   [{:name "state" :value "install_pending"}
                    {:name "provider" :value "LSW"}
                    {:name "publicIp" :value (get-in @lsw-inventory [skey :publicIp])}
                    {:name "REGION" :value (lsw-region az)}
                    {:name "SHORT_AZ" :value az}
                    {:name "AZ" :value (str az "-" suite)}])]
  (inventory/add-inventory-event {:type "resource"
                                :id (get-in server [:contract :reference] (str "lsw-" (name server-id)))
                                :reinstall reinstall
                                :default-host (name server-id)
                                :tags tags}))))

(defn- install-server!
  ([server-id]
      (install-server! server-id (:default-partition-scheme @lsw-conf) false))
  ([server-id reinstall]
      (install-server! server-id (:default-partition-scheme @lsw-conf) reinstall))
  ([server-id pschema-name reinstall]
    (when-not (:read-only @lsw-conf)
    (let [sidkey (if (keyword? server-id) server-id (keyword (str server-id)))]
    (when (nil? (sidkey @monitored-install))
    (let [pschema ((keyword pschema-name) @lsw-pscheme)
          server (get @lsw-inventory sidkey nil)
          raid-sugg (try (server/suggested-raid-configuration server)
                        (catch Exception e
                            (log/error "Server raid configuration not set do nothing")
                            nil))]
      (if-not (or (:disable-install @lsw-conf) (nil? raid-sugg))
        (do
          (log/info "[LSW] install" (name server-id) "/reference:" (get-in server [:contract :reference]) "with os :" (:default-os @lsw-conf) " - pschema :" pschema " - raid " (:raid-level raid-sugg))
          (if-not (= "error" (server/install @lsw-api-client
                                             (name server-id)
                                             (:default-os @lsw-conf)
                                             pschema
                                             (:raid-level raid-sugg)
                                             (:number-disks raid-sugg)
                                             (if (some? (:raid-level raid-sugg)) "SW" "NONE")
                                             nil
                                             (server/mk-post-install-script (mk-commands (name server-id)))))
            (do
              (server/set-reference @lsw-api-client (name server-id) (str "lsw-" (name server-id)))
              (log/info "[LSW] reference : server is now referenced with reference :" (str "lsw-" (name server-id)))
              ;;mark server has not setup
              (swap! lsw-inventory assoc-in [sidkey :contract :reference] (str "lsw-" (name server-id)))
              (swap! lsw-inventory assoc-in [sidkey :setup] false)
              (swap! lsw-inventory assoc-in [sidkey :os] (:default-os @lsw-conf))
              ;;update install
              (swap! monitored-install assoc sidkey true)
              ;;save to inventory
              (update-inventory server-id reinstall)
              (save-inventory))
            (do
              (save-inventory)
              (log/error "[LSW] installation call fail"))))
        (do
          (log/info "[LSW] install : install disabled" )
          (if-not (nil? raid-sugg)
            (do
              (server/set-reference @lsw-api-client server-id (str "lsw-" (name server-id)))
              (log/info "[LSW] reference : server is now referenced with reference :" (str "lsw-" (name server-id)))
              ;;mark server has not setup
              (swap! lsw-inventory assoc-in [sidkey :setup] true)
              (swap! lsw-inventory assoc-in [sidkey :contract :reference] (str "lsw-" (name server-id)))
              (swap! lsw-inventory assoc-in [sidkey :os] (:default-os @lsw-conf))
              (update-inventory server-id reinstall)
              (mark-as-installed! server-id)
              (save-inventory))
            (log/error "Server info not fullfiled skip" server))))))))))

(defn- bootstrap
  "server bootstrap"
  [server-id]
  (when-not (:read-only @lsw-conf)
  (let [server-id (name server-id)
        desc (server/describe @lsw-api-client server-id)
        contract (:contract desc)
        sidkey (keyword (str (:id desc)))
        ip (net/get-ip (get-in desc [:networkInterfaces :public :ip] nil))]
        (log/info "[LSW] bootstraping" server-id "- reference :" (:reference contract))
        (if-not (or (nil? (:specs desc)) (nil? ip) (nil? (:location desc)))
          (when-not (cached? server-id)
            (if (and
                    (not (nil? (:reference contract)))
                    (.startsWith (:reference contract) "lsw-"))
              ;;Server has a reference : Don't install server mark it as deployed
              (do
                (log/info "[LSW] Server already setup detected")
                (swap! lsw-inventory assoc sidkey (assoc desc :api-version "v2"
                                                            :setup true
                                                            :init false
                                                            :publicIp ip
                                                            :os (:default-os @lsw-conf)))
                (update-inventory server-id false)
                (inventory/add-inventory-event {:type "resource"
                                  :id (:reference contract)
                                  :tags [{:name "state" :value "deployed"}]})
                (save-inventory))
              ;;Server has no reference : proceed to server setup
              (do
                (log/info "[LSW] save server info")
                (swap! lsw-inventory assoc sidkey (assoc desc :api-version "v2"
                                                            :setup false
                                                            :init true
                                                            :publicIp ip))
                (log/info "[LSW] Adding server" server-id "to private network" (:default-private-net @lsw-conf))
                (pnet/add-server @lsw-api-client (:default-private-net @lsw-conf) server-id)
                (log/info "[LSW] Launch install on server" server-id "- reference :" (str "lsw-" (name server-id)))
                (install-server! server-id))))
          (save-inventory)))))

(defn- refresh-server-ids
    [server]
    (let [contract-id (get-in server [:contract :id])
          reference (get-in server [:contract :reference])
          server-id (:id server)
          skey (if (keyword? server-id) server-id (keyword (str server-id)))]
        (when (and
                (some? reference)
                (not (= reference "")))
        (when-let [server-stored (get-server-with-ref reference)]
            (let [server-ii (get-server-inventory-id-with-ref reference)
                  server-updated-id (assoc server-stored :id (name server-id))
                  server-updated (assoc-in server-updated-id [:contract :id] contract-id)]
                 (log/info "[LSW] updating lsw server with current id" (name server-ii) "with new id" (name server-id))
                ;;add updated map with new ids
                (swap!
                    lsw-inventory
                    assoc
                    skey
                    server-updated)
                ;;remove deprecated server map
                (swap!
                    lsw-inventory
                    dissoc
                    server-ii))
                (save-inventory)))))

(defn- server-operation!
  "Send bootstrap, reverse or delete operation
  NB: because of api v2, identifier can be server-id or reference"
  [[identifier action params]]
  (when-not (:read-only @lsw-conf)
  (condp = action
    "delete" (delete-server! identifier)
    "bootstrap" (bootstrap identifier)
    "reboot" (reboot-server identifier)
    "reinstall" (install-server! identifier true)
    "custom-reinstall" (install-server! identifier params true)
    "end-setup" (mark-as-installed! identifier)
    "set-private-ip" (set-private-ip identifier params)
    (log/info "unknow action for lsw handler"))))

;;loop to poll lsw api
(defn- start-lsw-loop
  "get server list and check inventory consistency
  add server to queue if it is absent, remove deleted server"
  []
  (when-not (or (ro?) (nil? (:refresh-period @lsw-conf)))
  (let [refresh-period (periodic-seq (t/now) (t/minutes (:refresh-period @lsw-conf)))]
  (log/info "[LSW][Refresh] starting LSW refresh loop")
  (chime-at refresh-period
    (fn [_]
      (when-let [slist (:servers (server/list-all @lsw-api-client))]
          (let [smap (into {} (map (fn [x] {(keyword (str (:id x))) true}) slist))]
            ;;detection of new server
            (doseq [base-server slist]
              (let [contract-id (str (get-in base-server [:contract :id]))
                    reference (get-in base-server [:contract :reference])
                    server-id (:id base-server)]
              ;;update server id/contract-id if a server has the same reference -> only when server doesn't exist
              (when-not (cached? server-id)
                (refresh-server-ids base-server))
              ;;check if server already exist
              (when-not (cached? server-id)
                (log/info "Adding server" contract-id "/" server-id "to lsw queue")
                (.put lsw-queue [server-id "bootstrap" nil]))))
            ;;detection of deleted servers
              (doseq [[k v] @lsw-inventory]
                (when-not (k smap)
                  (log/info "Deleting server" (name k) "from lsw inventory")
                  (.put lsw-queue [(name k) "delete" nil]))))))))))


;;loop to setup
(defn- start-op-consumer!
  "consum install queue"
  []
  (when-not (:read-only @lsw-conf)
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [ev (.take lsw-queue)]
          ;; extract queue and pids from :radarly and dissoc :radarly data
          (server-operation! ev)))
      "lsw operation consumer")))


(defn get-install-state
  "Return server"
  [server-id]
  (let [sidkey (if (keyword? server-id) server-id (keyword (str server-id)))]
  (if-let [install-state (get @monitored-install sidkey nil)]
    install-state
    nil)))

;;return an lsw view of our infrastructure
(defn get-lsw-inventory
  "Return lsw inventory"
  []
  (into [] (map (fn [[k v]] k) @lsw-inventory)))


  ;;return an lsw view of our infrastructure
  (defn get-lsw-inventory-with-ref
    "Return lsw inventory reference"
    []
    (into [] (map (fn [[k v]] (get-in v [:contract :reference])) @lsw-inventory)))

;;return an lsw view of our infrastructure
(defn get-lsw-pscheme
  "Return lsw partition scheme list"
  []
  (into [] (map (fn [[k v]] (name k)) @lsw-pscheme)))

(defn create-partition-schema
  "Return lsw partition scheme"
    [name pscheme]
    (when-not (:read-only @lsw-conf)
    (swap! lsw-pscheme assoc (keyword name) pscheme)
    (save-pscheme)))

(defn delete-partition-schema
  "Return lsw partition scheme"
    [id]
    (when-not (:read-only @lsw-conf)
    (swap! lsw-pscheme dissoc (keyword id))
    (save-pscheme)))

(defn get-partition-schema
  "Return lsw partition scheme"
    [id]
    (get @lsw-pscheme (keyword id) nil))

(defn end-setup
  "Send event Update inventory when setup is ended"
  [server-name]
  (when-not (ro?)
  (.put lsw-queue [server-name "end-setup" nil])))

(defn start!
  []
  (if-not (or (nil? @lsw-conf) (nil? (:refresh-period @lsw-conf)) (:read-only @lsw-conf))
    [{:stop (start-lsw-loop)} (start-op-consumer!)]
    (do
      (if-not (:read-only @lsw-conf)
        (log/info "[LSW] missing parameters can't load refresh loop")
        (log/info "[LSW] mode set to READ_ONLY, API actions are limited"))
      [])))

(defn configure!
  [conf]
  (reset! lsw-conf conf)
  (log/info "[LSW]" (:store @lsw-conf))
  (when-not (nil? @lsw-conf)
    (log/info "[LSW] installation disabled : " (get @lsw-conf :disable-install true))
    (load-inventory!)
    (load-pscheme!)
    (reset! current-address (:offset (:net conf)))
    (reset! lsw-api-client (lsw/mk-client (:token @lsw-conf)))))
