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

### MSW has to patch `fetch` before anyone captures it
In Vitest, MSW's `server.listen()` replaces `globalThis.fetch`. `openapi-fetch` keeps a reference to
the `fetch` that existed when `createClient()` ran, at import time, so its requests bypassed the
mocks and failed with "fetch failed". The client now passes `fetch: (request) => globalThis.fetch(request)`
to look it up on each call. The same applies to any library that takes a `fetch` option (FE-0.2).

### `staleTime` decides whether TanStack Query refetches on mount and focus
We set a global `staleTime: 30_000` in `main.ts`. Refetch-on-mount, refetch-on-window-focus and
refetch-on-reconnect only fire for *stale* queries, so inside those 30 s they're skipped.
`refetchInterval` ignores `staleTime`, so polling queries like the notification bell (#17) set
their own `refetchInterval` and `staleTime: 0`. `gcTime` is different: it's how long unused cache
entries stay in memory (FE-0.1 review).

## TypeScript

### One tsconfig per environment, tied together with project references
`tsconfig.json` only lists references: `tsconfig.app.json` (browser code, DOM types),
`tsconfig.node.json` (vite, vitest and eslint config files, Node types) and `tsconfig.vitest.json`
(tests, jsdom + Node types). `vue-tsc --build` checks each with its own globals, so app code can't
use Node APIs. `pnpm build` runs it first, so a type error in a test fails the build (FE-0.1).

## Java

## Spring Boot

### Boot 4 splits features into their own starters
Spring Boot 4 has a starter per technology, and a test starter for each: `spring-boot-starter-liquibase`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`. Snippets written for Boot 3 (plain `liquibase-core`, `spring-boot-starter-test`) still compile but miss the auto-configuration. Check the Boot 4 starter name first. (BE-0.1)

### Security also authorizes the /error forward
Since Spring Security 6 the filter chain also runs on the ERROR dispatch. With `anyRequest().authenticated()`, a 404/500 on a public endpoint reaches an anonymous caller as 401. Permit `DispatcherType.ERROR`. (BE-0.1 review)

### The OpenAPI base path isn't in the generated mappings by default
With `interfaceOnly`, the generated `@RequestMapping`s only hold the path (`/hello`). The `servers: /api` prefix is dropped unless you set `requestMappingMode=api_interface`, which puts `@RequestMapping("${openapi.<title>.base-path:/api}")` on the interface. `skipDefaultInterface=true` removes the default `501 Not Implemented` bodies, so a controller that misses an endpoint doesn't compile. (BE-0.2)

### Generated `@Validated` turns parameter errors into 500s
openapi-generator puts `@Validated` on the interfaces unless `useSpringBuiltInValidation=true`. With it, Spring validates `@RequestParam`/`@PathVariable` through an AOP proxy that throws `ConstraintViolationException`, which `ResponseEntityExceptionHandler` doesn't handle. Without it, Spring 6.1+'s built-in validation throws `HandlerMethodValidationException`, which becomes a 400 ProblemDetail. Also: a catch-all `@ExceptionHandler(Exception.class)` beats `@ResponseStatus` on exception classes and catches `AccessDeniedException`, so map those explicitly. (BE-0.2 review)

## Postgres

## Liquibase

## Tooling (Vite, pnpm, Maven, Docker, OpenAPI)

### A required status check must run on every PR
The CI workflows have no `paths:` filter. If `frontend.yml` only ran on `frontend/**` changes, a
BE-only PR would never report the required `frontend` check, and GitHub would keep the merge
blocked waiting for it. So both workflows run on every PR, and each finishes in a few minutes
(FE-0.3, BE-0.3).

### Checking committed generated code in CI: regenerate, then `git diff --exit-code`
`git diff --exit-code` exits with 1 when the working tree differs from the commit. Running it after
`pnpm gen:api` fails the build when someone changed `api/openapi.yaml` without regenerating the
types. Running it again at the end of the job catches any step that silently rewrites tracked files,
e.g. MSW's install script updating `public/mockServiceWorker.js` after an MSW upgrade (FE-0.3).

### ` #` starts a comment in YAML, even inside an unquoted string
`description: Problem Details (decision #21)` parses as `Problem Details (decision`, and the
generated TypeScript comment shows the cut. Quote values that contain ` #`:
`description: 'Problem Details (decision #21)'` (FE-0.2).

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
