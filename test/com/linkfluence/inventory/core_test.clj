(ns com.linkfluence.inventory.core-test
  (:use [clojure.test])
  (:require [com.linkfluence.inventory.core :as inventory]))

(def resource-id "resource-test")

(def group-id "group-test")

(def tag-request {:tags [{:name "my_first_tag" :value "my_first_value"} {:name "my_second_tag" :value "my_second_value"}]})

(def group-tag-request  {:tags [{:name "my_third_tag" :value "my_third_value"}]})

(def resource {:my_first_tag "my_first_value"
               :my_second_tag "my_second_value"})

(def group {:my_third_tag "my_third_value"})

(deftest tag-validation
    (is (inventory/validate-tags [{:name "my_tag" :value "my_value"}]))
    (is (inventory/validate-tags [{:name "my_tag" :delete true}]))
    (is (inventory/validate-tags [{:name "my_tag" :value "my_value" :delete true}]))
    (is (not (inventory/validate-tags [{:name "my_other_tag" :delete false}])))
    (is (inventory/validate-tags [{:name "my_other_tag" :value "my-value" :delete false}])))

(deftest crud-resources
    (#'inventory/update-resource! (assoc tag-request :id resource-id))
    (is (= (inventory/get-resource resource-id) resource))
    ;;single tag match
    (is (.contains
            (keys (inventory/get-resources [{:name "my_second_tag" :value "my_second_value"}]))
            (keyword resource-id)))
    ;;multiple tag matcher
    (is (.contains
            (keys (inventory/get-resources [{:name "my_second_tag" :value ["my_second_value"]}]))
            (keyword resource-id)))
    ;;multiple tag matcher
    (is (.contains
            (keys (inventory/get-resources [{:not true :name "my_second_tag" :value "my_not_value"}]))
            (keyword resource-id)))
    ;;multiple tag matcher
    (is (nil? (keys (inventory/get-resources [{:not true :name "my_second_tag" :value ["my_not_value" "my_second_value"]}]))))
    (#'inventory/update-resource! {:id resource-id :delete true})
    (is (= (inventory/get-resource resource-id) nil)))



(deftest crud-alias
    (#'inventory/update-resource! (assoc tag-request :id resource-id))
    (is (= (get (deref inventory/resources) (keyword resource-id)) resource))
    (#'inventory/update-alias! (assoc tag-request :resource-id resource-id :create true))
    (is (= (inventory/get-alias (str resource-id "_" 1)) (assoc resource :resource-id resource-id :alias true)))
    (#'inventory/update-alias! (assoc tag-request :id (str resource-id "_" 1) :delete true))
    (is (= (inventory/get-alias (str resource-id "_" 1)) nil))
    (#'inventory/update-resource! (assoc tag-request :id resource-id :delete true)))

(deftest crud-group
    (#'inventory/update-group! (assoc group-tag-request :id group-id))
    (is (= (inventory/get-group group-id) (assoc group :name group-id)))
    (#'inventory/update-resource! (assoc tag-request :id resource-id))
    (is (= (inventory/get-resource resource-id) resource))
    ;;add resource to gropup
    (#'inventory/update-resource! (assoc {:tags [{:name "group" :value group-id}]} :id resource-id))
    (is (= (inventory/get-resource resource-id) (merge resource group {:group group-id})))
    (#'inventory/update-group! {:id group-id :delete true})
    (is (= (inventory/get-group group-id) nil))
    (#'inventory/update-resource! {:id resource-id :delete true}))
