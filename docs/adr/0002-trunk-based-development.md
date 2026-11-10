# 0002. Trunk-based development

- Status: Accepted
- Date: 2026-11-10

## Context

Triode has one developer, deploys continuously to staging on every merge to `main`, and releases to production by tagging (`v0.N.0` at the end of each sprint). There is only ever one version in production, so there are no parallel release lines to maintain. The Bitbucket free plan includes 50 build minutes a month, so every extra branch merge that triggers a pipeline has a real cost.

## Decision

Use trunk-based development. `main` is always deployable. Every change is a short-lived branch (1–3 days) named after its Jira issue (`feature/TRI-<n>-short-name`, `fix/…`, `chore/…`), merged through a pull request with squash merge so `main` has one commit per story. Releases are tags on `main`, and hotfixes are ordinary `fix/` branches.

Rejected alternative: **Git Flow** (long-lived `develop`, `release/*` and `hotfix/*` branches). It suits products with scheduled releases and several supported versions at once. Here it would add merges between long-lived branches, more merge conflicts and more pipeline runs, without any benefit.

## Consequences

- Small pull requests that are easy to review and quick to integrate; few merge conflicts.
- `main` must stay green: the pull-request pipeline runs the full test suite, and work that is not ready for users is hidden behind configuration or not yet wired in.
- The free Bitbucket plan shows failed checks but does not block merging, so "never merge red" is a personal rule, not one the tool enforces.
- The history of `main` reads as a list of completed stories, each linked to its Jira issue.
