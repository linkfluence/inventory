(ns com.linkfluence.store.file
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
    [clojure.tools.logging :as log]))

(def conf (atom nil))

(defn check-dir
  [])

(defn normalize-path
  [path]
  (if (= "/" (str (first path)))
                (str/join (rest path))
                path))

(defn put
  ([path content]
    (put (:default-bucket @conf) path content))
  ([bucket path content]
    (let [filepath (str bucket "/" (normalize-path path))]
    (io/make-parents filepath)
    (try
    (with-open [w (io/writer filepath)]
      (.write w content))
      (catch Exception e
        (log/info "Can't write file" path e))))))

(defn get
  ([path]
    (com.linkfluence.store.file/get (:default-bucket @conf) path true))
  ([path fail-fast]
    (com.linkfluence.store.file/get (:default-bucket @conf) path))
  ([bucket path fail-fast]
    (try
     (slurp (str bucket "/" (normalize-path path)))
     (catch Exception e
       (log/error e)
       (if fail-fast
         (throw (Exception. (str "Can't load resource" (normalize-path path))))
         "")))))

(defn del
  ([path]
    (del (:default-bucket @conf) path))
  ([bucket path]
    (try
     (io/delete-file (str bucket "/" (normalize-path path)))
     (catch Exception e
       (log/info "Can't delete resource" (normalize-path path))))))

(defn default-bucket
  "For a file store, bucket is a root dir"
  []
  (:default-bucket @conf))

(defn configure!
    [file-conf]
    (let [dbucket (if (= "/" (str (last (:default-bucket file-conf))))
                    (str/join (butlast (:default-bucket file-conf)))
                    (:default-bucket file-conf))]
      (reset! conf (assoc file-conf :default-bucket dbucket))
      (log/info @conf)))
