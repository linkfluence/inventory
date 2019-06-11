# LSW

This API follow ovh ones with some leaseweb specifics things

    path : /lsw

## Enable/Disable Installation

### Enable server installation

    path : /lsw/enable-server-install
    method : POST

Enable server installation when new server are detected

### Disable server installation

    path : /lsw/disable-server-install
    method : POST

Disable server installation when new server are detected

## Monitored Install

### Get current installation state

    path : /lsw/install-state
    method : GET

Return a dictionary containing servers where an installation process is pending

### Clean monitored installation

    path : /lsw/install-state/clean
    method : POST

Clean all installation pending

### Clean monitored installation of a specific server

    path : /lsw/install-state/clean/:id
    method : POST

Remove installation pending for server identified by id

## Server

### Server list

    path : /lsw/server
    method : GET

Return lsw servers list

### Server reference list

    path : /lsw/server-with-ref
    method : GET

Return lsw servers reference list


### Server details

    path : /lsw/server/:id
    method : GET

Return details of lsw server with id :id

### Server details with reference

    path : /lsw/server-with-ref/:ref
    method : GET

Return details of lsw server with reference :ref

### Server Jobs on leaseweb side

    path : /lsw/server/:id/jobs
    method : GET

Return list of jobs of server see: [Leaseweb docs](http://developer.leaseweb.com/api-docs/baremetal_v2.html#get-servers12345jobs)

### Server Job details on leaseweb side

    path : /lsw/server/:id/job/:jobid
    method : GET

Return job details including job steps, see : [Leaseweb docs](http://developer.leaseweb.com/api-docs/baremetal_v2.html#get-servers12345jobs3a867358-5b4b-44ee-88ac-4274603ef641)

### Server installation launch

    path : /lsw/server/:id/install
    method : POST

Send order to reinstall server identified by :id

### Server installation state

    path : /lsw/server/:id/install
    method : GET

Return details of current installation of lsw server with id :id

### Server reboot launch

    path : /lsw/server/:id/reboot
    method : POST

Send order to reboot server identified by :id

## Partition Schema

### Partition Schema list

    path : /lsw/pschema
    method : GET

Return lsw partition schema list

### Partition Schema details

    path : /lsw/pschema/:id
    method : GET

Return lsw partition schema identified with id :id
