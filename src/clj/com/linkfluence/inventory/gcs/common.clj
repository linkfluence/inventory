(ns com.linkfluence.inventory.gcs.common
  (:require [clojure.string :as str]
            [clj-time.core :as t]
            [cheshire.core :as json]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2020 / Adot 2020

;;gcs conf
(def conf (atom nil))

(defn set-conf
  [config]
  (reset! conf config))

(defn get-conf
  []
  @conf)

  (defn ro?
    []
    (:read-only @conf))
