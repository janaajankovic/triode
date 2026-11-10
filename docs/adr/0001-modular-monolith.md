# 0001. Modular monolith with Spring Modulith

- Status: Accepted
- Date: 2026-11-10

## Context

Triode is built by one developer, part-time, over about 12 weeks, on a small AWS budget. It has distinct areas (ticket intake, the triage agent, the knowledge base, human review, external integrations) and two runtime roles: an `api` that must answer in milliseconds and a `worker` that runs slow LLM calls from a queue. The areas need clear boundaries so the code stays understandable as it grows, but they share one database and are released together.

## Decision

Build one Spring Boot application, split into modules by feature: `tickets`, `triage`, `knowledge`, `review`, `integrations`, `shared`. Each module has an `api` package (the only part other modules may use), a framework-free `domain` package and an `infra` package. Spring Modulith verifies these boundaries in a test (`ModularityTests`), so the build fails if a module reaches into another module's internals or modules depend on each other in a cycle. Modules communicate through `api` interfaces and application events. The same image runs as `api` or `worker` depending on a Spring profile, deployed as two ECS services.

Rejected alternative: **microservices.** They would add network calls, distributed transactions, a deployment pipeline and infrastructure per service, and higher AWS cost, without adding anything for one developer and one team. A plain monolith without enforced boundaries was also rejected: its boundaries erode quietly.

## Consequences

- One build, one artifact, one database: simple local setup, simple CI, low cost.
- Boundaries are checked on every build instead of relying on discipline.
- Modules talk through events and `api` types, so a module could be extracted into a service later if a real need appears (for example, scaling the worker separately is already possible through the two services).
- Sharing one database requires discipline: each table belongs to one module, and other modules do not query it directly.
- Spring Modulith is one more tool to learn; its event publication registry (`event_publication` table) needs a Flyway migration.
