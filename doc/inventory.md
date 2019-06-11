# Inventory

    path : /inventory

## Special tags

Tags are used to query specific resources. There are special tags which are usefull:

* group: this tag make the resource belong to the group specified by group value. If a tag is added to the group all resources member are tagged accordingly
* hidden: this tag prevent resource to be render on all resource list route, very usefull for maintenance.
* prevent_from_deletion: this tag prevent resource from being deleted. If the provided handler send a delete event to main inventory, it will be ignored

## Resource

    path :/inventory/resource
    method : GET or POST
    params : (only for post) tags (ie: :tags [tag tag ... tag])

tags is an array of tags, a tag has the following structure:

    {
        :name "tags_name"
        :value "tags_value"
    }

You can also negate a tag to get all resources which don't match a tag:

    {
        :name "tags_name"
        :value "tags_value"
        :not true  
    }

:not field is a boolean

Moreover if you want to match several tag values , your can provide a list of value, like this:

    {
        :name "tags_name"
        :value ["tags_value_1","tag_value_2"]
    }


Return list of resources eventually filtered to match tags specified in "tags" params when using post method.

## Hidden Resources

    path :/inventory/hidden/resource
    method : GET or POST
    params : (only for post) tags (ie: :tags [tag tag ... tag])

tags is an array of tags, a tag has the following structure:

    {
        :name "tags_name"
        :value "tags_value"
    }

For more complete query see documentation of non hidden resources route.

Return list of hidden resources

## Resource Details

    path : /inventory/resource/:id
    method : GET

Return details of resource with id :id

## Delete Resource

    path : /inventory/resource/:id
    method : DELETE

Deletion of resource with id :id

## Hide a Resource

    path : /inventory/hide/resource/:id
    method : POST

Hide a resource with id :id

## Unhide a Resource

    path : /inventory/unhide/resource/:id
    method : POST

Unhide a resource with id :id

## Add tags to a resource

    path : /inventory/resource/:id/addtags
    method : POST
    params : tags (ie: :tags [tag tag ... tag])

tags is an array of tags, a tag has the following structure:

    {
        :name "tags_name"
        :value "tags_value"
        :delete true (optional : indicate tags deletion)
    }

Add tags to a resource

Example :

    curl -XPOST 'http://provisioning.infra.ovh.rtgi.eu/inventory/resource/{{ resource_id }}/addtags' -H "Content-Type: application/json" -d ' {"tags": [{"name" : "FQDN", "value" : "{{ resource_FQDN }}"}]}'

## Group

    path :/inventory/group
    method : GET

Return list of resources

## Group Details

    path : /inventory/group/:id
    method : GET

Return details of group with id :id

## Add tags to a group

    path : /inventory/group/:id/addtags
    method : POST
    params : tags

tags is an array of tags, a tag has the following structure:

    {
        :name "tags_name"
        :value "tags_value"
        :delete true (optional : indicate tags deletion)
    }

Add tags to a group, and recursively to all resource member of this group (ie : which tag group value is :id)

## Delete a group

  path : /inventory/group/:id
  method : DELETE

Delete group identified by :id

## Tag from resource

    path : /inventory/tag/resource
    method : GET

Return an array of distinct tag from all resource

## Tag value from resource

    path : /inventory/tag/resource/:tag
    method : GET or POST (see add tag), for POST request only

Return an array of distinct tag value matching :tag from all resource (Eventually filtered when using POST)

Curl Sample:

    curl -X POST \
        http://inventory_host/inventory/tag/resource/FQDN \
        -H 'Content-Type: application/json' \
        -d '{"tags":[{"name":"env","value":"prod"},{"name":"app","value":"your-app"}]}'

## Tag stats from resource

    path : /inventory/stats/tag/resource/:tag
    method : GET or POST
    params : tags (see add tag), for POST request only

Return a map with of distinct tag value as key matching :tag and resources count associated to value as value (Eventually filtered when using POST)

## Tag values from group

    path : /inventory/tag/group/:tag
    method : GET

Return an array of tag value matching :tag from all resource

## Aggregation by tags

    path : /inventory/agg/tag/resource
    method : POST
    params : tags, an array of tags string.

Return an object which is a collection of bucket of tag tag/value and the resource object at last level a pool of resource

## Aggregation by tags with tag value

    path : /inventory/agg/tag/resource/:tag
    method : POST
    params : tags, an array of tags string.

Return an object which is a collection of bucket of tag tag/value and the tag value of :tag at last level a list of tag value of resource for tag :tag

## Count resource

    path : /inventory/count/resource
    method : GET or POST
    params : tags (see add tag), for POST request only

Count resources which eventually match tags array filter (when using POST)

## Count group

    path : /inventory/count/group
    method : GET or POST
    params : tags (see add tag)

Count groups which eventually match tags array filter (when using POST)

## Create Alias

    path : /inventory/new/alias
    method : POST
    params : tags (see add tag), resource-id (alias is created for this resource), from-resource (tag name to be imported from original resource)

This method create an alias for a resource identified by resource-id copying tags specified into from-resource array.


## List Alias

    path : /inventory/alias
    method : GET

Retrieve List of all alias

## List Alias (filtered)

    path : /inventory/alias
    method : POST
    params : tags

Retrieve List of all alias, filtered with tags arrays spec (see add tag)

## Get Alias details

    path : /inventory/alias/:alias_id
    method : GET

Retrieve alias speciefied by : alias_id

## Add tags to an alias (similar to addtags to a resource)

    path : /inventory/alias/:id/addtags
    method : POST
    params : tags (ie: :tags [tag tag ... tag])

tags is an array of tags, a tag has the following structure:

    {
        "name":"tags_name (string)",
        "value":"tags_value (string)",
        "delete": true (optional : indicate tags deletion)
    }

Add tags to a alias

Example :

    curl -XPOST 'http://provisioning.infra.ovh.rtgi.eu/inventory/alias/{{ resource_id }}/addtags' -H "Content-Type: application/json" -d ' {"tags": [{"name" : "FQDN", "value" : "{{ alias_FQDN }}"}]}'


## Hide a alias

    path : /inventory/hide/alias/:id
    method : POST

Hide a alias with id :id

## Unhide a alias

    path : /inventory/unhide/alias/:id
    method : POST

Unhide a alias with id :id

## Delete an alias

    path : /inventory/alias/:id
    method : DELETE

Delete an alias specified by :id
