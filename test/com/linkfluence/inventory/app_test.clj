(ns com.linkfluence.inventory.app-test
  (:use [clojure.test])
  (:require [com.linkfluence.inventory.app :as app]))

(def env-id "env-test")

(def app-id "app-test")

(def env-tag-request {:app-tags [{:name "my_first_tag" :value "my_first_value"}] :resource-tags [{:name "my_second_tag" :value "my_second_value"}]})

(def app-tag-request  {:tags [{:name "my_third_tag" :value "my_third_value"}] :resource-tags [{:name "my_fourth_tag" :value "my_fourth_value"}]})

(def app-append-tag-request  {:tags [{:name "my_third_tag" :value "my_third_value"}] :resource-tags [{:name "my_fourth_tag" :value "my_fourth_value"}]})

(def env {:description "my-desc"
          :app-tags {:my_first_tag "my_first_value"}
          :resource-tags {:my_second_tag "my_second_value"}})

(def app {:tags {:my_third_tag "my_third_value"}
          :resource-tags {:my_fourth_tag "my_fourth_value"}})

(def app-append {:tags {:my_third_tag "my_third_value"}
        :resource-tags {:my_fourth_tag ["my_fourth_value" "my_second_fourth_value"]}})

(deftest crud-env
    ;;create env
    (#'app/create-env! env-id "my-desc")
    (is (.contains (keys (deref app/apps)) (keyword env-id)))
    ;;tag env
    (#'app/update-env! env-id (:app-tags env-tag-request) (:resource-tags env-tag-request))
    (is (= env (dissoc (get (deref app/apps) (keyword env-id)) :apps)))
    ;;delete env
    (#'app/delete-env! env-id)
    (is (nil? (get (deref app/apps) (keyword env-id)))))

(deftest crud-app
    (#'app/create-env! env-id "my-desc")
    (#'app/update-env! env-id (:app-tags env-tag-request) (:resource-tags env-tag-request))
    (#'app/update-app! app-id env-id (:tags app-tag-request) (:resource-tags app-tag-request))
    (is (= app (get-in (deref app/apps) [(keyword env-id) :apps (keyword app-id)])))
    (#'app/update-app! app-id env-id [] [{:append true :name "my_fourth_tag" :value "my_second_fourth_value"}])
    (is (= app-append (get-in (deref app/apps) [(keyword env-id) :apps (keyword app-id)])))
    ;;delete app
    (#'app/delete-app! app-id env-id)
    (is (nil? (get-in (deref app/apps) [(keyword env-id) :apps (keyword app-id)])))
    ;;delete env
    (#'app/delete-env! env-id))


;;(deftest resource-match)
