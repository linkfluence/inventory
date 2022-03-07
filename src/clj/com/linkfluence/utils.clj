(ns com.linkfluence.utils
  (:import [org.apache.commons.net.util SubnetUtils]
           [java.util.concurrent TimeoutException TimeUnit])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clj-http.client :as http]
            [clj-yaml.core :as yaml]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

(def conf (atom {}))

(def fsync-manager (atom {}))

(defn mk-resp
  "easy ring response formatter"
  ([status state body]
    {:status status :body (assoc body :state state)})
  ([status state body msg]
    {:status status :body (assoc body :state state :msg (str msg))}))

(defn send-fsync
  [mirror handler-name]
  (try
    (http/get (str "http://" mirror "/" handler-name "/fsync") {:socket-timeout 40000 :conn-timeout 20000})
    true
      (catch Exception e
        (log/error "[FSYNC][FAILURE]" handler-name "to" mirror "" e)
        false)))

(defn fsync
  [handler-name]
  (doseq [mirror (:fsync @conf)]
      (future
        (if-not (get-in @fsync-manager [mirror handler-name] false)
          (do
            (swap! fsync-manager assoc-in [mirror handler-name] true)
            (loop []
              (when-not (send-fsync mirror handler-name)
                  (Thread/sleep 60000)
                  (recur)))
            (swap! fsync-manager assoc-in [mirror handler-name] false))
          (log/warn "[FSYNC] An fsync operation is already pending for" mirror "-" handler-name " ignoring")))))

(defn start-thread!
  "This function launch a thread"
  [user-fn name]
  (log/info "Thread " name " started")
  (let [run (atom true)
        t (Thread.
           (fn []
             (while @run
               (try
                 (user-fn)
                (catch java.lang.InterruptedException _
                  (log/info "receive an interrupt exception in thread loop, exit " name)
                  (reset! run false))
                (catch Throwable t
                  (log/fatal t "FATAL ERROR EXIT THREAD LOOP : " name)
                  (reset! run false))))
             (log/info "End of operation thread" name)))]
    (.setDaemon t true)
    (.start t)
    {:stop
     (fn []
       (reset! run false)
       (.join t 1000)
       (when (.isAlive t)
         (log/warn "thread loop still alive (blocking take), interrupt." name)
         (.interrupt t)
         (.join t 1000))
       (log/info "thread loop stopped." name))
     :status (fn [] (and @run (.isAlive t)))}))

(defn yaml->map
  [st]
  (yaml/parse-string st))

(defn map->yaml
  [mp]
  (yaml/generate-string mp :dumper-options {:flow-style :block :indent 2}))

(defn random-string
  [length]
  (let [random (java.util.Random.)
        chars  (map char (concat (range 48 58) (range 66 91) (range 97 123)))
        rand-char (fn [] (nth chars (.nextInt random (count chars))))]
        (apply str (take length (repeatedly rand-char)))))

(defn timeout [timeout-ms callback]
   (let [fut (future (callback))
         ret (deref fut timeout-ms ::timed-out)]
     (when (= ret ::timed-out)
       (future-cancel fut))
     ret))

(defn tags-binder
 [tags-binding tags]
 (concat
   ;;binded tag
   (map (fn [[k v]]
       (when-let [t-value (k tags)]
         {:name v :value t-value}))
       tags-binding)
   ;;copied tag
   (map (fn [[k v]]
           (when (nil? (k tags-binding))
             {:name (name k) :value v}))
           tags)))

 (defn tags-value-morpher
  [tags-value-morphing tags]
    ;;morphed tag
    (into {} (map (fn [[k v]]
        (if-let [morpher (k tags-value-morphing)]
          [k (morpher v)]
          [k v]))
        tags)))

(defn save?
    [last-save items-not-saved]
    (and
        (> (- (System/currentTimeMillis) @last-save) 5000)
        (> @items-not-saved (or (:max-not-saved-items @conf) 50))))

(defn reset-save!
    [last-save items-not-saved]
    (reset! last-save (System/currentTimeMillis))
    (reset! items-not-saved 0))

(defn configure!
  [utils-conf]
  (reset! conf utils-conf))
