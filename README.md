# inventory

Cloud agnostic inventory system currently supporting:

* AWS (ec2, RDS, elasticache) with multi-region
* OVH (baremetal and cloud though ovh API)
* Leaseweb (also called LSW) baremetal API only
* Alicloud Services (also called ACS, ecs service only)
* GCP (Only vm)
* Internal inventory

## Installation

Compile jar and create a conf file like one in conf/sample

## Storage

Inventory are basically stored in flat yaml files so it can be read easily, however for distributed setup the following storage are supported:

* yaml stored into aws s3 / gcp gs / aliyun oss
* yaml stored into local filesystem
* map stored into consul

Storage replication is fully asynchronous inventory aims to retains data in memory to be fast

## Cloud Autorization

### GCP

The following permissions are needed to gcp service account:

* compute.instances.get
* compute.instances.list
* storage.objects.create
* storage.objects.delete
* storage.objects.get
* storage.objects.list

The compute permissions are needed to build the inventory, the storage permissions are needed to store inventory state

## Usage

Launching

    $ java -jar inventory-0.16.10-standalone.jar conf/prod.clj

## Documentation

Documentation is available [here](doc/intro.md)

## General stuffs

Copyright Jean-Baptiste Besselat © 2022 Linkfluence SAS, a Meltwater company.

Many thanks to Adot and Linkfluence. 

OMAPI dhcp by talamso@gmx.net, website <https://talamonso.net/omapi/>, maintained here: <https://github.com/jedi4ever/omapi-dhcp>

OMAPI dhcp has been patched to be compatible with java8 base64 encoder/decoder


Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
