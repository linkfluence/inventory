# DHCP

    path : /dhcp

## Get dhcp list

    path : /dhcp/list
    method : GET

Return an array of dhcp configured

## Get dhcp leases

    path : /dhcp/leases/:id
    method : GET

Return an array of leases of dhcp identified by id

A lease has the following structure :

      {
        :hostname "hostname"
        :macAddress "00:00:00:00:00:00"
        :ipAddress "10.2.0.0"
        :state "Dhcp state"
        :start "DateTime"
        :ends "DateTime"
      }
