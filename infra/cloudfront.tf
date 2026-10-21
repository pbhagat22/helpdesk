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

  # Don't wait the few minutes until every edge location has the new origin. The app takes longer
  # than that to start anyway, and teardown shouldn't wait for it.
  wait_for_deployment = false

  origin {
    origin_id = "alb"
    # The ALB gets a new DNS name each time it's created. While it doesn't exist, the origin is a
    # name that can never resolve (.invalid is reserved for that), so CloudFront answers 502.
    domain_name = var.running ? aws_lb.app[0].dns_name : "helpdesk-offline.invalid"
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      # How long CloudFront waits for the app's response (default 30s, 60s is the most without a
      # quota increase). The AI agent can take 10-30s for a question; the ALB's idle timeout is 60s.
      origin_read_timeout = 60
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
