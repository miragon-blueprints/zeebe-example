# 0006 — Fixed ports for v1, portless as the upgrade path

- **Status:** Accepted
- **Date:** 2026-08-18

## Context

This repo is developed with [Conductor](https://conductor.build), which runs each task in its own git
worktree — potentially several at once. Parallel worktrees that all bind the same ports collide. Two
answers exist: **fixed ports + serialised runs**, or **portless** (stable per-worktree `.localhost`
URLs that avoid collisions). Portless is the nicer end state, but it only wraps what it can slug — a JS
dev server — and this headless stack has no JS dev server at all; every collision source is a backend
process that wants a fixed port.

## Decision

For v1 we use **fixed ports and serialise the runs.** `.conductor/settings.toml` sets
**`run_mode = "nonconcurrent"`**, so only one worktree runs the app and its dev stack at a time and the
ports never clash. The port set (also published in `AGENTS.md` — see
[ADR-0005](0005-agents-md-as-the-single-source.md)) is:

| Component                              | Port         |
| -------------------------------------- | ------------ |
| Spring Boot app                        | 8081         |
| Zeebe (gRPC / REST)                    | 26500 / 8080 |
| Operate / Tasklist (Camunda 8 web apps)| 8080         |
| Postgres (app read-model)              | 5432         |

The app runs on **8081** because the Camunda 8 web apps hold **8080**.

**Portless is deferred, deliberately.** Portless only slugs a JS dev server, and this stack has none —
so it wraps **none** of the collision sources:

| Collision source                        | Port         | Wrapped by portless? |
| --------------------------------------- | ------------ | -------------------- |
| Spring Boot app                         | 8081         | ❌ no |
| Camunda 8 dev stack (Zeebe/Operate/Tasklist/Elasticsearch) | 26500 / 8080 | ❌ no |
| Postgres                                | 5432         | ❌ no |

Every collision here is a backend process or a container in `stack/docker-compose.yml`, **outside what
portless wraps.** Adopting it now would buy nothing and still force `nonconcurrent`, so it is added
complexity for zero coverage until the backend/DB/stack isolation story is solved.

## Consequences

- **Positive:** dead-simple, predictable URLs; the same ports in dev, tests, CI, and the docs; no
  slug/proxy layer to reason about.
- **Negative / trade-offs:** only one worktree can run the app at once (`nonconcurrent`); truly parallel
  end-to-end runs across worktrees are not possible in v1.
- **Neutral:** per-worktree isolation of the app, Postgres, and the Camunda 8 dev stack is the recorded
  upgrade path — a future ADR would supersede this one once all collision sources are covered.
