(ns com.linkfluence.es
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [qbits.spandex :as s]
            [ring.util.codec :refer [url-encode]])
  (:import [java.net URL]
           [clojure.lang ExceptionInfo])
  (:gen-class))

;;######################################
;; Query shortcut
;;######################################
(defn match-all
  []
  {:match_all {}})

;;######################################
;; Spandex shortcuts
;;######################################

(defn connect
  "[TESTED] Client builder"
  [url]
  (let [u (URL. url)
        h (.getHost u)
        p (.getPort u)]
    {:conn (s/client {:hosts [(str "http://" h ":" p)]})
     :http-conn (str "http://" h ":" p)}))


;; missing bulk op in elastisch
(def special-operation-keys [:_doc_as_upsert
                            :_type
                             :_index
                             :_id
                             :_retry_on_conflict
                             :_routing
                             :_percolate
                             :_parent
                             :_script
                             :_script_params
                             :_scripted_upsert
                             :_timestamp ;;this is deprecated
                             :_ttl ;;this is deprecated
                             :_version
                             :_version_type])

;;Bulk
;;----

(defn bulk
  "Bulk with spandex"
  [conn operations]
  (:body
    (s/request
   conn
   {:url "_bulk"
    :method :put
    ;:query-string {:consistency "quorum"}
    :headers {"Content-Type" "application/x-ndjson"}
    :body (s/chunks->body operations)})))

(defn mk-bulk-operation
  [op doc]
  {op (select-keys doc special-operation-keys)})

(defn mk-bulk
  "generates the content for a bulk with op operation"
  ([op documents]
   (let [operations (map (partial mk-bulk-operation op) documents)
         documents  (map #(apply dissoc % special-operation-keys) documents)]
     (interleave operations documents))))

(defn create
  "[TESTED]"
  [conn docs]
  (bulk conn (mk-bulk "create" docs)))

(defn index
  "[TESTED]"
  [conn docs]
  (bulk conn (mk-bulk "index" docs)))

(defn update
  "[TESTED]"
  [conn docs]
  (bulk conn (mk-bulk "update" docs)))

(defn hits-from
  [resp]
  (get-in resp [:hits :hits]))

(defn next-scroll-page
  [conn scroll-id ttl]
  (:body
    (try
    (s/request conn
      {
        :method :post
        :url "/_search/scroll"
        :body {:scroll_id scroll-id
               :scroll ttl}})
       (catch Exception e
         {:body {:error e}}))))

(defn scroll-seq
  [conn s ttl]
  (let [hits (hits-from s)
        scroll-id (:_scroll_id s)
        errors (:error s)]
        (when errors
          (log/error "[SCROLL] errors in scroll" errors)
          (throw (Exception. "[SCROLL] errors in scroll")))
        (if (seq hits)
          (concat hits (lazy-seq (scroll-seq
                                    conn
                                    (next-scroll-page conn scroll-id ttl)
                                    ttl)))
          hits)))

(defn scroll
  "[TESTED]"
  [conn index pagination-size & [query]]
    (let [query (if (nil? query)
                (match-all)
                query)
        ttl "2m"
        res (s/request conn
                      {:method :post
                       :url [index :_search]
                       :query-string {:scroll ttl}
                       :body {
                                :query query
                                :sort ["_doc"]
                                :size pagination-size
                                }})]
        {:hits (scroll-seq conn (:body res) ttl)
         :total (get-in res [:body :hits :total])}))

(defn sliced-scroll
  [conn index pagination-size slice-id slice-nb & [query]]
    (let [query (if (nil? query)
                (match-all)
                query)
        ttl "2m"
        res (s/request conn
                      {:method :post
                       :url [index :_search]
                       :query-string {:scroll ttl}
                       :body {  :slice {:id slice-id
                                        :max slice-nb}
                                :query query
                                :sort ["_doc"]
                                :size pagination-size
                                }})]
          {:hits (scroll-seq conn (:body res) ttl)
           :total (get-in res [:body :hits :total])}))

;;Docs count
;;----------

(defn index-count
  "[TESTED]"
  [conn index & [query]]
  (let [query (if (nil? query)
                (match-all)
                query)]
        (get-in (s/request conn {:url [index :_search]
                                 :method :get
                                 :body {:query query
                                        :size 0}})
          [:body :hits :total])))

;;Aliases
;;--------------------
(defn clean-aliases-op
  [{:keys [add remove]}]
  (if-not (nil? remove)
    {:remove (dissoc remove :filter :index_routing :search_routing)}
    {:add add}))

(defn clean-aliases-ops
  [updates]
  (into [] (map clean-aliases-op updates)))

(defn update-aliases
  "[TESTED]"
  [conn updates]
  (try
  (s/request conn {:url [:_aliases]
              :method :post
              :body {:actions (clean-aliases-ops updates)}})
    (catch ExceptionInfo e
      (ex-data e))
    (catch Exception e
      {:body {}})))

(defn get-aliases
  "[TESTED]"
 [conn index]
 (:body
   (try
     (s/request conn {:url [index :_aliases]
                    :method :get})
      (catch ExceptionInfo e
        (ex-data e))
      (catch Exception e
        {:body {}}))))

;;Index stuffs
;;------------
(defn set-index-refresh-interval
  "[TESTED]"
  [conn index interval]
  (s/request conn {:method :put
                   :url [index :_settings]
                   :body {:index {:refresh_interval interval}}}))

(defn create-index
  "[TESTED]"
  [conn index settings & [mappings]]
  (try
    (let [body (if mappings
                  {:settings settings
                   :mappings mappings}
                   {:settings settings})]
    (println "Create index" body)
    (s/request conn {:method :put
                   :url [index]
                   :body body}))
    (catch ExceptionInfo e
      (ex-data e))
    (catch Exception e
      {:body {}})))

(defn delete-index
  "[TESTED]"
  [conn index]
  (s/request conn {:method :delete
                   :url [index]}))

(defn refresh-index
  "[TESTED]"
  [conn index]
  (s/request conn {:method :post
                   :url [index :_refresh]}))

(defn indices-settings
  [conn & [index]]
  (:body
    (let [url (if index
                [index :_settings]
                [:_settings])]
    (s/request conn {:method :get
                     :url url}))))

;;Docs stuffs
;;-----------

(defn delete-by-query
  "[TESTED]"
  [conn index query]
  (s/request conn {
    :url [index :_delete_by_query]
    :method :post
    :body {:query query}}))

(defn delete-doc
  [conn index doc-id]
  (delete-by-query conn index {:term {:_id doc-id}}))

(defn index-doc
  [conn index doc]
  (s/request conn {
    :url [index :_doc]
    :method :post
    :body doc}))

;;#################################
;; END of spandex shortcuts
;;#################################
