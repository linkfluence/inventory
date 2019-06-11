(ns com.linkfluence.dns.zone-test
  (:use [clojure.test])
  (:require [com.linkfluence.dns.common :as common]
            [com.linkfluence.dns.zone :as zone]))

(def conf-test {:sudo false :bind-dir "/tmp" :store {:file {:bucket "/tmp/store"} :key "dns"} :restart false})



;;check that zone received through API are valid
;; 1 slave zone
;; 2 forward zone
;; 3 master zone
(deftest zone-specs
  (is (zone/check-zone {:type "slave" :masters "45.5.5.4; 10.5.6.8;" :name "aws.example.eu"}))
  (is (zone/check-zone {:type "forward" :forwarders "45.5.5.4;" :forward "only" :name "aws.example.eu"}))
  (is (zone/check-zone {:type "master" :name "aws.example.eu"})))

(def zone-file-string "zone \"provider-1\" {
    type master;
    file \"/tmp/db.provider-1\";
    also-notify { 10.2.0.1; 10.2.0.2;};
    notify yes;
};
zone \"provider-1.example.eu\" {
    type master;
    file \"/tmp/db.provider-1\";
    also-notify { 10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;};
    notify yes;
};
zone \"provider-2.example.eu\" {
    type master;
    file \"/tmp/db.provider-2\";
    also-notify { 10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;};
    notify yes;
};
zone \"uk.example.eu\" {
    type master;
    file \"/tmp/db.uk\";
    also-notify { 10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;};
    notify yes;
};
zone \"de.example.eu\" {
    type master;
    file \"/tmp/db.de\";
    also-notify { 10.0.0.1; 10.0.0.2; };
    notify yes;
};
zone \"0.10.in-addr.arpa\" IN {
  type master;
  file \"/tmp/0.10.in-addr.arpa\";
  allow-update { none; };
  also-notify { 10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;};
  notify yes;
};
zone \"eu-west-1.compute.internal\" {
  type forward;
  forward only;
  forwarders { 172.16.0.2; };
};
zone \"dev.aws.example.eu\" {
  type forward;
  forward only;
  forwarders { 172.17.0.2; };
};

zone \"aws.example.eu\" {
  type forward;
  forward only;
  forwarders { 172.16.0.2; };
};")

(def zone-map {:provider-1
 {:also-notify "10.2.0.1; 10.2.0.2;",
  :db "db.provider-1",
  :name "provider-1",
  :type "master",
  :file "/tmp/db.provider-1",
  :notify "yes"},
 :uk.example.eu
 {:also-notify "10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;",
  :db "db.uk",
  :name "uk.example.eu",
  :type "master",
  :file "/tmp/db.uk",
  :notify "yes"},
 :eu-west-1.compute.internal
 {:name "eu-west-1.compute.internal",
  :forwarders "172.16.0.2;",
  :forward "only",
  :type "forward"},
 :provider-1.example.eu
 {:also-notify "10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;",
  :db "db.provider-1",
  :name "provider-1.example.eu",
  :type "master",
  :file "/tmp/db.provider-1",
  :notify "yes"},
 :0.10.in-addr.arpa
 {:also-notify "10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;",
  :db "0.10.in-addr.arpa",
  :allow-update "none;",
  :name "0.10.in-addr.arpa",
  :type "master",
  :file "/tmp/0.10.in-addr.arpa",
  :notify "yes"},
 :dev.aws.example.eu
 {:name "dev.aws.example.eu",
  :forwarders "172.17.0.2;",
  :forward "only",
  :type "forward"},
 :de.example.eu
 {:also-notify "10.0.0.1; 10.0.0.2;",
  :db "db.de",
  :name "de.example.eu",
  :type "master",
  :file "/tmp/db.de",
  :notify "yes"},
 :aws.example.eu
 {:name "aws.example.eu",
  :forwarders "172.16.0.2;",
  :forward "only",
  :type "forward"},
 :provider-2.example.eu
 {:also-notify "10.0.0.1; 10.0.0.2; 172.16.0.2; 10.1.0.1; 10.1.0.2;",
  :db "db.provider-2",
  :name "provider-2.example.eu",
  :type "master",
  :file "/tmp/db.provider-2",
  :notify "yes"}})

;;Check zone parsing && zone file generation
;; 1 parse zone file
;; 2 check zone file match test map
;; 3 generate temp zone file
;; 4 check that zone file generated is parsable and has same map than original
(deftest zone-parse
  (common/configure! conf-test)
  (spit "/tmp/named.conf.local" zone-file-string)
  (is (= (zone/list-zones) zone-map))
  (zone/generate-zones)
  (reset! zone/zone-cache nil)
  (is (= (zone/list-zones) zone-map)))
