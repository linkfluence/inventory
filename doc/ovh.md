# OVH

    path : /ovh

## utils

    path : /ovh/server-id-with-ns/:short-ns
    method : GET

Return the completed serviceName of the server (ie: nsxxxxxx.ip-xx-xx-xx.eu) with only nsxxxxxx

## Server list

    path : /ovh/server
    method : GET

Return ovh servers list

## Server details

    path : /ovh/server/:id
    method : GET


Return details of ovh server with id :id

## Server re-installation

    path : /ovh/server/:id/install
    method : POST

Send order to reinstall server identified by :id

## Server reboot

    path : /ovh/server/:id/reboot
    method : POST

Send order to reboot server identified by :id

## Server reverse

    path : /ovh/server/:id/reverse
    method : POST
    params : reverse (String)

Update server reverse dns, operation will fail silently if dns record can't be resolve.

## Server installation end

    path : /ovh/server/:id/install/finish
    method : GET

Mark server with id :id as installed so it can be provisionned

## Cloud Instance list

    path : /ovh/cloud/instance
    method : GET

## CLoud Instance details

    path : /ovh/cloud/instance/:id
    method : GET

## Cloud Instance Bootstrap

    path : /ovh/cloud/instance/:id/bootstrap
    method : POST

Re-bootstrap an instance if it was not read on first bootstraping

## Reboot an instance

    path : /ovh/cloud/instance/:id/reboot
    method : POST

Reboot an instance
