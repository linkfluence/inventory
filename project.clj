(defproject linkfluence/inventory "0.16.11-SNAPSHOT"
  :description "Rtgi inventory App"
  :url "http://www.linkfluence.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 ;; logging stuff
                 [org.clojure/tools.logging "1.2.1"]
                 [org.slf4j/slf4j-api "1.7.32"]
                 [org.slf4j/slf4j-log4j12 "1.7.32"]
                 [org.slf4j/jcl-over-slf4j "1.7.32"]
                 [org.apache.logging.log4j/log4j-core "2.15.0"]
                 ;; time mgt
                 [clj-time "0.15.2"]
                 ;; web service
                 [compojure "1.6.1"]
                 [ring "1.8.0"]
                 [ring-middleware-format "0.7.4"]
                 [bk/ring-gzip "0.3.0"]
                 ;;for proxy
                 [clj-http "3.7.0"]
                 [digest "1.4.4"]
                 ;;json yaml mgt
                 [cheshire "5.9.0"]
                 [clj-commons/clj-yaml "0.7.0"]
                 ;;jackson
                 [com.fasterxml.jackson.core/jackson-core "2.11.0"]
                 [com.fasterxml.jackson.core/jackson-databind "2.11.0"]
                 [com.fasterxml.jackson.core/jackson-databind "2.11.0"]
                 ;;aws
                 [amazonica "0.3.156"]
                 ;;internal cloud lib
                 [luhhujbb/clj-aliyun "0.2.4"]
                 [luhhujbb/clj-ovh "0.1.13"]
                 [luhhujbb/leaseweb "0.3.7"]
                 [luhhujbb/clj-gcloud-compute "0.118.0-alpha-3"]
                 ;;scheduling
                 [jarohen/chime "0.2.2"]
                 ;;Storage
                 [luhhujbb/oss-117 "0.1.3"] ;;ali cloud
                 [luhhujbb/envoy "0.3.1"] ;;consul
                 ;;prometheus
                 [online.duevel/clometheus "0.1.0"]
                 ;;Networking automation
                 [clj-commons/clj-ssh "0.5.15"]
                 [commons-net/commons-net "3.6"]
                 ;;journal storer, esfs object saver
                 [cc.qbits/spandex "0.7.4"]
                 ;;conf template (dns conf generation)
                 [luhhujbb/clostache "1.5.0"]
                 ;;distributed queing
                 [org.apache.kafka/kafka_2.12 "2.4.1"]]
  :main ^:skip-aot com.linkfluence.starter
  :target-path "target/%s"
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :profiles {:uberjar {:aot :all}})
