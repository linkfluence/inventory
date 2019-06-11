(ns com.linkfluence.inventory.acs.ecs-test
  (:use [clojure.test])
  (:require [com.linkfluence.inventory.acs.ecs :as ecs]
            [com.linkfluence.inventory.acs.common :as common]))

(def describe-result {:instances
 '({:creationTime "2018-09-25T12:15Z",
   :description "",
   :vlanId "",
   :tags
   ({:tagKey "app", :tagValue "gw-acs"}
    {:tagKey "env", :tagValue "dev"}
    {:tagKey "provider", :tagValue "ACS"}),
   :rdmaIpAddress (),
   :vpcAttributes
   {:VSwitchId "vsw-*****************1",
    :natIpAddress "",
    :privateIpAddress '("172.16.253.141"),
    :vpcId "vpc-*******************1"},
   :creditSpecification "standard",
   :innerIpAddress (),
   :serialNumber "8d274df8-0ff4-4fd8-90f1-e3fa07cdb0cc",
   :regionId "cn-hangzhou",
   :operationLocks (),
   :expiredTime "2099-12-31T15:59Z",
   :autoReleaseTime "",
   :startTime "2018-09-25T12:16Z",
   :instanceType "ecs.t5-c1m1.large",
   :instanceId "i-***************1",
   :instanceChargeType "PostPaid",
   :instanceName "test-jbb.cn-hangzhou.dev.acs.rtgi.eu",
   :zoneId "cn-hangzhou-g",
   :internetChargeType "PayByTraffic",
   :dedicatedHostAttribute
   {:dedicatedHostId "", :dedicatedHostName ""},
   :keyPairName "jbb",
   :hostName "test-jbb.cn-hangzhou.dev.acs.rtgi.eu",
   :memory 2048,
   :localStorageAmount nil,
   :stoppedMode "Not-applicable",
   :clusterId "",
   :instanceNetworkType "vpc",
   :securityGroupIds '("sg-bp19k2rrtcy96k7m4vul"),
   :spotPriceLimit 0.0,
   :resourceGroupId "",
   :spotStrategy "NoSpot",
   :saleCycle "",
   :publicIpAddress '("47.99.126.242"),
   :deviceAvailable true,
   :ioOptimized true,
   :status "Running",
   :eipAddress
   {:allocationId "",
    :bandwidth nil,
    :internetChargeType "",
    :ipAddress "",
    :isSupportUnassociate nil},
   :OSType "linux",
   :localStorageCapacity nil,
   :GPUAmount 0,
   :internetMaxBandwidthOut 10,
   :instanceTypeFamily "ecs.t5",
   :networkInterfaces
   '({:macAddress "00:16:3e:0a:f1:d1",
     :networkInterfaceId "eni-bp1hrgs866abc9o7ye72",
     :primaryIpAddress "172.16.253.141"}),
   :imageId "ubuntu_16_0402_64_20G_alibase_20180409.vhd",
   :hpcClusterId nil,
   :OSName "Ubuntu  16.04 64位",
   :GPUSpec "",
   :recyclable false,
   :cpu 2,
   :internetMaxBandwidthIn 500}
  {:creationTime "2018-08-23T13:11Z",
   :description "",
   :vlanId "",
   :tags
   '({:tagKey "app", :tagValue "gw-acs"}
    {:tagKey "env", :tagValue "infra"}),
   :rdmaIpAddress (),
   :vpcAttributes
   {:VSwitchId "vsw-*****************1",
    :natIpAddress "",
    :privateIpAddress '("172.16.253.140"),
    :vpcId "vpc-*******************1"},
   :creditSpecification "standard",
   :innerIpAddress (),
   :serialNumber "fe751f9e-b152-4004-8a8d-6139a7f1017d",
   :regionId "cn-hangzhou",
   :operationLocks (),
   :expiredTime "2018-10-25T16:00Z",
   :autoReleaseTime "",
   :startTime "2018-09-25T10:12Z",
   :instanceType "ecs.t5-lc1m1.small",
   :instanceId "i-***************2",
   :instanceChargeType "PrePaid",
   :instanceName "iZbp1abi62nwo4hlbzei3yZ",
   :zoneId "cn-hangzhou-g",
   :internetChargeType "PayByTraffic",
   :dedicatedHostAttribute
   {:dedicatedHostId "", :dedicatedHostName ""},
   :keyPairName "jbb",
   :hostName "iZbp1abi62nwo4hlbzei3yZ",
   :memory 1024,
   :localStorageAmount nil,
   :stoppedMode "Not-applicable",
   :clusterId "",
   :instanceNetworkType "vpc",
   :securityGroupIds '("sg-bp19k2rrtcy96k7m4vul"),
   :spotPriceLimit 0.0,
   :resourceGroupId "",
   :spotStrategy "NoSpot",
   :saleCycle "",
   :publicIpAddress '("47.98.232.246"),
   :deviceAvailable true,
   :ioOptimized true,
   :status "Running",
   :eipAddress
   {:allocationId "",
    :bandwidth nil,
    :internetChargeType "",
    :ipAddress "",
    :isSupportUnassociate nil},
   :OSType "linux",
   :localStorageCapacity nil,
   :GPUAmount 0,
   :internetMaxBandwidthOut 1,
   :instanceTypeFamily "ecs.t5",
   :networkInterfaces
   '({:macAddress "00:16:3e:09:2a:fa",
     :networkInterfaceId "eni-bp19k2rrtcy96k7i0b1l",
     :primaryIpAddress "172.16.253.140"}),
   :imageId "ubuntu_16_0402_64_20G_alibase_20180409.vhd",
   :hpcClusterId nil,
   :OSName "Ubuntu  16.04 64位",
   :GPUSpec "",
   :recyclable false,
   :cpu 1,
   :internetMaxBandwidthIn 200}),
 :pageNumber 1,
 :pageSize 10,
 :requestId "502CF556-C40F-4260-8397-72D6D58B5B92",
 :totalCount 2})

 (def aliyun-conf { :tags-binding {
         :Name "FQDN"
         :env "RTGI_LEVEL"
         :app "APP"
       }})

 (deftest ecs-test
     (common/set-conf aliyun-conf)
     (ecs/update-acs-inventory! (first (:instances describe-result)))
     (is (= (common/date-hack (first (:instances describe-result))) (ecs/get-instance (keyword "i-***************1"))))
     (ecs/update-acs-inventory! (assoc (first (:instances describe-result)) :delete true))
     (is (nil? (ecs/get-instance (keyword "i-***************1")))))
