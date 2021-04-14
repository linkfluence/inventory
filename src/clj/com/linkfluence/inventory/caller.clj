(ns com.linkfluence.inventory.caller
  (:import [java.util.concurrent LinkedBlockingQueue]
           [java.io File])
  (:require [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.tools.logging :as log]
            [com.linkfluence.utils :as utils]
            [com.linkfluence.es :as es]
            [cheshire.core :as json])
  (:use [clj-ssh.ssh]
        [clojure.java.io]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

; Command prototype :
; {
;   :commands "ls" or ["service server-init start" "service server-generic start"], semicolon are added
;   :src (reserved for sftp)
;   :user (optional use default user configured)
;   :dest (reserved for sftp)
;   :module (reserved for ansible)
;   :playbook (reserved for ansible-playbook)
;   :module-path (reserved for terraform)
;   :var-file (reserved for terraform)
;   :sudo true
;   :password (optional, use caller private key)
;   :key-path (optional, override default key path)
;   :hosts "you-host.example.com" or ["host1.example.com" "host2.example.com"]
;   :method ":ssh" ":ansible" ":ansible-playbook" ":sftp" ":terraform" or ":local"
; }

; /!\ WARNING /!\
;the caller aim not to reachable through API because it is really dangerous, ie it can send command to all servers.
;Be carefull when you use it in lib, triple check your command building

;;command counter for log
(def caller-counter (atom 0))

;;caller conf
(def caller-conf (atom nil))

;;for es writer (store a spandex client)
(def es-journal (atom nil))

(defn ro?
  []
  (:read-only @caller-conf))

;;queue for command
(def command-queue (LinkedBlockingQueue.))

;;queue for command journal
(def journal-queue (LinkedBlockingQueue.))

(def cust-date-format (f/formatter "YYYY-MM-dd"))

(def cust-datetime-format (f/formatter "YYYY-MM-dd HH:mm:ss"))

;;indicate whether to build index or not
(def es-index-exist? (atom false))

(def es-client (atom nil))

;;
(def es-mappings
  {
        :properties {
          :commands {
            :type "text",
            :fields { :keyword { :type "keyword" :ignore_above 256 }}}
          :state { :type "keyword"}
          :method { :type "keyword"}
          :host { :type "keyword"}
          :exit { :type "integer"}
          :id { :type "keyword" }
          :out {
            :type "text",
            :fields { :keyword { :type "keyword" :ignore_above 256 }}}
          :err {
            :type "text",
            :fields { :keyword { :type "keyword" :ignore_above 256 }}}
          :date {
            :type "date"
            :format "yyyy-MM-dd' 'HH:mm:ss"
          }}})

(defn write-journal->es
    "write jouranl to es"
    [command state]
    (try
     (when (nil? @es-client)
       (reset! es-client (es/connect (:journal-es @caller-conf))))
     (when-not @es-index-exist?
       (es/create-index
          (:conn @es-client)
          "caller-journal"
          {}
          es-mappings)
          (reset! es-index-exist? true))
      (es/index-doc
        (:conn @es-client)
        "caller-journal"
        { :date (f/unparse cust-datetime-format (t/now))
          :method (:method command)
          :state state
          :id (:id command)
          :commands (str (:commands command) (:playbook command) (:module command) (:src command))
          :hosts (str (:hosts command))
          :out (str (when (:out command) (:out command)))
          :err (str (when (:err command) (:err command)))
          :exit (str (when (:exit command) (:exit command)))})
          (catch clojure.lang.ExceptionInfo e
            (log/error "Error saving caller journal" (ex-data e)))
          (catch Exception e
            (log/error "Error saving caller journal" e))))

(defn write-journal->file
    "write journal to file"
    [command state]
    (let [date (t/now)
        date-st (f/unparse cust-date-format date)
        datetime-st (f/unparse cust-datetime-format date)]
    (with-open [wrt (writer (str (:journal-path @caller-conf) "/journal-" date-st ".log" ) :append true)]
      (.write wrt (json/generate-string { :date (f/unparse cust-datetime-format (t/now))
        :method (:method command)
        :state state
        :commands (str (:commands command) (:playbook command) (:module command) (:src command))
        :hosts (str (:hosts command))
        :out (str (when (:out command) (:out command)))
        :err (str (when (:err command) (:err command)))
        :exit (str (when (:exit command) (:exit command)))}))
      (.newLine wrt))))

(defn write-journal
  "write command in journal"
  [command state]
  (when-not (ro?)
    ;;file writer
    (when (:journal-path @caller-conf)
        (write-journal->file command state))
    (when (:journal-es @caller-conf)
        (write-journal->es command state))))

(defn add-command
  "Add command to queue"
  [command]
    (when-not (ro?)
      (let [command-counter (swap! caller-counter inc)
            prefix (utils/random-string 8)
            id (str prefix "-" command-counter)]
      (.put command-queue (assoc command :id id))
      (log/info "[CALLER] add command - queue size :" (.size command-queue))
      id)))

(defn add-to-journal
  "add command to journal"
  [command state]
  (when-not (ro?)
  (.put journal-queue [command state])))

(defn host-vec->list
  "convert host vec to an ansible hostlist"
  [host-vec]
  (if (vector? host-vec)
    (if (= 1 (count host-vec))
      (str (first host-vec) ",")
      (str (str/join "," host-vec) ","))
    (str host-vec ",")))

(defn command-vec->string
  "convert a command vec to a semicolon separated command string"
  [sudo command-vec]
  (if-not sudo
    (if (vector? command-vec)
        (str/join ";" command-vec)
        command-vec)
    (if (vector? command-vec)
        (str "sudo " (str/join ";sudo " command-vec))
        (str "sudo "command-vec))))

(defn command-user
  [command]
  (if-let [u (:user command)]
    u
    (:default-user @caller-conf)))

(defn set-ssh-public-key
  "setup public key and disable password,
  if commands specified execute additionnal commands"
  ([user password hosts]
    (set-ssh-public-key user password hosts []))
  ([user password hosts commands]
  (add-command {:commands (vec (concat
                                  ["mkdir /root/.ssh"
                                   (str "echo \"" (slurp (:default-public-key @caller-conf)) "\" >> /root/.ssh/authorized_keys")
                                   "passwd --lock root"]
                                  commands))
                :user user
                :hosts hosts
                :password password
                :method :ssh})))

(defn reset-ssh-key
  [server-list]
  (doseq [server-name server-list]
   (shell/sh "/usr/bin/ssh-keygen" "-R" server-name "-f" "/root/.ssh/known_hosts")))

(defn mk-ssh-session
  "mk clj-ssh session either with password or with the default key"
  [command port]
  (if (nil? (:password command))
    (let [agent (ssh-agent {:use-system-ssh-agent false})]
        (add-identity agent {:private-key-path (:default-key @caller-conf)})
            (session agent (:hosts command) {:strict-host-key-checking :no :port port :username (command-user command)}))
    (let [agent (ssh-agent {:use-system-ssh-agent false})]
        (session agent (:hosts command) {:strict-host-key-checking :no :port port :username (command-user command) :password (:password command)}))))

(defn send-ssh-command
  "send ssh command"
  [command port]
  (if (:commands command)
  (let [session (mk-ssh-session command port)]
      (with-connection session
          (let [result (ssh session {:in (command-vec->string (:sudo command) (:commands command))})]
            (add-to-journal (assoc command :out (:out result) :err (:err result) :exit (:exit result)) "SENT"))))
            (add-to-journal  (assoc command :out "NONE" :err "No commands !" :exit 1) "FAILED")))

(defn send-sftp-command
  "send ssh command"
  [command port]
          (let [session (mk-ssh-session command port)]
            (with-connection session
              (let [channel (ssh-sftp session)]
                 (with-channel-connection channel
                   (sftp channel {} :put (:src command) (:dest command))))))
         (add-to-journal command "SENT"))

;;send command to a (list of) server(s)
(defmulti execute-command (fn [command]
                            (:method command)))

(defmethod execute-command :ssh [command]
  "Send ssh command"
  (try
    ;;send command through default ssh port
    (send-ssh-command command 22)
    (catch com.jcraft.jsch.JSchException e
      (try
        (log/error "Port 22 failed" e)
        ;;fall back to custom port
        (send-ssh-command command (:ssh-custom-port @caller-conf))
        (catch com.jcraft.jsch.JSchException ee
          (add-to-journal (assoc command :out (.getMessage ee)) "FAILED")
          (log/error "Port " (:ssh-custom-port @caller-conf) " failed" ee))
        (catch Exception eee
          (add-to-journal (assoc command :out (.getMessage eee)) "FAILED")
          (log/error "Port " (:ssh-custom-port @caller-conf) " failed" eee))
        (catch Throwable th
          (add-to-journal (assoc command :out (.getMessage th)) "FAILED")
          (log/error "Port " (:ssh-custom-port @caller-conf) " failed" th))))))

(defmethod execute-command :sftp [command]
  "Send sftp comand"
  (try
    ;;send command through default ssh port
    (send-sftp-command command 22)
    (catch com.jcraft.jsch.JSchException e
      (try
        (log/error "Port 22 failed" e)
        ;;fall back to custom port
        (send-sftp-command command (:ssh-custom-port @caller-conf))
        (catch com.jcraft.jsch.JSchException ee
          (add-to-journal (assoc command  :out (.getMessage ee)) "FAILED")
          (log/error "Port " (:ssh-custom-port @caller-conf) " failed" ee))
        (catch Exception eee
          (add-to-journal (assoc command :out (.getMessage eee)) "FAILED")
          (log/error "Port " (:ssh-custom-port @caller-conf) " failed" eee))
        (catch Throwable th
          (add-to-journal (assoc command :out (.getMessage th)) "FAILED")
          (log/error "Port " (:ssh-custom-port @caller-conf) " failed" th))))))

(defmethod execute-command :ansible [command]
  "Send ansible command"
  (let [out (shell/sh
          (str (or (:ansible-path @caller-conf) "/usr/local/bin") "/ansible")
           "-i" (host-vec->list (:hosts command))
           "-m" (:module command)
           "-a" (:commands command))]
    (when (:debug @caller-conf)
      (log/info "ansible return :" (:out out)))
    (add-to-journal (assoc command :out (:out out) :err (:err out) :exit (:exit out)) "SENT")))

(defmethod execute-command :ansible-playbook [command]
  "Send ansible-playbook command"
  (let [out (shell/sh
                (str (or (:ansible-path @caller-conf) "/usr/local/bin") "/ansible-playbook")
                "-i"
                (host-vec->list (:hosts command))
                (:playbook command))]
    (when (:debug @caller-conf)
      (log/info "ansible-playbook return :" (:out out)))
    (add-to-journal (assoc command :out (:out out) :err (:err out) :exit (:exit out)) "SENT")))

(defmethod execute-command :terraform [command]
  (let [tf-cmd (first (:commands command))
        out (shell/sh
          (str (or (:terraform-bin-path @caller-conf) "/usr/local/bin") "/terraform")
           tf-cmd)]
    (when (:debug @caller-conf)
      (log/info "terraform return :" (:out out)))
    (add-to-journal (assoc command :out (:out out) :err (:err out) :exit (:exit out)) "SENT")))

(defmethod execute-command :local [command]
  "Send local command"
    (if (vector? (:commands command))
      (doseq [co (:commands command)]
        (shell/sh co))
        (shell/sh (:commands command)))
  (add-to-journal (assoc command :out "LOCAL") "SENT"))

(defn- start-command-consumer!
  "This function consume command queue"
  [index]
  (utils/start-thread!
      (fn [] ;;consume queue
        (when-let [command (.take command-queue)]
          (try
          (execute-command command)
            (catch Exception e
              (log/error "Command execution failed" e)))))
      (str "Caller command consumer - " index)))

(defn- start-journal-writer!
  "this function write command event into journal"
  []
    (utils/start-thread!
      (fn [] ;;consume queue
        (when-let [[command state] (.take journal-queue)]
          (write-journal command state)))
      "Caller journal consumer"))

(defn start!
  []
  (if-not (or (nil? @caller-conf) (ro?))
    (into []
      (concat
        (map (fn [x] (start-command-consumer! x)) (range 1 (:nb-thread @caller-conf)))
        [(start-journal-writer!)]))
    []))

(defn configure!
  "initialize caller:
    :journal-path commands journal (can be store on s3 and on local file system)
    :default-key
    :default-user"
  [conf]
  (reset! caller-conf conf))
