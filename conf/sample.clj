{
 :read-only false ;;default to false, can be overrided in handler subsection
 ;; this section is dedicated to aws handler
 :aws {:regions {
                 :eu-west-1 {
                            :access-key "******"
                            :secret-key "*******"
                            :name "eu-west-1"}
                          }
       :tags-binding {
         :Name "FQDN"
         :env "ENVIRONMENT"
         :app "APP"
         :aws:autoscaling:groupName "autoscaling_groupname"
       }
       :store {:file {:bucket "/etc/inventory"}
               :s3 {:bucket "my_bucket"}
               :key "aws"
       }
       :refresh-period 5}
 :acs {:regions {
          :cn-shanghai {
            :access-key "******"
            :secret-key "*******"
            :name "cn-shanghai"
          }
       }
       :tags-binding {
         :Name "FQDN"
         :env "ENVIRONMENT"
         :app "APP"
       }
       :store {
         :file {:bucket "/etc/inventory"}
         :s3 {:bucket "my_bucket"}
         :key "acs"
       }
       :refresh-period 10}
;;ovh common
 :ovh {:application_key "******"
       :application_secret "******"
       :consumer_key "*******"
       :install-disabled false}
;;ovh dedicated server
 :ovh-server {:default-template "xenial64"
       :default-hard-template "xenial64-raid5"
       :default-partition-scheme "default"
       :soft-partition-schemes {
         :default "default"
         :3disks "softRaid-raid5"
         :4disks "softRaid-raid5"
       }
       :hard-partition-schemes {
          :default "hardwareRaid-raid5"
          :3disks "hardwareRaid-raid5"
          :4disks "hardwareRaid-raid5-4d"}
       :default-ssh-key ""
       :default-post-install-script nil
       :default-vrack "pn-xxxx"
       :refresh-period 3 ; in minutes
       :debug true ;(not proceed to server setup)
       :store {:file {:bucket "/etc/inventory"}
               :s3 {:bucket "my_bucket"}
               :key "ovh"}}
;;ovh public cloud
 :ovh-cloud {:project-id  "********"
             :refresh-period 2
             :post-install-optional-cmd  ["sudo apt-get update"
                                          "sudo apt-get -y install python curl moreutils"]
             :post-install-user "ubuntu"
             :inventory-host "internal_ip"
             :store {:file {:bucket "/etc/inventory"}
                     :s3 {:bucket "my_bucket"}
                     :key "ovh-cloud"}}
 ;;leaseweb
 :lsw {:token "********"
            :default-os "UBUNTU_16_04_64BIT" ;; ubuntu 16.04
            :base-distrib :debian ;;could be :red-hat (used for iface configuration), default to :debian
            :default-partition-scheme "default_pscheme"
            :default-private-net 1234 ;;your private network id
            :refresh-period 5
            :configure-if true
            :inventory-host "internal_ip" ;; should be the same of :host key in :api section
            :store {:file {:bucket "/etc/inventory"}
                    :s3 {:bucket "my_bucket"}
                    :key "lsw"}
            :net {:network "your-lsw-net"
                  :netmask "your-lsw-netmask"
                  :offset "XX.XX.XX.20"
                  ;;indicate if there is a need to configure one if (typically private if)
                  :configure-if true ;; default to true
                  :interface-name ["eth1","eno2","ens2f1"] ;; this can be a string or an array of string default to eth1
                  }
            :post-install-delay 120000
            :post-install-optional-cmd  ["apt-get update"
                                         "apt-get -y install python curl moreutils"]
            :disable-install true
            :public-keys "/home/inventory/.ssh/id_rsa.pub"
       }
;;caller to send ssh/ansible command
 :caller {:journal-path "/var/log"
          :journal-es "http://your_host:9200" ;; when using es to store journal events, reader can alse access to it
          :default-key "/home/inventory/.ssh/id_rsa"
          :default-public-key "/home/inventory/.ssh/id_rsa.pub"
          :name "commands.log"
          :bucket nil
          :ssh-custom-port 9999
          :default-user "inventory"
          :debug false
          :nb-thread 5}
 ;;store conf files
 :store {:order [:s3 :file]
         :s3 {:access-key "******"
              :secret-key "*******"
              :endpoint "s3.eu-west-1.amazonaws.com"
              :default-bucket "linkfluence.inventory"}
         :es {:bulk-size 500
              :host "es-hostname"}
         :file {
           :default-bucket "/etc/inventory"
           }}
;;dns (bind9) rest API
 :dns {:sudo true
       :bind-dir "/etc/bind"
       :store {:file {:bucket "/etc/inventory"}
               :s3 {:bucket "my_bucket"}
               :key "dns"}
       :restart false}
;;dhcp
 :dhcp {:server {:ovh {:host "10.2.0.247"
                       :port 7911
                       :omapi_key "******"
                       :subnet {:start "10.2.0.0"
                       :end "10.2.7.255"}}
                 :lsw {:host "host_lsw"
                       :port 7911
                       :omapi_key "****"
                       :subnet {:start "10.4.0.0"
                       :end "10.4.7.255"}}}}
 :api {:secret "*******" ; secret to secure API (not yet implemented)
       :host "internal_ip"
       :port 8080}
 :prometheus {
  :host "internal_ip"
  :port "8081"
  :tags ["ENV","Name","REGION","TEAM"]
 }
 ;; internal provider to store resource directly hosted by your company
 :internal {:company "Linkfluence"
            :store {:file {:bucket "/etc/inventory"}
                     :s3 {:bucket "my_bucket"}
                     :key "internal"}}
;;main inventory conf
 :inventory {:store {:file {:bucket "/etc/inventory"}
                     :s3 {:bucket "my_bucket"}}
             :master "my_master_inventory_host" ;; only usefull when main inventory is ro, and some handler are not
             ;;views are pre evaluated aggregations, they are stored and rebuild each times inventory is saved
            :views [{:tag "privateIp" :tags ["REGION" "Name"] :name "my-company-tag-view"}
                    {:tags ["REGION" "ENV" "Name"] :name "my-company-resource-view"}]}
 :app {:store {:file {:bucket "/etc/inventory"}
                     :s3 {:bucket "my_bucket"}
                 :key "apps"}
       :lifecycle-tag "FQDN"}
 ;; proviion resource typically baremetal with raw OS
 :provision {:default {:command {:method :ansible-playbook
                       :playbook "/data/playbook/ovh-init.yml"}}
             :ovh-cloud {:command {:method :ansible-playbook
                         :playbook "/data/playbook/ovh-cloud-init.yml"}}} ;;see caller.clj to get commend structure
 :deploy {
   ;;At linkfluence a server deployment has three steps/phase with different scripts/or playbook
   ;; init phase set all envs vars and configure dns
   ;; global/generic phase add stuffs common for all servers (basically monitoring)
   ;; app phase deploy the applicatiion(s) intended to be running on this server/instances/vm
   ;; This can be customized here
   :deploy ["/home/inventory/bin/server-init" "/home/inventory/bin/server-generic" "/home/inventory/bin/server-app"]
   :update ["/home/inventory/bin/server-generic" "/home/inventory/bin/server-app"]
   :global ["/home/inventory/bin/server-generic"]
   :app ["/home/inventory/bin/server-app"]
 }
 :fsync ["slave_server_ip"]
 :master false
}
