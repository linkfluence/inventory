# OVH-CLOUD

    path : /ovh/cloud

## List all ovh cloud instances

    path : /ovh/cloud/instance
    method : GET

Return ovh instances list

## Describe an ovh cloud instance

    path : /ovh/cloud/instance/:id
    method : GET

Return ovh instance description specified with id

## Rename an ovh cloud instance

    path : /ovh/cloud/instance/:id
    method : PUT
    params: name (String)

Update an ovh instance name

## Bootstrap ovh-cloud instance

    path : /ovh/cloud/instance/:id/bootstrap
    method : POST

Bootstrap an instance at ovh-cloud (ie private iface configuration)

## Reboot ovh-cloud instance

    path : /ovh/cloud/instance/:id/reboot
    method : POST

Reboot an instance at ovh-cloud
