# API

Most of API calls are fully asynchronous, ie an API call submit an operation to an internal queue which will be eventually not executed instantly.

Replication between multiple instances of inventory is also asynchronous. When an operation is executed and queue is empty, changes are committed to remote storage. on complete, an http call is sent to others instances to retrieve the last committed state.

Since this is not an ideal replication system, this protocol may be one day switch to a technology which uses kafka.

NB:

* We use JSON as format to send data to the API.

* Content-type header should be set accordingly : "Content-type: application/json"

Result are send with the following format:

    {
        "state":"success",
        "data":Containt paylod of response,
        "msg":"Message attached to response"
    }

In case of error:

    {
        "state":"error",
        "msg":"Message attached to error response"
    }

HTTP Return code respond either 200 or 202 on success and 404, 403, 500, etc... on error. No surprise here.

## Inventory

Doc for inventory is there : [Inventory](inventory.md)

## AWS

Doc for aws provider features is there : [AWS](aws.md)

## OVH

Doc for ovh provider features is there : [OVH](ovh.md)

## OVH-Cloud

Doc for ovh-cloud provider features is there : [OVH-CLOUD](ovh-cloud.md)

## Lease Web (LSW)

Doc for lsw provider features is there : [LSW](lsw.md)

## Internal

Doc for internal provider features is there : [Internal](internal.md)

## Provisioning

Doc for provisioning routes is there : [provisioning](provision.md)

## Deployment

Doc for deployment routes is there : [Deploy](deploy.md)

## DNS

Doc for dns management routes is there : [DNS](dns.md)

## DNS Cleaner

Doc for dns-cleaning using inventory : [DNS-CLEANER](dns-cleaner.md)

## DHCP

Doc for dhcp management routes is there : [DHCP](dhcp.md)

## APP

Doc for dhcp management routes is there : [APP](app.md)

## AliCloud

Doc for alicloud provider routes is there : [APP](alicloud.md)
