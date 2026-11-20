> This is a mirror of https://bitbucket.org/janajankovic/triode, where development, pull requests, CI pipelines and Jira tracking happen.

# Triode

AI-assisted support ticket triage: Triode classifies incoming tickets, drafts a cited reply from a knowledge base and proposes Jira bugs, and a human approves every action.

- Live demo: _TODO (Sprint 3)_
- 2-minute demo video: _TODO_

> The scenario ("Taskly", a fictional project-management SaaS), its knowledge base and the labeled tickets are synthetic.

## Architecture

_TODO: diagram (plan §4)._ A modular monolith ([ADR 0001](docs/adr/0001-modular-monolith.md)) deployed as two ECS services from one image: the `api` answers users, the `worker` runs the slow AI steps from an SQS queue. Nothing leaves the system until a human approves it.

## Evaluation results

_TODO: one row per release._

| Release | Category accuracy | Retrieval recall | p95 latency | $ per ticket |
| --- | --- | --- | --- | --- |
| — | — | — | — | — |

## Tech stack

- **Application:** Java 25, Spring Boot 4.1, Spring Modulith 2.1, Spring AI 2.0
- **Data:** PostgreSQL 17 + pgvector, Flyway, Spring Data JPA
- **AI:** Amazon Bedrock (Ollama locally)
- **Frontend:** Angular + Angular Material
- **Infrastructure:** AWS ECS Fargate, RDS, SQS, Cognito, Terraform
- **CI/CD:** Bitbucket Pipelines
- **Quality:** JUnit, Testcontainers, Spring Modulith tests, ArchUnit, Spotless, JaCoCo

## Key design decisions

| ADR | Decision |
| --- | --- |
| [0001](docs/adr/0001-modular-monolith.md) | Modular monolith with Spring Modulith |
| [0002](docs/adr/0002-trunk-based-development.md) | Trunk-based development |
| [0010](docs/adr/0010-api-error-model.md) | API error model: domain exception kinds, one RFC 9457 handler |

## Run locally

Requirements: Java 25 and Docker.

```bash
docker compose up -d                # PostgreSQL 17 + pgvector
cd backend
./mvnw spring-boot:run              # starts the app on http://localhost:8080
./mvnw verify                       # formatting check, unit + integration tests, coverage report
```

## Try the API

With the app running, submit a ticket. Until authentication arrives (Sprint 4), the caller is identified by the
temporary `X-Customer-Id` header (any UUID; this one is made up):

```bash
curl -i -X POST http://localhost:8080/api/v1/tickets \
  -H 'Content-Type: application/json' \
  -H 'X-Customer-Id: 3f2b8c1e-0000-4000-8000-000000000001' \
  -d '{"subject": "Cannot log in", "body": "The login page shows an error after I enter my password."}'
```

The answer is `201 Created` with a `Location: /api/v1/tickets/{id}` header and
`{"id": "...", "subject": "...", "body": "...", "status": "NEW", "createdAt": "..."}`.

Fetch it again with the same header (replace `{id}` with the id from the answer):

```bash
curl -i http://localhost:8080/api/v1/tickets/{id} \
  -H 'X-Customer-Id: 3f2b8c1e-0000-4000-8000-000000000001'
```

The answer is `200 OK` with the same JSON. Another customer's ticket and an unknown id both return the same `404`,
so the API does not reveal which ids exist. Responses with ticket content are sent with `Cache-Control: no-store`.
Errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details ([ADR 0010](docs/adr/0010-api-error-model.md)).
The OpenAPI description is at http://localhost:8080/v3/api-docs (Swagger UI: http://localhost:8080/swagger-ui.html).

## Engineering process

- Jira board and burndown: _TODO: screenshot_
- Branching: trunk-based, short-lived `feature/TRI-<n>-…` branches, squash-merged via pull requests ([ADR 0002](docs/adr/0002-trunk-based-development.md))
- Quality gates: [engineering standards](docs/engineering-standards.md) — what the build, the pipeline and the review enforce
- Pipeline: _TODO: diagram_ (pull request → tests; merge to `main` → staging; tag `v*` → production after approval)

## CI runner

Pull-request builds run on a self-hosted Bitbucket runner (Linux Docker, x86_64) on the maintainer's laptop, so they use no build minutes from the Free plan's 50. The steps are the same as in the cloud, each with a `max-time` and an image pinned by version and digest: secret scan (gitleaks), build and test (Temurin JDK 25, a Docker service for Testcontainers with Ryuk disabled), dependency scan (Trivy). See [engineering standards](docs/engineering-standards.md).

**Register once:** Repository settings → Runners → Add runner → System and architecture *Linux Docker (x86_64)*, labels `self.hosted` and `linux`. The dialog shows a `docker container run` command with your UUIDs and OAuth credentials.

**Start it:** open a **separate PowerShell window** just for this. Never paste the dialog's values into a file, a chat or a command line (PowerShell keeps command history in a plain-text file). Enter each one at a `Read-Host` prompt instead, which is not saved. Copy the values exactly as the dialog shows them; the UUIDs already include their `{ }`:

```powershell
$env:ACCOUNT_UUID        = Read-Host "ACCOUNT_UUID"
$env:REPOSITORY_UUID     = Read-Host "REPOSITORY_UUID"
$env:RUNNER_UUID         = Read-Host "RUNNER_UUID"
$env:OAUTH_CLIENT_ID     = Read-Host "OAUTH_CLIENT_ID"
$env:OAUTH_CLIENT_SECRET = Read-Host "OAUTH_CLIENT_SECRET"
# "-e NAME" without a value passes the variable from this window's environment.
# If the dialog's image or other flags differ from this template, follow the dialog.
docker container run -d --name triode-runner `
  -v /var/lib/bbrunner:/var/lib/bbrunner `
  -v /var/run/docker.sock:/var/run/docker.sock `
  -v /var/lib/docker/containers:/var/lib/docker/containers:ro `
  -e ACCOUNT_UUID `
  -e REPOSITORY_UUID `
  -e RUNNER_UUID `
  -e OAUTH_CLIENT_ID `
  -e OAUTH_CLIENT_SECRET `
  -e RUNTIME_PREREQUISITES_ENABLED=true `
  -e WORKING_DIRECTORY=/var/lib/bbrunner `
  docker-public.packages.atlassian.com/sox/atlassian/bitbucket-pipelines-runner
docker logs -f triode-runner        # wait until the log reports the runner as online, then Ctrl+C
```

Once Repository settings → Runners shows the runner as **Online**, **close that PowerShell window**; the variables disappear with it. The runner keeps running in Docker Desktop.

**Image:** use the image exactly as Atlassian's Add-runner dialog shows it. Currently that is the image without a tag (`latest`). The Runners page shows each runner's version; a red badge next to it means the runner is out of date.

**Upgrading:** the secret is never stored, so you can't re-run the same command. Delete the runner in Repository settings → Runners, remove the old container (`docker rm -f triode-runner`), `docker pull` the image, then register a new runner and start it as above with the new dialog's values.

**Docker Desktop fixes** (Docker Desktop on WSL2 is not officially supported by Atlassian; these two make it work):
1. **Working directory:** mount `/var/lib/bbrunner` and set `WORKING_DIRECTORY=/var/lib/bbrunner`, as above. Do not use `/tmp` or `/var/tmp`: in Docker Desktop's VM both are mounted `noexec`, so the cloned `./mvnw` fails with "Permission denied" and native libraries (e.g. netty's) fail to load. `/var/lib` is on the VM's ext4 disk without `noexec`. Check with `docker run --rm -v /var/lib/bbrunner:/w alpine sh -c "mount | grep ' /w '"`.
2. **Log driver:** Docker Desktop → Settings → Docker Engine, set `"log-driver": "json-file"` in the JSON and click Apply & restart. The runner refuses to start with any other log driver.

If step logs stop appearing in the Bitbucket UI, the fallback is a Windows runner.

**After a reboot:** start Docker Desktop, then `docker start triode-runner`. Stop it with `docker stop triode-runner` when you are done. The container keeps its configuration, so no secret is needed again.

**When the laptop is off:** a step with `runs-on` fails at once if no runner is online (it is not queued). Either start the runner and re-run the step, or run the same steps in the cloud (about 3–4 build minutes): Pipelines → Run pipeline → choose the branch → `test-in-cloud`. The result attaches to the commit, so the pull request shows it. Or run `./mvnw verify` locally and leave the PR build until the runner is back.

**Check that a build used 0 cloud build minutes:**
1. On the pipeline run, open the step: its details should name the runner you registered, not Bitbucket's cloud. Also check `docker logs triode-runner`, which shows the step being picked up.
2. Workspace settings → **Pipelines usage** (workspace admins, monthly plans) lists build-minute consumption per repository and per build for the current billing period; the runner build shows 0 minutes and the workspace total does not change. Atlassian: "Builds that run on self-hosted runners do not count towards build minutes."

Security: the repository is public, but pull requests from forks don't trigger pipelines in Bitbucket, so only people with write access (the maintainer) can run code on the runner.

## Known limitations and next steps

- _TODO_
- Live demo safety (planned): seeded demo with a read-only reviewer login, per-user rate limiting, and a daily Bedrock spend cap that switches the agent off.
