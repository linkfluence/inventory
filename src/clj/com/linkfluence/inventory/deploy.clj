(ns com.linkfluence.inventory.deploy
    (:import [java.util.concurrent LinkedBlockingQueue])
    (:require [clojure.tools.logging :as log]
              [com.linkfluence.inventory.caller :as caller]
              [com.linkfluence.inventory.core :as inventory]
              [com.linkfluence.utils :as utils]))

;@author Jean-Baptiste Besselat
;@Copyright Linkfluence SAS 2018

;;platform agnostic app deployment

;;two main tasks :
;; -> check that minimum tags are available (ie env, app, provider)

;;At Linkfluence we cut deployment into three steps :
; 1- Env configuration (user, envs vars , bashrc, etc), app specific server registration to inventory (after being provisioned)
; 2- Global set (monitoring setup, exploitation scripts setup)
; 3- App Setup

;Deploy : Do 1,2 and 3, update do 2,3, deploy-app do 3, deploy-base/global do 2.

;this can be obviously configured in conf

(def deploy-conf (atom {}))

(defn ro?
 []
 (:read-only @deploy-conf))

(defn gen-resource
  "Update a resource"
  [resource commands]
  (when-not (ro?)
  (when-let [host-tags (inventory/get-resource resource)]
    (let [fqdn (:FQDN host-tags)
          private-ip (:privateIp host-tags)
          host (if private-ip
                    private-ip
                      (if fqdn
                        fqdn
                        resource))]
    ;;deploy-base , deploy-app send
    (caller/add-command {:method :ssh
                         :commands commands
                         :hosts host
                         :sudo false})))))

(defn deploy-resource
  "Deploy a resource (init env, setup global (monitoring, etc), then setup app)"
  [resource]
  (when-not (ro?)
  (when-let [host-tags (inventory/get-resource resource)]
    (inventory/add-inventory-event {:type "resource"
                                    :id resource
                                    :tags [{:name "state" :value "deployment_pending"}]})
    (gen-resource resource (:deploy @deploy-conf)))))

(defn update-resource
  "To update both generic and app specific settings"
  [resource]
  (gen-resource resource (:update @deploy-conf)))

(defn deploy-app-resource
  "For app specific configuration"
  [resource]
  (gen-resource resource (:app @deploy-conf)))

(defn deploy-base-resource
  "For global configuration"
  [resource]
  (gen-resource resource (:global @deploy-conf)))


 (defn end-deployment
   "Update inventory and mark resource as deployed"
   [resource]
   (when-not (ro?)
   (when-let [host-tags (inventory/get-resource resource)]
     (inventory/add-inventory-event {:type "resource"
                                     :id resource
                                     :tags [{:name "state" :value "deployed"}]}))))

 (defn deploy-app-group
   "Deploy a group of resource"
   [group-name]
   (let [resources (inventory/get-group-resources group-name)]
     (doseq [resource resources]
       (deploy-app-resource (name resource)))))

 (defn deploy-base-group
   "Deploy a group of resource"
   [group-name]
   (let [resources (inventory/get-group-resources group-name)]
     (doseq [resource resources]
       (deploy-base-resource (name resource)))))

(defn update-group
  "Deploy a group of resource"
  [group-name]
  (let [resources (inventory/get-group-resources group-name)]
    (doseq [resource resources]
      (update-resource (name resource)))))

(defn deploy-group
 "Deploy a group of resource"
 [group-name]
 (let [resources (inventory/get-group-resources group-name)]
   (doseq [resource resources]
     (deploy-resource (name resource)))))

(defn start!
 [])

(defn configure!
  [conf]
  (reset! deploy-conf conf))
