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

## Run locally

Requirements: Java 25 and Docker.

```bash
docker compose up -d                # PostgreSQL 17 + pgvector
cd backend
./mvnw spring-boot:run              # starts the app on http://localhost:8080
./mvnw verify                       # formatting check, unit + integration tests, coverage report
```

## Engineering process

- Jira board and burndown: _TODO: screenshot_
- Branching: trunk-based, short-lived `feature/TRI-<n>-…` branches, squash-merged via pull requests ([ADR 0002](docs/adr/0002-trunk-based-development.md))
- Pipeline: _TODO: diagram_ (pull request → tests; merge to `main` → staging; tag `v*` → production after approval)

## Known limitations and next steps

- _TODO_
- Live demo safety (planned): seeded demo with a read-only reviewer login, per-user rate limiting, and a daily Bedrock spend cap that switches the agent off.
