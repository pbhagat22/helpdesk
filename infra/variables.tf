variable "app_image_tag" {
  description = "Tag (git short sha) of the helpdesk image in ECR that the app task definition runs"
  type        = string
  default     = "e69e7c8"
}
