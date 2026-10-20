# AWS deployment (Stage 7, built by hand)

Account "Try Catch Finally" (936303390305), region **us-east-2**. The organization's SCP allows only
that region. The CLI profile is `helpdesk` (IAM user `pbuser1`, access key). Run everything from the repo root.

- `env.sh`: `source` it to look up every resource ID by name (a missing one shows as `None`).
- `iam/`: the ECS task execution role's trust policy and its SSM read policy.
- `ecs/`: task definitions (the ECS equivalent of the services in `compose.yaml`).
- `cloudfront/`: the distribution config used to create the HTTPS front door (only needed again if it's deleted).

**The app's address is https://d1fpqs2kiuwucp.cloudfront.net** (CloudFront distribution `EIXMZUF70ITNT`).
CloudFront terminates HTTPS with its `*.cloudfront.net` certificate and forwards to the ALB over HTTP, with
caching off (`CachingDisabled`) and all headers except `Host` passed through (`AllViewerExceptHostHeader`).
The ALB's security group only accepts CloudFront's managed prefix list (`pl-b6a144df`), so the ALB address
alone doesn't answer. Between sessions, the distribution returns 502 because its ALB is gone.

## What stays between sessions (free or nearly free)

ECR image, IAM role `helpdesk-ecs-execution`, SSM SecureStrings `/helpdesk/{db-password,jwt-secret,admin-password}`,
security groups `helpdesk-{alb,app,db,infra}`, DB subnet group `helpdesk-db`, ECS cluster `helpdesk`,
task definitions, log group `/ecs/helpdesk` (1-day retention), and the CloudFront distribution (free tier).

## Bring it up (~15 minutes, mostly waiting for RDS)

```bash
source aws/env.sh

# 1. Database (same endpoint every time: helpdesk-db.cpow8k2qs3rt.us-east-2.rds.amazonaws.com)
aws rds create-db-instance --db-instance-identifier helpdesk-db \
  --engine postgres --engine-version 18.6 --db-instance-class db.t4g.micro \
  --allocated-storage 20 --storage-type gp3 --storage-encrypted \
  --db-name helpdesk --master-username helpdesk \
  --master-user-password "$(aws ssm get-parameter --name /helpdesk/db-password --with-decryption --query Parameter.Value --output text)" \
  --db-subnet-group-name helpdesk-db --vpc-security-group-ids $DB_SG \
  --no-publicly-accessible --no-multi-az --backup-retention-period 0 \
  --monitoring-interval 0 --no-enable-performance-insights \
  --tags Key=project,Value=helpdesk --query DBInstance.DBInstanceStatus --output text

# 2. Private DNS (helpdesk.local), then its two names
aws servicediscovery create-private-dns-namespace --name helpdesk.local --vpc $VPC --query OperationId --output text
sleep 60 && source aws/env.sh    # NAMESPACE_ID must not be None
for s in kafka elasticsearch; do
  aws servicediscovery create-service --name $s --namespace-id $NAMESPACE_ID \
    --dns-config 'RoutingPolicy=MULTIVALUE,DnsRecords=[{Type=A,TTL=10}]' \
    --health-check-custom-config FailureThreshold=1 --query Service.Arn --output text
done
sd() { aws servicediscovery list-services --query "Services[?Name=='$1'].Arn | [0]" --output text; }

# 3. Kafka and Elasticsearch
for s in kafka elasticsearch; do
  aws ecs create-service --cluster helpdesk --service-name $s --task-definition helpdesk-$s \
    --desired-count 1 --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$INFRA_SG],assignPublicIp=ENABLED}" \
    --service-registries registryArn=$(sd $s) --query 'service.[serviceName,status]' --output text
done

# 4. Load balancer
ALB_ARN=$(aws elbv2 create-load-balancer --name helpdesk --type application --scheme internet-facing \
  --subnets $(echo $SUBNETS | tr ',' ' ') --security-groups $ALB_SG --query 'LoadBalancers[0].LoadBalancerArn' --output text)
TG_ARN=$(aws elbv2 create-target-group --name helpdesk-app --protocol HTTP --port 8080 --vpc-id $VPC \
  --target-type ip --health-check-path /actuator/health --health-check-interval-seconds 15 \
  --healthy-threshold-count 2 --query 'TargetGroups[0].TargetGroupArn' --output text)
aws elbv2 modify-target-group-attributes --target-group-arn $TG_ARN \
  --attributes Key=deregistration_delay.timeout_seconds,Value=30 --query 'Attributes[0].Value' --output text
aws elbv2 create-listener --load-balancer-arn $ALB_ARN --protocol HTTP --port 80 \
  --default-actions Type=forward,TargetGroupArn=$TG_ARN --query 'Listeners[0].Port' --output text

# 5. App: wait until RDS is available first, or the app fails its first start (ECS would retry it)
aws rds wait db-instance-available --db-instance-identifier helpdesk-db
aws ecs create-service --cluster helpdesk --service-name app --task-definition helpdesk-app \
  --desired-count 1 --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$APP_SG],assignPublicIp=ENABLED}" \
  --load-balancers targetGroupArn=$TG_ARN,containerName=app,containerPort=8080 \
  --health-check-grace-period-seconds 300 --query 'service.[serviceName,status]' --output text

# 6. Point CloudFront at the new ALB (its DNS name changes every time the ALB is recreated)
ALB_DNS=$(aws elbv2 describe-load-balancers --names helpdesk --query 'LoadBalancers[0].DNSName' --output text)
aws cloudfront get-distribution-config --id $CF_ID > /tmp/helpdesk-cf.json
jq --arg d "$ALB_DNS" '.DistributionConfig | .Origins.Items[0].DomainName = $d' /tmp/helpdesk-cf.json > /tmp/helpdesk-cf-new.json
aws cloudfront update-distribution --id $CF_ID --if-match $(jq -r .ETag /tmp/helpdesk-cf.json) \
  --distribution-config file:///tmp/helpdesk-cf-new.json --query 'Distribution.Status' --output text
aws cloudfront wait distribution-deployed --id $CF_ID && echo "https://$(aws cloudfront get-distribution --id $CF_ID --query Distribution.DomainName --output text)"
```

`update-distribution` replaces the whole config, so it is read, edited, and sent back with the ETag
(`--if-match`). The ETag makes the update fail if someone else changed the distribution in between. Admin login is `admin@example.com`, with the password from
`aws ssm get-parameter --name /helpdesk/admin-password --with-decryption --query Parameter.Value --output text`.
The database starts empty each time.

A new image is pushed under a new tag (see CLAUDE.md). Point `ecs/app.json` at the tag, then re-register it with
`aws ecs register-task-definition --cli-input-json file://aws/ecs/app.json`, which creates the next revision.
Services created afterwards use the latest revision.

## Tear it down (~10 minutes, at the end of every session)

```bash
source aws/env.sh
for s in app kafka elasticsearch; do
  aws ecs delete-service --cluster helpdesk --service $s --force --query service.status --output text
done
aws elbv2 delete-load-balancer --load-balancer-arn $ALB_ARN
aws rds delete-db-instance --db-instance-identifier helpdesk-db --skip-final-snapshot --delete-automated-backups \
  --query DBInstance.DBInstanceStatus --output text

aws ecs wait services-inactive --cluster helpdesk --services app kafka elasticsearch
aws elbv2 wait load-balancers-deleted --load-balancer-arns $ALB_ARN
aws elbv2 delete-target-group --target-group-arn $TG_ARN
for s in kafka elasticsearch; do   # if "instances still registered": wait 30s and repeat
  aws servicediscovery delete-service --id $(aws servicediscovery list-services --query "Services[?Name=='$s'].Id | [0]" --output text)
done
aws servicediscovery delete-namespace --id $NAMESPACE_ID --query OperationId --output text
```

Deleting the namespace within 12 hours of creating it avoids Route 53's $0.50/month hosted-zone charge.
