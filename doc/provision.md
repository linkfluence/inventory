# Provision

    path : /provision

NB:
* This API is independent from provider so it use main inventory resource id.
* You can defined several provisionning methods in configuration, they can be specified by :type in provision with type route

## Resource (DEPRECATED)

    path : /provision/:id
    method : GET

Send a provision order to a resource with id :id

## Resource

    path : /provision/v1/start/:id
    method : GET

Send a provision order to a resource with id :id with type default

## Resource with provision type

    path : /provision/v1/start/:id/:type
    method : GET

Send a provision order to a resource with id :id and type :type, type is defined in inventory configuration.

## Finish resource provisioning (DEPRECATED)

    path : /provision/finish/:id
    method : GET

Send an update to inventory to mark resource as provisioned


## Finish resource provisioning

    path : /provision/v1/finish/:id
    method : GET

Send an update to inventory to mark resource as provisioned
