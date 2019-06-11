# Internal

    path : /internal

## Resource/server list

    path : /internal/resource
    method : GET

Return internal resources list

## Resource/server details

    path : /internal/resource/:id
    method : GET

Return details of internal resource with id :id

## Resource Create

    path : /internal/resource
    method : POST
    params : resource

Create a new internal resource identified

A resource has the following structure:

    {
     :cpuName
     :memTotal
     :privateIp
     :privateMacAddress
     :privateReverse
     :publicIp
     :publicMacAddress
     :publicReverse
     :datacenter (where the serveur is)
     :availabilityZone (short az equivalent)
     :type (vm, container, baremetal)
     :meta (add what you want there)
     :provider (default to :company in internal conf)
    }


## Resource Update

    path : /internal/resource/:id
    method : PUT
    params : privateIp,privateIp,privateReverse,publicIp,publicReverse,memTotal

Send update to an existing resource :id

## Resource Delete

    path : /internal/resource/:id
    method : DELETE

Delete resource identified by :id
