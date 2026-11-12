# 0009. Region eu-north-1, EU cross-Region inference, tagging

- Status: Accepted
- Date: 2026-11-10

## Context

The plan (§8.1) assumed Frankfurt (`eu-central-1`). The AWS project for Triode was instead created with the new AWS sign-up (project-based, Paid plan). There, AWS assigns one **selected Region** from the contact address, and a managed service control policy (SCP) denies almost every regional action outside it. For this project that Region is **Stockholm (`eu-north-1`)** and it cannot be changed. The same SCP allows a short list of actions in `us-east-1` (global services) and allows `bedrock:InvokeModel*` in every Region, so cross-Region inference profiles work.

Every service Triode needs is available on the plan and in `eu-north-1`: ECS Fargate, ECR, ALB, RDS PostgreSQL, SQS, Secrets Manager, Cognito, CloudFront, S3 and Bedrock (checked 2026-11-10). None of them requires activating advanced features. The plan's multi-Region features (cross-Region replication for S3, RDS or secrets, multi-Region KMS keys, Route 53 latency or failover routing, CloudFormation StackSets) are not available, and Triode does not use them.

## Decision

1. **All regional resources live in `eu-north-1`**: VPC, ECS, ECR, ALB, RDS, SQS, Secrets Manager, Cognito, S3 and the regional CloudWatch log groups. Terraform's default provider uses `eu-north-1`.
2. **Bedrock calls use EU cross-Region inference profiles (`eu.` prefix)** for Claude models, for example `eu.anthropic.claude-haiku-5-5` for classification. The call is made in `eu-north-1`. Bedrock may run it in eu-central-1, eu-north-1, eu-south-1, eu-south-2, eu-west-1 or eu-west-3, so ticket text stays inside the EU. Global profiles (`global.`) are rejected because they can route to any commercial Region. Embeddings use **Titan Text Embeddings V2** (`amazon.titan-embed-text-v2:0`), which runs in `eu-north-1` itself. Model IDs live in configuration, not in code (see ADR 0008).
3. **`us-east-1` is used only for what has to live there**, through a separate Terraform provider alias:
   - the ACM certificate for the CloudFront distribution (CloudFront only accepts certificates from `us-east-1`);
   - the CloudFront distribution itself (a global service whose API is in `us-east-1`);
   - a WAF web ACL with `CLOUDFRONT` scope, if we add one;
   - CloudWatch Logs for CloudFront or WAF logging, if we turn it on.

   No other resources go to `us-east-1` (or any other Region): no Lambda, no API Gateway, no Lambda@Edge (not supported on this plan).
4. **Tagging convention**: every resource gets `project=triode` and `env=staging` or `env=prod`. Terraform sets both through `default_tags` on every provider, including the `us-east-1` alias. Both keys are activated as cost allocation tags in Billing and Cost Management, so Cost Explorer can split costs by environment.

## Consequences

- Latency from Central Europe to Stockholm is fine for a portfolio demo. The cost estimates in the plan (§8) were made for Frankfurt and should be rechecked with `eu-north-1` prices before the first `terraform apply`.
- Claude calls can cross EU Region borders but never leave the EU. CloudTrail logs each call in `eu-north-1`.
- When we take an inventory of resources or check costs, we look in two Regions: `eu-north-1`, plus `us-east-1` for CloudFront, ACM, WAF and their logs.
- Bedrock usage cannot be split by our tags directly. If per-environment model costs matter, we can tag application inference profiles later.
- **When the project nears its spend limit, AWS applies an SCP that denies `bedrock:InvokeModel`**, along with creating ECS services, RDS instances, SQS queues, secrets and more. The worker must treat Bedrock `AccessDeniedException` as a non-retryable failure (dead-letter queue, clear log message), not as something to retry in a loop.
- Bedrock model access is per account: Titan needs to be authorized once, and Claude needs Anthropic's first-time-use form submitted once, before the first call.
