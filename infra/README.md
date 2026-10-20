# Infrastructure as code (Stage 8, Terraform)

Terraform describes the AWS setup that [aws/README.md](../aws/README.md) built by hand. Run every
command from this folder with the `helpdesk` profile:

```bash
cd infra
export AWS_PROFILE=helpdesk
terraform init      # once per clone, and after changing providers or the backend
terraform plan      # show what would change; changes nothing
terraform apply     # show the plan again, then make the changes after you type "yes"
```

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
