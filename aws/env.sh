# Looks up the IDs of the hand-built AWS resources by name, so any terminal can get them back.
# Usage, from the repo root: source aws/env.sh   (safe to re-run; a missing resource shows as None)
export AWS_PROFILE=helpdesk AWS_REGION=us-east-2

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
VPC=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true --query 'Vpcs[0].VpcId' --output text)
# Two AZs only: the ALB needs at least two, and only routes to tasks in AZs it's enabled in
SUBNETS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC Name=default-for-az,Values=true \
  Name=availability-zone,Values=us-east-2a,us-east-2b --query 'Subnets[].SubnetId' --output text | tr '\t' ',')

sg() { aws ec2 describe-security-groups --filters Name=vpc-id,Values=$VPC Name=group-name,Values=$1 \
  --query 'SecurityGroups[0].GroupId' --output text; }
ALB_SG=$(sg helpdesk-alb); APP_SG=$(sg helpdesk-app); DB_SG=$(sg helpdesk-db); INFRA_SG=$(sg helpdesk-infra)

NAMESPACE_ID=$(aws servicediscovery list-namespaces \
  --query "Namespaces[?Name=='helpdesk.local'].Id | [0]" --output text)
ALB_ARN=$(aws elbv2 describe-load-balancers --names helpdesk \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || echo None)
TG_ARN=$(aws elbv2 describe-target-groups --names helpdesk-app \
  --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || echo None)

echo "account=$ACCOUNT_ID vpc=$VPC subnets=$SUBNETS"
echo "sg: alb=$ALB_SG app=$APP_SG db=$DB_SG infra=$INFRA_SG"
echo "namespace=$NAMESPACE_ID"
echo "alb=$ALB_ARN"
echo "tg=$TG_ARN"
