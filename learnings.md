# Learnings

What we learned building praying-mantis, grouped by technology. We're experienced developers new
to this stack, so skip general basics and record what's specific to Vue, Java, Spring Boot,
Postgres and the tooling around them.

Add entries at the demo and reflect step of each feature, and from the AI reviewers' suggestions
(see `CONTRIBUTING.md` §4). One entry per learning:

```
### Short title
What surprised us or cost us time, what we now do instead. Link the PR, story or decision (#N).
```

## Vue

## TypeScript

## Java

## Spring Boot

## Postgres

## Liquibase

## Tooling (Vite, pnpm, Maven, Docker, OpenAPI)

### Pin pnpm with `packageManager` and Corepack
`frontend/package.json` has `"packageManager": "pnpm@12.6.0"`. Corepack ships with Node 22–24
(from Node 25 install it once with `brew install corepack`). After `corepack enable`, typing `pnpm` downloads and runs exactly that version, so everyone (and CI)
uses the same pnpm without a global install. The lockfile is `pnpm-lock.yaml`; a
`package-lock.json` means someone ran `npm install` by mistake (decision #5).
