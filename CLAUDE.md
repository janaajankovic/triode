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
