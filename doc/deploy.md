# Deploy

    path : /deploy

## Resource

    path : /deploy/resource/:id
    method : GET

Send a deploy command to a server with id :id

## Finish resource deployment

    path : /deploy/resource/:id/finish
    method : GET

Send an update to inventory to mark resource as deployed

## Group

    path : /deploy/group/:id
    method : GET

Send a deploy command to a group of resource with id :id
