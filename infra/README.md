# AWS infrastructure (Terraform)

Everything the helpdesk runs on in AWS, as code. It was built by hand with the AWS CLI in Stage 7,
then imported into Terraform in Stage 8 (see git history for the hand-built version).

Account "Try Catch Finally" (936303390305), region **us-east-2**. The organization's SCP denies every
other region. Run every command from this folder with the CLI profile `helpdesk` (IAM user `pbuser1`,
access key):

```bash
cd infra
export AWS_PROFILE=helpdesk
terraform init                     # once per clone, and after changing providers or the backend
terraform plan -var running=false  # show what would change; changes nothing
terraform apply -var running=false # show the plan again, then make the changes after you type "yes"
```

**The app's address is https://d1fpqs2kiuwucp.cloudfront.net** (`terraform output app_url`).

## Bring it up and tear it down

Hourly-billed resources (RDS, Cloud Map namespace, the three ECS services, the ALB) only exist while
the variable `running` is true. It has no default, so every plan and apply must say which state it
means (one without `-var running=...` stops and asks):

```bash
terraform apply -var running=true    # bring up: ~10 minutes, mostly RDS; the app answers a few minutes later
terraform apply -var running=false   # tear down, at the end of EVERY session
```

During a session, use `running=true` for every plan/apply; `running=false` would show the session's
resources being destroyed.

The database starts empty each time. Admin login is `admin@example.com`, with the password from
`aws ssm get-parameter --name /helpdesk/admin-password --with-decryption --query Parameter.Value --output text`.
Kafka and Elasticsearch data doesn't survive either; rebuild search with `POST /api/admin/search/reindex`.
Deleting the namespace within 12 hours of creating it avoids Route 53's $0.50/month hosted-zone charge.

What stays between sessions (free or nearly free): the ECR repository and images, IAM role, security
groups, DB subnet group, ECS cluster, task definitions, log group (1-day retention), the CloudFront
distribution (its origin points at a placeholder `.invalid` name, so it answers 502), and the state
bucket. **Never run `terraform destroy`**: it would also delete those (ECR refuses, thanks to
`prevent_destroy`, which stops the whole destroy).

## Files

| File | What |
|---|---|
| `terraform.tf`, `providers.tf` | Versions, the S3 state backend, region, account guardrail, default tags |
| `variables.tf` | `running` |
| `data.tf` | Read-only lookups: account, default VPC and subnets, CloudFront's IP prefix list |
| `ecr.tf`, `iam.tf`, `network.tf`, `ecs.tf`, `cloudfront.tf` | Permanent resources |
| `github.tf` | GitHub OIDC provider and the deploy role CI assumes |
| `database.tf`, `services.tf`, `alb.tf` | Resources that only exist while `running` |

The secrets are SSM SecureStrings `/helpdesk/{db-password,jwt-secret,admin-password}`, created by hand
and deliberately not managed here, so their values never land in the state. ECS reads them into the
app's environment through the execution role; RDS gets its password through an ephemeral resource
and the write-only `password_wo` argument.

## Deploying a new image

Normally CI does it: every push to `main` that passes the tests runs the `deploy` job in
[ci.yml](../.github/workflows/ci.yml). It logs in to AWS through GitHub OIDC as the role
`helpdesk-github-deploy` ([github.tf](github.tf); there are no AWS keys in GitHub), builds an ARM64
image, pushes it as `helpdesk:<7-char git sha>`, and, if the app is running, rolls the ECS service
onto it. When the app isn't running, the push is all it does: the app task definition here always
uses the newest image in ECR, so the next `terraform apply -var running=true` starts it.

By hand (e.g. to test an image before pushing to main), push a plain single-arch image. Without the
attestation flags, Docker pushes an image index plus a provenance manifest, which breaks scanning by
tag and the lifecycle count:

```bash
REGISTRY=936303390305.dkr.ecr.us-east-2.amazonaws.com
aws ecr get-login-password | docker login --username AWS --password-stdin $REGISTRY
docker build --pull --platform linux/arm64 --provenance=false --sbom=false -t $REGISTRY/helpdesk:$(git rev-parse --short HEAD) .
docker push $REGISTRY/helpdesk:$(git rev-parse --short HEAD)
```

After a deploy while running, the next plan shows the app task definition being replaced with the
new tag. That only re-registers what's already deployed: the service ignores Terraform's task
definition once created (`ignore_changes`), so Terraform never rolls the app back to an older image.

## State

Terraform's state (which real AWS object each resource block manages) is in the S3 bucket
`helpdesk-tfstate-936303390305`, key `helpdesk/terraform.tfstate`. The bucket has versioning, so an
older state can be restored, and it locks with a `.tflock` file next to the state, so two runs can't
overlap. Never edit the state by hand; use `terraform state list`, `terraform state show <address>`,
and `terraform state rm` / `import` blocks.

The bucket itself is the one thing Terraform doesn't manage (it can't create the bucket its own state
lives in). It was created once with:

```bash
export AWS_PROFILE=helpdesk AWS_REGION=us-east-2
BUCKET=helpdesk-tfstate-936303390305
aws s3api create-bucket --bucket $BUCKET --create-bucket-configuration LocationConstraint=us-east-2
aws s3api put-bucket-versioning --bucket $BUCKET --versioning-configuration Status=Enabled
aws s3api put-bucket-tagging --bucket $BUCKET --tagging 'TagSet=[{Key=project,Value=helpdesk}]'
```

New buckets already block public access and encrypt objects (SSE-S3) by default.
