# Triode — rules for AI assistants

Triode is an AI-assisted support ticket triage service (portfolio project). Read these before larger changes:

- `docs/plan.md` — project plan: scope, tech stack (§3), architecture and data model (§4), code organisation (§5), pipeline (§7.4)
- `docs/adr/` — architecture decision records; follow them, and propose a new ADR rather than silently deviating

## Stack and structure
- Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring AI 2.0 (later), Maven wrapper in `backend/`.
- Package by feature, never by layer. Modules under `dev.triode`: tickets, triage, knowledge, review, integrations, shared.
- Each module has `api` (what other modules may use: interfaces, events, DTOs as records), `domain` and `infra`.
  Other modules may only use a module's `api` package; `ModularityTests` enforces this.
- `domain` packages are pure Java: no Spring, AWS, Spring AI or JPA (`jakarta.persistence`) imports.
  JPA entities live in `infra` and map to the domain objects. `ArchitectureTests` enforces this.
- Schema changes only via Flyway migrations in `backend/src/main/resources/db/migration`; never edit an applied migration.
- Every LLM call goes through `LlmGateway`. Prompts live in versioned files in `resources/prompts` (e.g. `classify.v1.st`).
- Errors are RFC 9457 problem details from one global exception handler.

## Working rules
- Write or update tests with every change. Before finishing, run from `backend/`: `./mvnw spotless:apply` then `./mvnw verify`
  (needs Docker running: integration tests use Testcontainers).
- Local database: `docker compose up -d` from the repo root.
- Never add secrets, keys or tokens to code or config files. Local defaults in `application.properties` are throwaway values matching `docker-compose.yml`.
- Explain any new library or pattern before using it.
- Commits: Conventional Commits with the Jira key, e.g. `feat(triage): TRI-14 classify tickets with structured output`.
- Branches: `feature/TRI-<n>-short-name`, `fix/...`, `chore/...`; never commit to or push `main` directly.
- Git remote: `git@ssh.bitbucket.org:janajankovic/triode.git` (Bitbucket moved SSH off `bitbucket.org` on 2026-11-12).
- AWS: ADR 0009 and the note at the top of plan §8.1 override the older AWS steps in the plan (Frankfurt, root user, Identity Center).

## CI (Bitbucket Pipelines)
- Free plan: 50 build minutes/month; a cloud run costs about 2 of them. Only pull requests trigger a pipeline (each push to an open PR
  is a run); keep it that way (no `default:`/`branches:` sections) and keep `max-time` on the step.
  Push only after a green local `verify`, and batch commits.
- The PR step has `runs-on: [self.hosted, linux]` (laptop runner, 0 build minutes); the manual custom pipeline
  `test-in-cloud` runs the same step in the cloud when the runner is offline.
- Runner: Docker container `triode-runner` (setup and Docker Desktop fixes in README "CI runner"). Before pushing, check
  `docker ps --filter name=triode-runner`; if it isn't running, ask the user to `docker start triode-runner` (an offline
  runner fails the PR step at once). Never ask for or handle the runner's UUIDs, client ID or secret.
- The Bash tool is Git Bash: prefix `docker run -v /linux/path:…` with `MSYS_NO_PATHCONV=1`, or the path gets rewritten.
- Testcontainers runs against the Pipelines Docker service with Ryuk disabled (`TESTCONTAINERS_RYUK_DISABLED=true`);
  the service has 1 GB of the 4 GB step, the rest is for Maven/Surefire.
- PRs: create them with the Atlassian MCP tool `createBitbucketRepoPullRequest` (workspace `janajankovic`, repo `triode`).
  Bitbucket operations aren't primary tools: find them with `discover`, run them with `executeRead`/`executeWrite`.
  Description sections: What / Why (Jira: TRI-n) / How it was tested / Screenshots (drop if none) /
  Explain-back (heading left empty for the user) / Notes.
- The coordinator review is a PR comment headed "Coordinator review (Claude)"; read it with
  `listBitbucketRepoPullRequestComments`. The user replies to and resolves it.

<!-- BEGIN AWS Agent Toolkit rules -->
# AWS Guidance for the new AWS experience

This user has signed up for the new AWS experience. This experience lets you sign into AWS using a social provider and requires the following additional context.

Where this guidance conflicts with the project's own instructions, the project's instructions take precedence.

## Context

### Terminology:

- Say "project" instead of "account" — a project contains an AWS account and settings for sharing with other collaborators
- Say "team member" instead of "IAM user" — users are invited by email, not created or federated in IAM
- Say "AWS Settings" when referring to management tasks at [settings.aws.com](https://settings.aws.com/) (project management, billing, team members, spend limits). Users view their actual AWS resources in the AWS Management Console.
- Say "selected Region" when referring to the user's Region — not "home Region"
- The user has a managed IAM experience. This includes a managed service control policies (SCP) and resource control policies (RCP) that govern the use of AWS. They will still need to use IAM to create policies to let services work with each other. If there are questions about the SCPs or RCPs, go to the documentation at https://docs.aws.amazon.com/accounts/latest/reference/scps-and-rcps-for-projects.html

### Constraints:

- All projects share a single AWS Region determined by the user's contact address. Resources cannot be created in other Regions
- When developing:
  - MUST create all Regional resources in the project's assigned Region
  - You CAN create AWS WAF and Cloudwatch Logs resources in us-east-1 when there are global resources (like a global WAF instance) that require a connection to dependencies in us-east-1. You should not use these for any other reason, because resources in the selected Region will provide lower cost (due to no cross-Region traffic), increased availability (due to no cross-Region traffic), and easier manageability (due to not needing to look in another Region). When you need to do an inventory of resources, you need to look in both the selected Region and us-east-1 for Cloudwatch Logs or WAF resources.
  - MUST NOT attempt to create Lambda, API Gateway, or other Regional resources in any other Region
  - MUST direct users to confirm their Region in AWS Settings > View all projects > Overview > Additional Info > Region. If the user cannot confirm their Region, check in ~/.aws/config
  - MUST NOT use Lambda@Edge — excluded from both Lambda and CloudFront
  - MUST NOT use CloudFormation StackSets — no multi-account or multi-Region deployments
  - MUST NOT attempt cross-Region actions — no cross-Region replication for DynamoDB/S3/RDS, no multi-Region KMS keys
  - MUST NOT use Route 53 cross-Region routing — geolocation, latency-based, and failover routing policies are not available
  - CloudFront is a global service and its actions ARE allowed in `us-east-1`. A user can create a CloudFront distribution pointing to their project-region Lambda function URL or API Gateway. However, Lambda and API Gateway themselves MUST NOT be created in `us-east-1` — they must be in the project Region.
  - Reduced availability in `eu-north-1` specifically: Amazon Rekognition, Amazon Textract, Amazon Personalize, AWS App Runner are not available in that Region.
- IAM permissions for human access are managed by AWS. Don't assign roles to team members unless absolutely necessary
- The user may have a spend limit if they are on the paid plan. The limit that pauses their project if it's exceeded. If resources suddenly become inaccessible, ask if they have a spend limit configured. Only project owners can modify a spend limit.
- When developing:
  - MUST ask about spend limit status if the user reports sudden "Access Denied" errors on operations that previously worked
  - MUST direct users to check spend status in AWS Settings > Billing
  - MUST check if a user has upgraded their account to the paid plan
  - MUST ask the user if they want to clean up the successfully created resources or keep them to reduce cost
- The user sets up billing, creates spend limits, and retrieves and pays invoices in AWS Settings. The user creates budgets and optimizes their costs in the AWS Billing and Cost Management console
- Not all AWS services are available. If a service isn't working, do the following:
  1. Run the command `aws freetier get-account-plan-state`
  2. If accountPlanType": "FREE", check the [Free Tier supported services list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-free-tier) next,
  3. If accountPlanType": "PAID", check the [Paid Tier supported services list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#supported-services-paid-plan).
  4. If neither list shows the service, check the [Not supported for this experience list](https://docs.aws.amazon.com/accounts/latest/reference/supported-services-sign-up-new.html#unsupported-services). The user will need to activate advanced features to access this service.
- Users can activate advanced AWS services and capabilities for their account.
- For any AWS task or question — building, configuring, migrating, debugging, or operating a service, writing SDK code, authoring CDK/CloudFormation, or answering how a service works — first call `aws___search_documentation` with topic `agent_skills` and load the matching skill with `aws___retrieve_skill`. Do this even when you believe you know the answer: skills carry current service behavior that your training may not. Prefer the skill's guidance over general knowledge. Skip it only for work that does not involve AWS.

### Help level

- help_level (required): LOW, MEDIUM, or HIGH. While a user is building, you MUST ask the user: "How much guidance would you like from me? Low (I only flag security risks), medium (I ask a couple of clarifying questions if something seems off), or high (I explain what I'm doing, suggest alternatives, and flag best practices)."

You CAN update this rule file to save a user's help_level.

help_level: MEDIUM

Constraints for each level:

**LOW:**

- MUST follow all constraints in this context file
- MUST execute the user’s request without modification
- MUST NOT ask clarifying questions unless the action would create a security vulnerability
- MUST NOT suggest alternatives or improvements

**MEDIUM:**

- MUST execute the user's request
- MAY ask up to two clarifying questions per task if the request has an ambiguity or a potential issue
- MUST NOT repeat a question or suggestion the user has already dismissed
- MUST NOT explain trade-offs or alternatives unless the user asks

**HIGH:**

- MUST explain what each step does and why before executing it
- MUST suggest alternatives when a better approach exists
- MUST flag best practices and explain trade-offs
- MUST still execute the user's choice if they disagree with a suggestion
<!-- END AWS Agent Toolkit rules -->
