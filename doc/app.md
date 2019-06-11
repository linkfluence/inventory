# APP

    path : /app

## List APP env

    path : /app/env
    method : GET

Return list of app environment

## Describe a specific env

    path : /app/env/:env
    method : GET

Return description of an env specified by :env

## List APPS of a specific env

    path : /app/env/:env/apps
    method : GET

## Description of an app for an env

    path : /app/env/:env/app/:app
    method : GET

Return a description of an app

## Get app resources

    path : /app/env/:env/app/:app/resources
    method : GET

Return resources for app

## Get app tag of app resources

    path : /app/env/:env/app/:app/resources/tag/:tag
    method : GET

Return specific tag :tag for app :app in env :anv

## Create a new environment

    path : /app/new/env
    method : POST
    params : name (string), description (string)

Create a new environment to store apps

## Tag an environment

    path : /app/env/:env
    method : PUT
    params : app-tags (array of tags), resource-tags (array of tags)

A tag has the following structure :

    {
        "name":"tags_name (string)",
        "value":"tags_value (string)",
        "delete": true (optional : indicate tags deletion)
    }

## Delete environment

    path : /app/env/:env
    method : DELETE

Delete a specific env with all its apps, this operation is irreversible

## Register a new application

    path : /app/new/env/:env/app
    method : POST
    params : name (string), tags (array of tags), resource-tags (array of tags)

A tag has the following structure :

    {
        "name":"tags_name (string)",
        "value":"tags_value (string)",
        "delete": true (optional : indicate tags deletion)
    }

Register an application in env :env

## Update an application

    path : /app/env/:env/app/:app
    method : put
    params : tags (array of tags), resource-tags (array of tags)

A tag has the following structure :

    {
        "name":"tags_name (string)",
        "value":"tags_value (string)",
        "delete": true (optional : indicate tags deletion)
    }

Update tag of an application :app in environment :env

## Delete an application

    path : /app/env/:env/app/:app
    method : DELETE

Delete a single application :app in environment :env

## Application lifecycle

    path : /app/env/:env/app/:app/action/:action
    metho : POST
    
Using systemctl restart a registred application :app on environment :env. Available actions are : start, stop, status, restart and reload
