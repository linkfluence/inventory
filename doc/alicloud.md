# ACS

    path : /acs

## ECS

### ECS Instance list

    path : /acs/ecs
    method : GET

Return aws instances list

### ECS Instance description

    path : /acs/ecs/:id
    method : GET

Return details concerning ecs instance identifiied by id

### ECS Instance renaming

    path : /acs/ecs/:id
    method : put
    params : name (String)

Set the name of instance identified by id to "name" json property putted
