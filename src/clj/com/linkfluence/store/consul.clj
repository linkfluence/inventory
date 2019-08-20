(ns com.linkfluence.store.consul
  (:require [oss.core :as oss]
            [clojure.tools.logging :as log]
            [envoy.core :as envoy]
            [clojure.string :as s]
            [clojure.data :as d]))


(def conf (atom nil))

(defn clean-slash
    [path]
    (s/join "/"(remove #{""} (s/split path #"/"))))

(defn get-last-fragment
    [path]
    (let [fragments (remove #{""} (s/split path #"/"))]
    [(s/join "/"(butlast fragments)) (keyword (last fragments))]))

(defn get-first-fragment
    [path]
    (let [fragments (remove #{""} (s/split path #"/"))]
        [(keyword (first fragments)) (s/join "/"(rest fragments))]))

(defn put
  ([path content]
    (put (:default-bucket @conf) path content))
  ([bucket path content]
     (let [clean-path (str (clean-slash bucket) "/" (clean-slash path))
           build-url (envoy/url-builder (select-keys @conf [:hosts :port :secure?]))
           [consul-path kw] (get-last-fragment clean-path)]
    ;;Base store synchronous sync
    (try
        (envoy/map->consul (build-url consul-path) {kw content} {:serializer :json :overwrite? true})
      (catch Exception e
        (log/info "Can't write data to consul" path e)))
    ;;Mirrors are asynchronously synced
    (future
      (when (some? (:mirrors @conf)))
        (doseq [mirror (:mirrors @conf)]
          (try
            (envoy/map->consul
              ((envoy/url-builder
                (select-keys mirror [:hosts :port :secure?]))
                consul-path)
              {kw content}
              {:serializer :json :overwrite? true})
              (catch Exception e
                (log/info "Can't write to consul mirrors" path e))))))))

(defn get
  ([path]
    (com.linkfluence.store.consul/get (:default-bucket @conf) path true))
  ([path fail-fast]
    (com.linkfluence.store.consul/get (:default-bucket @conf) path))
  ([bucket path fail-fast]
      (let [clean-path (str (clean-slash bucket) "/" (clean-slash path))
            build-url (envoy/url-builder (select-keys @conf [:hosts :port :secure?]))
            [consul-path _] (get-last-fragment clean-path)]
    (try
        (let [res (envoy/consul->map (build-url consul-path) {:offset clean-path :serializer :json})]
            (cond
                (and fail-fast (nil? res)) (throw (Exception. (str "Can't load resource " clean-path " - " consul-path)))
                (nil? res) ""
                :else res))
     (catch Exception e
       (log/error e)
       (if fail-fast
         (throw (Exception. (str "Can't load resource " clean-path " - " consul-path)))
         ""))))))

(defn del
  ([path]
    (del (:default-bucket @conf) path))
  ([bucket path]
      (let [clean-path (str (clean-slash bucket) "/" (clean-slash path))
            build-url (envoy/url-builder (select-keys @conf [:hosts :port :secure?]))]
    (try

        (envoy/delete (build-url clean-path))
     (catch Exception e
       (log/info "Can't delete resource" clean-path))))))

(defn default-bucket
  "For a consul store, bucket is a root dir in kv store"
  []
  (:default-bucket @conf))

(defn configure!
    [consul-conf]
    (let [dbucket (clean-slash (:default-bucket consul-conf))]
      (reset! conf (assoc consul-conf :default-bucket dbucket))
      (log/info @conf)))
