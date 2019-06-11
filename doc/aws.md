# AWS

    path : /aws

## EC2

### EC2 Instance list

    path : /aws/ec2
    method : GET

Return aws instances list

### EC2 Instance list filtered by tag

    path : /aws/ec2
    method : POST
    params : tags (ie: :tags [tag tag tag])

tags is an array of tags, a tag has the following structure:

    {
        :name "tags_name"
        :value "tags_value"
    }

Return aws instances list

### Instance details

    path : /aws/ec2/:id
    method : GET

Return details of aws instance with id :id

### EC2 Instance tags List

    path : /aws/tag/ec2
    mathod : GET

Return all tags key extracted from Instance

### Instance tag Value

    path : /aws/tag/ec2/:tag
    method : GET

Return all configured value in instance for tag :tag

### Instance AMI stats

    path : /aws/stats/ec2/ami
    method : GET, POST
    params : tags (see instance list filtered by tags)

Return instance ami stats (ie instance ami repartition)

### Instance type stats

    path : /aws/stats/ec2/type
    method : GET, POST
    params : tags (see instance list filtered by tags)

Return instance type stats (ie instance type repartition)

### Instance state stats

    path : /aws/stats/ec2/state
    method : GET

Return instance state stats (ie instance state repartition)

### Instance EC2 az stats

    path : /aws/stats/ec2/az
    method : GET

Return instance az stats (ie instance az repartition)

### Instance subnet stats

    path : /aws/stats/ec2/subnet
    method : GET

Return instance subnet stats (ie instance subnet repartition)

## Autoscaling Group (asg)

### ASG list

    path : /aws/asg
    method : GET

Return aws asg list

### ASG list filtered by tag

    path : /aws/asg
    method : POST
    params : tags (ie: :tags [tag tag tag])

tags is an array of tags, a tag has the following structure:

    {
        :name "tags_name"
        :value "tags_value"
    }

Return aws asg list

### ASG details

    path : /aws/ec2/:asg
    method : GET

Return details of aws instance with id :asg

### ASG tags List

    path : /aws/tag/asg
    mathod : GET

Return all tags key extracted from ASG

### ASG tag Value

    path : /aws/tag/asg/:tag
    method : GET

Return all configured value in ASG for tag :tag

## RDS (Relational Database Services)

### RDS list

    path : /aws/rds
    method : GET

Return aws rds list

### RDS list filtered by tag

    path : /aws/rds
    method : POST
    params : tags (ie: :tags [tag tag tag])

tags is an array of tags, a tag has the following structure:

    {
        :name "tags_name"
        :value "tags_value"
    }

Return aws rds list

### RDS details

    path : /aws/rds/:rds
    method : GET

Return details of aws instance with id :rds

## Elasticache (Redis/memcached)

### Elasticache cluster list

    path : /aws/elasticache
    method : GET

### Elasticache get cluster details

    path : /aws/elasticache/:cluster_id
    method : GET

Return details of aws cluster id
