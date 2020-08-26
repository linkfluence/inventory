(ns com.linkfluence.inventory.core
  (:import [java.util.concurrent LinkedBlockingQueue])
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.linkfluence.store :as store]
            [com.linkfluence.utils :as u]
            [clj-http.client :as http]
            [cheshire.core :refer :all]
            [clojure.spec.alpha :as spec]
            [com.linkfluence.inventory.caller :as caller]))

; @author Jean-Baptiste Besselat
; @Copyright Linkfluence SAS 2017

; Inventory event update structure
; {
; :type resource or group
; :id (resource_id or group-name)
; :tags Tags vec
; :resources (only for group and optional, list of resources to add to a group)
; :delete true (optional : indicate object deletion)
; }
;
; Tags structure :
; {
;   :name "tags_name"
;   :value "tags_value"
;   :delete true (optional : indicate tags deletion)
; }
;pec validate spec
(spec/def ::name string?)
(spec/def ::value string?)
(spec/def ::delete (spec/and boolean? true?))
(spec/def ::tag (spec/or :add (spec/keys :req-un [::name ::value])
                         :delete (spec/keys :req-un [::name ::delete]
                                    :opt-un [::value])))
(spec/def ::tags (spec/coll-of ::tag))

;;fucntion to validate tag array
(defn validate-tags [tags]
    (spec/valid? ::tags tags))

(defn explain-tags [tags]
    (spec/explain ::tags tags))

; Reserved tags :
; state : available, install_pending, install, provision_pending, provisioned, deployment_pending, deployed
; group : for resource group

;; queue for inventory update
(def ^LinkedBlockingQueue inventory-queue (LinkedBlockingQueue.))

;;this the main resource inventory collecting resources from all providers handlers
(def resources (atom {}))
;;this is the group inventory
(def groups (atom {}))
;;these are inventory alias
(def aliases (atom {}))
;;atom for views
(def views (atom {}))
;;atom for conf
(def conf (atom nil))

(def bulk (atom nil))

(defn ro?
  []
  (:read-only @conf))

;;return queue size
(defn get-event-queue-size
  []
  (.size inventory-queue))


(defn post-event
  [master ev]
  (try
    (http/post
      (str "http://" master "/inventory/event")
      {:content-type :json :body (generate-string {:event ev})})
    true
      (catch Exception e
        false)))

(defn post-bulk
  [master]
  (try
    (http/post
      (str "http://" master "/inventory/events")
      {:content-type :json :body (generate-string {:events @bulk})})
    (reset! bulk nil)
    true
      (catch Exception e
        false)))

(defn send-event
  [master event]
  (cond
    (and (= 0 (.size inventory-queue)) (nil? @bulk))
    (loop []
      (when-not (post-event master event)
        (Thread/sleep 60000)
        (recur)))
    (and (= 0 (.size inventory-queue)) (not (nil? @bulk)))
    (do
      (swap! bulk conj event)
      (loop []
        (when-not (post-bulk master)
          (Thread/sleep 60000)
          (recur))))
    :else (if (nil? @bulk)
            (reset! bulk [event])
            (swap! bulk conj event))))

;;add event Update
(defn add-inventory-event
  [ev]
  (when (or
          (not (ro?))
          (not (nil? (get @conf :master nil))))
    (.put inventory-queue ev)))

;;add event Update
(defn hide-event
  [id hide type]
  (when (or
          (not (ro?))
          (not (nil? (get @conf :master nil))))
    (.put inventory-queue {:type (name type)
                           :id (keyword id)
                           :tags [{:name "hidden" :value (if hide "true" "false")}]})))

(defn tag-matcher
 "Check if an entity group/resource match a tag"
 [entity tag]
 (let [val (:value tag)
       ma (cond
           (or (string? val) (number? val))  (= val ((keyword (:name tag)) entity))
           (coll? val) (.contains (vec val) ((keyword (:name tag)) entity)))]
       (if-not (:not tag)
           ma
           (not ma))))

(defn tags-matcher
 "Check if an entity group/resource match the tags array"
 [entity tags]
 (if-not (= 0 (count tags))
   (loop [tgs tags
         m true]
         (if m
           (if-not (= 0 (count tgs))
             (if (tag-matcher entity (first tgs))
               (recur (next tgs) true)
               false)
               m)
             false))
   true))


;;get resources
(defn get-resources
  "Just return resources atom filtered or not"
  ([]
    (get-resources [] false))
  ([tags] (get-resources tags false))
  ([tags with-alias?]
      (let [ress (if with-alias? (merge @resources @aliases) @resources)]
      (into {} (filter
                    (fn [[k v]]
                      (tags-matcher v (conj tags {:not true :value "true" :name "hidden"})))
                     ress)))))

(defn get-tag-value-from-aggregated-resources
 "Return resource aggregated"
 ([itag tags with-alias]
   (get-tag-value-from-aggregated-resources itag tags with-alias (get-resources [] with-alias)))
 ([itag tags with-alias ress]
   (if (and
           (spec/valid? (spec/coll-of string?) tags)
           (spec/valid? (spec/or :k keyword? :s string?) itag)
           (spec/valid? (spec/or :nil nil? :bool boolean?) with-alias))
   (let [r-tags (rest tags)
         cr-tags (count r-tags)]
     ;; we treat first tag of the list
     (if-let [tag (first tags)]
       (let [step (loop [rest-ress ress
                         inv {}]
           ;;we walk across resource
           (if-let [a (first rest-ress)]
             (let [[k v] a]
               (if-let [tv ((keyword tag) v)]
                 (recur
                   (rest rest-ress)
                   (assoc-in
                     inv
                     [(keyword tag) (keyword tv) k] v))
                 (recur
                   (rest rest-ress)
                   (assoc-in
                   inv
                   [(keyword tag) :undefined k] v
                   ))))
                 inv))]
       (if-not (= 0 cr-tags)
         (into {} (map
           (fn [[k v]]
             [k (into {} (map (fn [[kt vt]]
                   [kt (get-tag-value-from-aggregated-resources itag r-tags with-alias vt)]) v))]) step))
         (into {} (map
           (fn [[k v]]
             [k (into {} (map (fn [[kt vt]]
                   [kt (filter
                         (fn [x] (not (nil? x)))
                         (map (fn [[kr vr]] ((keyword itag) vr)) vt)
                  )]) v))]) step))))
       ress))
       {:error {:tags (spec/explain-str (spec/coll-of string?) tags)
                :tag (spec/explain-str (spec/or :k keyword? :s string?) itag)
                :with-alias (spec/explain-str (spec/or :nil nil? :bool boolean?) with-alias)}})))

  (defn- aggregate-resources
      [tags ress]
          (if (spec/valid? (spec/coll-of string?) tags)
      (let [r-tags (rest tags)
            cr-tags (count r-tags)]
        ;; we treat first tag of the list
        (if-let [tag (first tags)]
          (let [step (loop [rest-ress ress
                            inv {}]
              ;;we walk across resource
              (if-let [a (first rest-ress)]
                (let [[k v] a]
                  (if-let [tv ((keyword tag) v)]
                    (recur
                      (rest rest-ress)
                      (assoc-in
                        inv
                        [(keyword tag) (keyword tv) k] v))
                    (recur
                      (rest rest-ress)
                      (assoc-in
                      inv
                      [(keyword tag) :undefined k] v
                      ))))
                    inv))]
          (if-not (= 0 cr-tags)
            (into {} (map
              (fn [[k v]]
                [k (into {} (map (fn [[kt vt]]
                      [kt (aggregate-resources r-tags vt)]) v))]) step))
            step))
          ress))
          {:error {:tags (spec/explain-str (spec/coll-of string?) tags)}}))

  (defn get-aggregated-resources
    "Return resource aggregated by tag"
    ([tags with-alias?]
          (get-aggregated-resources tags with-alias? []))
    ([tags with-alias? filters]
          (aggregate-resources tags (get-resources filters with-alias?))))

(defn- res-to-csv-line
  [ress tags]
  (let [[k v] ress]
    (str (name k)
      ","
      (str/join "," (map
                      (fn [tag]
                        (str (get v (keyword tag))))
                         tags))
      "\n")))

(defn get-csv-resources
  ([tags with-alias?]
        (get-csv-resources tags with-alias? [] true))
  ([tags with-alias? filters]
        (get-csv-resources tags with-alias? filters true))
  ([tags with-alias? filters add-header]
        (if (spec/valid? (spec/coll-of string?) tags)
          (let [ress (get-resources filters with-alias?)
                head (if add-header (str "id," (str/join "," tags) "\n") "")]
                (loop [rest-ress ress
                       csv head]
                    ;;we walk across resource
                    (if-let [a (first rest-ress)]
                        (recur
                          (rest rest-ress)
                          (str csv (res-to-csv-line a tags)))
                        csv)))
          {:error {:tags (spec/explain-str (spec/coll-of string?) tags)}})))

(defn load-inventory
  []
  ;;applicable if we wan't to store something (ie : not during test)
  (when (:store @conf)
  ;load groups
  (when-let [gps (store/load-map (assoc (:store @conf) :key "groups"))]
    (reset! groups gps))
  ;load resources
  (when-let [ress (store/load-map (assoc (:store @conf) :key "resources"))]
    (reset! resources ress))
  ;load alias
  (when-let [aliass (store/load-map (assoc (:store @conf) :key "aliases"))]
    (reset! aliases aliass))
  ;; generate views
  (doseq [view (:views @conf)]
      (if (:tag view)
        (let [tag-view (get-tag-value-from-aggregated-resources (:tag view) (:tags view) (:with-alias? view))]
          (swap! views assoc (keyword (:name view)) tag-view))
        (let [res-view (get-aggregated-resources (:tags view) (:with-alias? view))]
          (swap! views assoc (keyword (:name view)) res-view))))))

;;store inventory backup
(defn save-inventory
  []
  ;;applicable if we wan't to store something (ie : not during test)
  (when (:store @conf)
  (when-not (ro?)
  ;save groups
  (store/save-map (assoc (:store @conf) :key "groups") @groups)
  ;save resources
  (store/save-map (assoc (:store @conf) :key "resources") @resources)
  ;save alias
  (store/save-map (assoc (:store @conf) :key "aliases") @aliases)

  (doseq [view (:views @conf)]
    (store/save-map
      (assoc (:store @conf) :key (:name view))
      (if (:tag view)
        (let [tag-view (get-tag-value-from-aggregated-resources (:tag view) (:tags view) (:with-alias? view))]
          (swap! views assoc (keyword (:name view)) tag-view)
          tag-view)
        (let [res-view (get-aggregated-resources (:tags view) (:with-alias? view))]
          (swap! views assoc (keyword (:name view)) res-view)
          res-view)))))))

;;group mgt
(defn get-group-tags
  [group-name]
  (get @groups (keyword group-name) {}))

(defn get-group
  [group-name]
  (get @groups (keyword group-name) nil))

(defn list-groups
  []
  (into [] (map (fn [[k v]] k) @groups)))

(defn get-view
  [view-name]
  (get @views (keyword view-name) nil))

(defn list-views
  []
 (into [] (map (fn [[k v]] k) @views)))

(defn count-groups
  ([] (count @groups))
  ([tags] (count (filter (fn [[k v]] (tags-matcher v tags)) @groups))))

(defn get-group-members
"return group resource"
[group-name dataset]
(let [gpn (name group-name)]
  (filter
    (fn [x] (not (nil? x)))
    (map
      (fn [[k v]]
        (if (= gpn (get v :group ""))
          k
          nil))
       dataset))))

(defn get-group-resources
  "return group resource"
  [group-name]
  (get-group-members group-name @resources))

 (defn get-group-aliases
  "return group aliases"
   [group-name]
   (get-group-members group-name @aliases))

(defn get-tag-value-from-groups
  [tag]
  (into [](filter
              (fn [x] (not (nil? x)))
              (map
                (fn [[k v]] ((keyword tag) v))
                @groups))))

(defn get-group-resources-tag
 "return the asked tag of all resource of a group"
 [group-name tag]
 (filter
   (fn [x] (not (nil? x)))
   (map
     (fn [[k v]]
       (if (= group-name (get v :group ""))
         (get v (keyword tag) nil)
         nil))
      @resources)))

(defn copy-group-tags-to-resource!
  "Import tag of a group to a resource, obviously ignoring group name (ie: name tag)"
  [group-name resource-id]
  (let [group ((keyword group-name) @groups)]
    (doseq [[k v] group]
      (when-not (= :name k)
        (swap! resources assoc-in [(keyword resource-id) k] v)))))

(defn copy-group-tags-to-alias!
  "Import tag of a group to a alias, obviously ignoring group name (ie: name tag)"
  [group-name alias-id]
  (let [group ((keyword group-name) @groups)]
    (doseq [[k v] group]
      (when-not (= :name k)
        (swap! aliases assoc-in [(keyword alias-id) k] v)))))

;;get-aliases
(defn get-aliases
    ([]
        (get-aliases []))
    ([tags]
        (into {} (filter
                      (fn [[k v]]
                        (tags-matcher v (conj tags {:not true :value "true" :name "hidden"})))
                       @aliases))))

(defn alias-exists?
    [rid from-resource tags]
    ;;retrieve tags from base resource
    (let [res-tags (select-keys (get @resources (keyword rid) nil) (map keyword from-resource))
          res-tags (map (fn [[k v]] {:name (name k) :value v}) res-tags)
          all-tags (concat res-tags tags [{:name "resource-id" :value (name rid)}])]
    (log/info all-tags)
    ;;try to match an alias
    (< 0 (count (keys (get-aliases all-tags))))))

(defn- create-alias-id
    [resource-id]
    (loop [i 1]
        (if (nil? (get @aliases (keyword (str (name resource-id) "_" i))))
            (keyword (str (name resource-id) "_" i))
            (recur (inc i)))))

(defn get-alias-tags
  [id]
      (get @alias (keyword id) {}))

(defn- update-alias!
    [ev]
    (if-let [kid (keyword (:id ev))]
        ;;resource like ops
        (when-let [alias (get @aliases kid)]
            (if-not (:delete ev)
                ;;update alias
                (do
                  (doseq [tag (:tags ev)]
                    (if-not (:delete tag)
                      (do
                        (swap! aliases assoc-in [kid (keyword (:name tag))] (:value tag))
                        (when (= :group (keyword (:name tag)))
                          (copy-group-tags-to-alias! (:value tag) kid)))
                     (when-not (= :resource-id (keyword (:name tag)))
                      (swap! aliases assoc kid (dissoc alias (keyword (:name tag))))))))
                ;;deletion
                (swap! aliases dissoc kid))
                (when (= 0 (.size inventory-queue))
                  (save-inventory)
                  (u/fsync "inventory")))
        (let [rid (:resource-id ev)
              nrid (name rid)]
        ;;alias creation
        (if (and
                (= (:create ev) true)
                (not (nil? rid))
                (not (nil? (get @resources (keyword rid))))
                (not (and
                        (:create-only ev)
                        (alias-exists? rid (:from-resource ev) (:tags ev)))))
            (let [alias-id (create-alias-id rid)
                  alias (if (and (:from-resource ev) (> (count (:from-resource ev)) 0))
                            (assoc
                                (select-keys
                                    (get @resources (keyword rid))
                                    (map keyword (:from-resource ev)))
                                :resource-id nrid
                                :alias true)
                                {:resource-id nrid :alias true})]
                (swap! aliases assoc alias-id alias)
                (update-alias! (assoc ev :id alias-id)))
            (log/error "the resource doesn't exist")))))

(defn- propagate-group-tags
    "Propagate tags to either resource or alias"
    [id members type dataset]
    (doseq [member members]
      (when-not (nil? (get dataset (keyword member) nil))
         (add-inventory-event {:type type :id member :tags [{:name "group" :value (name id)}]}))))

(defn- update-group!
  "update group tags, delete, etc"
  [ev]
  (let [kid (keyword (:id ev))]
    (if-not (:delete ev)
      ;;add tags or create new group
      (do
        ;; set group name
        (swap! groups assoc-in [kid :name] (:id ev))
        ;; update group atom
        (doseq [tag (:tags ev)]
          (if-not (:delete tag)
            (swap! groups assoc-in [kid (keyword (:name tag))] (:value tag))
            (swap! groups assoc kid (dissoc (get-group-tags kid) (keyword (:name tag))))))
          ;;add eventual group resources
        (when-let [ress (:resources ev)]
            (propagate-group-tags (:id ev) ress "resource" @resources))
              ;;alias
        (when-let [aliass (:alias ev)]
            (propagate-group-tags (:id ev) aliass "alias" @aliases))
        ;;get group resources member
        (let [resources (get-group-resources (name (:id ev)))]
          (doseq [res resources]
            (add-inventory-event {:type "resource" :id res :tags (:tags ev)})))
        ;;get group alias
        (let [aliass (get-group-aliases (name (:id ev)))]
          (doseq [alias aliass]
            (add-inventory-event {:type "alias" :id alias :tags (:tags ev)}))))
      ;;delete group
      (do
        ;;wipe this group in resources
        (doseq [[k v] @resources]
          (when (= (:group v) (name kid))
            (swap! resources assoc k (dissoc v :group))))
        ;;wipe this groups from inventory
        (swap! groups dissoc kid))))
    ;;save everything
    (when (= 0 (.size inventory-queue))
      (save-inventory)
      (u/fsync "inventory")))

;;get resources
(defn get-resources
  "Just return resources atom filtered or not"
  ([]
    (get-resources [] false))
  ([tags] (get-resources tags false))
  ([tags with-alias?]
      (let [ress (if with-alias? (merge @resources @aliases) @resources)]
      (into {} (filter
                    (fn [[k v]]
                      (tags-matcher v (conj tags {:not true :value "true" :name "hidden"})))
                     ress)))))

(defn get-resource-aliases
    "Retrieve resource-id aliases"
    [resource-id]
    (get-aliases [{:name "resource-id" :value (name resource-id)}]))

;;get-aliases
(defn get-hidden-aliases
 ([]
     (get-aliases []))
 ([tags]
     (into {} (filter
                   (fn [[k v]]
                     (tags-matcher v (conj tags {:value "true" :name "hidden"})))
                    @aliases))))

(defn get-hidden-resources
  "Just return resources atom filtered or not"
  ([]
    (get-hidden-resources []))
  ([tags]  (get-hidden-resources tags false))
  ([tags with-alias?] (let [ress (if with-alias? (merge @resources @aliases) @resources)]
      (into {} (filter
                    (fn [[k v]]
                      (tags-matcher v (conj tags {:value "true" :name "hidden"})))
                     ress)))))

(defn count-resources
  ([] (count-resources []))
  ([tags] (count (filter
                    (fn [[k v]]
                      (tags-matcher v (conj tags {:not true :value "true" :name "hidden"})))
                      @resources))))

(defn get-resource
  "return a resource specified by id"
  ([id]
      (get-resource id false))
  ([id with-alias?]
      (let [ress (if with-alias? (merge @resources @aliases) @resources)]
        (get ress (keyword id) nil))))


(defn get-alias
    "return a resource specified by id"
    [id]
    (get @aliases (keyword id) nil))


(defn get-tag-value-from-resources
  ([tag]
    (get-tag-value-from-resources tag nil))
  ([tag tags]
    (get-tag-value-from-resources tag tags false))
  ([tag tags with-alias?]
      (into []
      (keys
        (reduce (fn [acc val] (assoc acc val true)) {}
          (filter
              (fn [x] (not (nil? x)))
              (map
                (fn [[k v]] ((keyword tag) v))
                (get-resources tags with-alias?))))))))

(defn get-tags-stats-from-resources
  ([tag]
    (get-tags-stats-from-resources tag nil false))
  ([tag tags]
    (get-tags-stats-from-resources tag tags false))
  ([tag tags with-alias?]
  (into {}
      (reduce (fn [acc val]
                (if-not (nil? val)
                  (assoc acc val (inc (get acc val 0)))
                  (assoc acc "not_defined" (inc (get acc "not_defined" 0))))) {}
            (map
              (fn [[k v]] ((keyword tag) v))
              (get-resources tags with-alias?))))))

(defn get-resource-tags-list
  "get tag list"
  [& [with-alias?]]
  (into []
    (keys
      (reduce (fn [acc [k v]]
                (reduce (fn [accu [ku vu]]
                          (assoc accu (name ku) true))
                acc v)) {} (if with-alias? (merge @resources @aliases) @resources)))))

(defn get-resource-tags
  ([id]
      (get @resources (keyword id) {}))
      ([id with-alias?]
          (get (if with-alias? (merge @resources @aliases) @resources) (keyword id))))

;;Update
(defn- update-resource!
  "update a resource or delete it"
  [ev]
  (let [kid (keyword (:id ev))]
    ;;resource reinstall
    (when (:reinstall ev)
      (let [hosts [(:default-host ev) (:privateIp (kid @resources)) (:publicIp (kid @resources)) (:FQDN (kid @resources))]
            hosts* (filter (fn [x] (not (nil? x))) hosts)]
            (caller/reset-ssh-key hosts*)))
    (if-not (:delete ev)
      ;;assoc resource tags
      (do
        (doseq [tag (:tags ev)]
          (if-not (:delete tag)
            (do
              (swap! resources assoc-in [kid (keyword (:name tag))] (:value tag))
              (when (or (= :group (:name tag)) (= "group" (:name tag)))
                (copy-group-tags-to-resource! (:value tag) kid)))
            (swap! resources assoc kid (dissoc (get-resource-tags kid) (keyword (:name tag)))))))
      ;;Add prevent from deletion tags
      (when-not (= (:prevent_from_deletion (get-resource-tags kid)) "true")
        ;;wipe resources alias
        (doseq [[k v] @aliases]
          (when (= (:resource-id v) (name kid))
            (swap! aliases dissoc k)))
        (swap! resources dissoc kid))))
    ;;save everything
    (when (= 0 (.size inventory-queue))
      (save-inventory)
      (u/fsync "inventory")))

(defn update-inventory!
  "generic inventory update"
  [ev]
  (if-not (ro?)
    (condp = (:type ev)
      "group" (update-group! ev)
      "resource" (update-resource! ev)
      "alias" (update-alias! ev))
    (when-let [master (get @conf :master nil)]
      (send-event master ev))))

(defn- start-inventory-consumer!
  []
  (u/start-thread!
      (fn [] ;;consume queue
        (when-let [ev (.take inventory-queue)]
          (update-inventory! ev)))
      "Inventory Consumer"))

(defn start!
  []
  (if (or
        (not (ro?))
        (and (ro?)
              (not (nil? (get @conf :master)))))
    [(start-inventory-consumer!)]
    []))

(defn configure!
  [inventory-conf]
  (reset! conf inventory-conf)
  (load-inventory))
