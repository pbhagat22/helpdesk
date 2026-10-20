# AWS-managed policies, looked up by name rather than by their opaque IDs
data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

# HTTPS front door (https://d1fpqs2kiuwucp.cloudfront.net). CloudFront terminates TLS with its own
# *.cloudfront.net certificate and forwards to the ALB over HTTP, caching nothing.
resource "aws_cloudfront_distribution" "app" {
  comment         = "helpdesk: HTTPS in front of the ALB"
  enabled         = true
  price_class     = "PriceClass_100" # edge locations in North America and Europe only (cheapest)
  http_version    = "http2and3"
  is_ipv6_enabled = true

  origin {
    origin_id = "alb"
    # The last ALB's DNS name. The ALB is recreated each session with a new name; step 8.3 will
    # point this at the ALB resource instead of a fixed string.
    domain_name = "helpdesk-413068722.us-east-2.elb.amazonaws.com"
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id         = "alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "PATCH", "POST", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

output "app_url" {
  value = "https://${aws_cloudfront_distribution.app.domain_name}"
}
