(ns com.linkfluence.inventory.api.aws
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.tools.logging :as log]
            ;;import inventory handler
            [com.linkfluence.inventory.aws.common :refer [ro?]]
            [com.linkfluence.inventory.aws.ec2 :as ec2]
            [com.linkfluence.inventory.aws.autoscaling :as asg]
            [com.linkfluence.inventory.aws.rds :as rds]
            [com.linkfluence.inventory.aws.elasticache :as elasticache]
            [com.linkfluence.utils :refer [mk-resp]]))

;;aws resource mgt
(defroutes AWS
  ;;EC2 API
  (GET "/ec2/fsync" [] (if (ro?)
                        (do
                          (future
                            (ec2/load-inventory!)
                            (log/info "[FSYNC] received aws ec2 update notfication"))
                          (mk-resp 202 "success" {} "Fsync submitted"))
                          (mk-resp 403 "error" {} "Not Read Only")))
  (GET "/ec2/refresh" [] (if-not  (ro?)
                            (do
                              (future (ec2/refresh nil))
                              (mk-resp 200 "success" {} "refresh submitted"))
                            (mk-resp 403 "error" {} "read only : refresh forbidden")))
  (GET "/ec2/refresh/:id" [id] (if-not  (ro?)
                            (do
                              (future (ec2/refresh-instance id))
                              (mk-resp 200 "success" {} "refresh of instance submitted"))
                            (mk-resp 403 "error" {} "read only : refresh forbidden")))
  (GET "/ec2" [] (mk-resp 200 "success" {:data (ec2/get-aws-inventory)}))
  (POST "/ec2" [tags] (mk-resp 200 "success" {:data (ec2/get-aws-inventory tags)}))
  (GET "/ec2/:id" [id] (mk-resp 200 "success" {:data (ec2/get-instance id)}))
  (GET "/ec2/:id/tags" [id] (mk-resp 200 "success" {:data (ec2/get-instance-tags id)}))
  ;;Autoscaling Group
  (GET "/asg/fsync" [] (if (ro?)
                        (do
                          (future
                            (asg/load-inventory!)
                            (log/info "[FSYNC] received aws asg update notfication"))
                          (mk-resp 202 "success" {} "Fsync submitted"))
                          (mk-resp 403 "error" {} "Not Read Only")))
  (GET "/asg" [] (mk-resp 200 "success" {:data (asg/get-aws-inventory)}))
  (POST "/asg" [tags] (mk-resp 200 "success" {:data (asg/get-aws-inventory tags)}))
  (GET "/asg/:id" [id] (mk-resp 200 "success" {:data (asg/get-asg id)}))
  (POST "/asg/:id/desired-capacity" [id size] (mk-resp 200 "success" {:data (asg/set-desired-capacity id size)}))
  ;; RDS
  (GET "/rds/fsync" [] (if (ro?)
                        (do
                          (future
                            (asg/load-inventory!)
                            (log/info "[FSYNC] received aws rds update notfication"))
                          (mk-resp 202 "success" {} "Fsync submitted"))
                          (mk-resp 403 "error" {} "Not Read Only")))
  (GET "/rds" [] (mk-resp 200 "success" {:data (rds/get-aws-inventory)}))
  (POST "/rds" [tags] (mk-resp 200 "success" {:data (rds/get-aws-inventory tags)}))
  (GET "/rds/:id" [id] (mk-resp 200 "success" {:data (rds/get-db-instance id)}))
  (GET "/rds/:id/tags" [id] (mk-resp 200 "success" {:data (rds/get-db-instance-tags id)}))
  ;; Elasticache
  (GET "/elasticache/fsync" [] (if (ro?)
                        (do
                          (future
                            (elasticache/load-inventory!)
                            (log/info "[FSYNC] received aws elasticache update notfication"))
                          (mk-resp 202 "success" {} "Fsync submitted"))
                          (mk-resp 403 "error" {} "Not Read Only")))
  (GET "/elasticache" [] (mk-resp 200 "success" {:data (elasticache/get-aws-inventory)}))
  (GET "/elasticache/:id" [id] (mk-resp 200 "success" {:data (elasticache/get-cluster id)}))
  ;;Tag API
  (GET "/tag/ec2" [] (mk-resp 200 "success" {:data (ec2/get-instance-tags-list)}))
  (GET "/tag/ec2/:tag" [tag] (mk-resp 200 "success" {:data (ec2/get-tag-value-from-instances tag)}))
  (GET "/tag/asg" [] (mk-resp 200 "success" {:data (asg/get-asg-tags-list)}))
  (GET "/tag/asg/:tag" [tag] (mk-resp 200 "success" {:data (asg/get-tag-value-from-asgs tag)}))
  ;;AZ
  (POST "/az/ec2" [az] (mk-resp 200 "success" {:data (ec2/get-aws-inventory-by-az az)}))
  ;;Stats API
  (GET "/stats/ec2/ami" [] (mk-resp 200 "success" {:data (ec2/get-instance-ami-stats)}))
  (POST "/stats/ec2/ami" [tags] (mk-resp 200 "success" {:data (ec2/get-instance-ami-stats tags)}))
  (GET "/stats/ec2/type" [] (mk-resp 200 "success" {:data (ec2/get-instance-type-stats)}))
  (POST "/stats/ec2/type" [tags] (mk-resp 200 "success" {:data (ec2/get-instance-type-stats tags)}))
  (GET "/stats/ec2/az" [] (mk-resp 200 "success" {:data (ec2/get-az-stats)}))
  (GET "/stats/ec2/subnet" [] (mk-resp 200 "success" {:data (ec2/get-subnet-stats)}))
  (GET "/stats/ec2/state" [] (mk-resp 200 "success" {:data (ec2/get-state-stats)}))
  )
