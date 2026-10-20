variable "app_image_tag" {
  description = "Tag (git short sha) of the helpdesk image in ECR that the app task definition runs"
  type        = string
  default     = "e69e7c8"
}

variable "running" {
  description = <<-EOT
    Whether the hourly-billed resources exist: RDS, the private DNS namespace, the three ECS services
    and the ALB. `-var running=true` brings the app up, `-var running=false` tears them down and
    keeps everything else. No default on purpose: every plan/apply must say which one it means, and
    one that doesn't stops and asks.
  EOT
  type        = bool
  nullable    = false
}
