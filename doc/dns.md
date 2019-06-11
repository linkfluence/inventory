# DNS

    path : /dns

## List zone files

    path : /dns/zones
    method : GET

Return list of zone fils in the current server

## zone file existence

    path : /dns/zone/exists/:db
    method : GET

Return state success with code 200, if db exists.
Return state error with code 404, if db doesn't exist

## List records into a db

    path : /dns/records/:db
    method : GET

Return list of record in the db file :db

## Sync records from a db

    path : /dns/records/:db/sync
    method : GET

## Add a record

    path : /dns/record/:db/create
    method : POST
    params : record

A record is an object with following Structure

    {
        :name "bidule"
        :type "A"
        :ttl "300"
        :value "10.2.0.3" (possibly an array)
      }

Add the record to the zone :db

## Add a list of records

    path : /dns/records/:db/create
    method : POST
    params : records

record is an array of record object which have the following Structure

    {
        :name "bidule"
        :type "A"
        :ttl "300"
        :value "10.2.0.3" (possibly an array)
      }

Add the record to the zone :db

## Update a record

    path : /dns/record/:db/update
    method : PUT
    params : record

Record : see add to get structure

## Update a list of records

    path : /dns/records/:db/update
    method : PUT
    params : records

records : see add to get structure records array structure

## Delete a record

    path : /dns/record/:db/delete
    method : DELETE
    params : record

Record : see add to get structure
