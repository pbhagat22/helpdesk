# Credentials are not configured here: locally they come from AWS_PROFILE=helpdesk, in CI (later)
# from the OIDC role. Hard-coding a profile would break the second case.
provider "aws" {
  region = "us-east-2" # the organization's SCP denies every other region

  # A guardrail: refuse to plan or apply if the credentials belong to any other account.
  allowed_account_ids = ["936303390305"]

  # Added to every resource that supports tags, so the console and the bill show what Terraform owns.
  default_tags {
    tags = {
      project    = "helpdesk"
      managed-by = "terraform"
    }
  }
}
