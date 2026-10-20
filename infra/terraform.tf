# Which Terraform and provider versions this code needs, and where its state lives.
terraform {
  # 1.10+ for S3's native state locking (use_lockfile), which replaces the old DynamoDB lock table
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0" # any 6.x; `terraform init -upgrade` moves within it, .terraform.lock.hcl pins the exact one
    }
  }

  # State lives in S3, not on this laptop, so CI can use it later and losing the laptop loses nothing.
  # The bucket was created by hand (see infra/README.md): Terraform can't store its state in a bucket
  # it hasn't created yet. Backend settings can't use variables, so the names are written out here.
  backend "s3" {
    bucket       = "helpdesk-tfstate-936303390305"
    key          = "helpdesk/terraform.tfstate"
    region       = "us-east-2"
    use_lockfile = true # writes terraform.tfstate.tflock next to the state while a plan/apply runs
    encrypt      = true
  }
}
