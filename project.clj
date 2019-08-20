(defproject linkfluence/inventory "0.16.2-SNAPSHOT"
  :description "Rtgi inventory App"
  :url "http://www.linkfluence.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 ;; logging stuff
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.19"]
                 [org.slf4j/slf4j-log4j12 "1.7.19"]
                 [org.slf4j/jcl-over-slf4j "1.7.19"]
                 [log4j "1.2.17"]
                 ;; time mgt
                 [clj-time "0.13.0"]
                 ;; web service
                 [compojure "1.5.2"]
                 [ring "1.5.1"]
                 [ring-middleware-format "0.7.2"]
                 [bk/ring-gzip "0.1.1"]
                 ;;for proxy
                 [clj-http "3.7.0"]
                 [digest "1.4.4"]
                 ;;json yaml mgt
                 [cheshire "5.8.0"]
                 [circleci/clj-yaml "0.5.5"]
                 ;;aws
                 [amazonica "0.3.127"]
                 ;;internal cloud lib
                 [luhhujbb/clj-aliyun "0.2.4"]
                 [luhhujbb/clj-ovh "0.1.13"]
                 [luhhujbb/leaseweb "0.3.7"]
                 ;;scheduling
                 [jarohen/chime "0.2.2"]
                 ;;Storage
                 [luhhujbb/oss-117 "0.1.3"] ;;ali cloud
                 [linkfluence/envoy "0.2.5"] ;;consul
                 ;;Networking automation
                 [clj-ssh "0.5.14"]
                 [commons-net/commons-net "3.5"]
                 ;;journal storer, esfs object saver
                 [cc.qbits/spandex "0.5.2"]
                 ;;conf template (dns conf generation)
                 [luhhujbb/clostache "1.5.0"]]
  :main ^:skip-aot com.linkfluence.starter
  :target-path "target/%s"
  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]
  :profiles {:uberjar {:aot :all}})
