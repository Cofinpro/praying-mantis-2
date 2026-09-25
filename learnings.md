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

### Boot 4 splits features into their own starters
Spring Boot 4 has a starter per technology, and a test starter for each: `spring-boot-starter-liquibase`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`. Snippets written for Boot 3 (plain `liquibase-core`, `spring-boot-starter-test`) still compile but miss the auto-configuration. Check the Boot 4 starter name first. (BE-0.1)

### Security also authorizes the /error forward
Since Spring Security 6 the filter chain also runs on the ERROR dispatch. With `anyRequest().authenticated()`, a 404/500 on a public endpoint reaches an anonymous caller as 401. Permit `DispatcherType.ERROR`. (BE-0.1 review)

## Postgres

## Liquibase

## Tooling (Vite, pnpm, Maven, Docker, OpenAPI)

### Pin pnpm with `packageManager` and Corepack
`frontend/package.json` has `"packageManager": "pnpm@12.6.0"`. Corepack ships with Node 22–24
(from Node 25 install it once with `brew install corepack`). After `corepack enable`, typing `pnpm` downloads and runs exactly that version, so everyone (and CI)
uses the same pnpm without a global install. The lockfile is `pnpm-lock.yaml`; a
`package-lock.json` means someone ran `npm install` by mistake (decision #5).

### pnpm blocks dependency install scripts until you approve them
Since pnpm 10, `postinstall` scripts of dependencies don't run by default, and pnpm 12 fails the
install until each one is approved or denied. The choice lives in `frontend/pnpm-workspace.yaml`
under `allowBuilds` (`pnpm approve-builds` writes it). We deny `vue-demi` (pulled in by
TanStack Query): its script only switches builds for Vue 2, and the shipped build already
targets Vue 3. Check what a script does before approving it: install scripts are a common
supply-chain attack vector (FE-0.1).

### `vitest.config.ts` reuses the Vite config
`mergeConfig(viteConfig, defineConfig({ test: { environment: 'jsdom' } }))` gives tests the same
Vue plugin and `@` alias as the app, so there's no separate Jest-style transform to maintain.
Import it as `./vite.config.ts` (with the extension), or Vite 8 warns that its native config
loader won't support it (FE-0.1).
