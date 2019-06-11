# DNS Cleaner

    path : /dns-cleaner

## Show dns-cleaner state of a zone

    path : /dns-cleaner/show/:zone
    method : GET

Return an array of records with a boolean property indicating if the record is to clean in specficied zone :zone

## Clean a zone

    path : /dns-cleaner/clean/:zone
    method : POST

Clean the specified zone :zone (Dangerous so make a preview first using "show" route)
