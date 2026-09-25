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

### Attribute fallthrough goes to the root element
Attributes and listeners a component doesn't declare as props or emits land on its root element.
That's what makes `<BaseButton :disabled @click>` work, because the `<button>` is the root. In
`BaseInput` the root is a wrapper `<div>`, so `maxlength` or `name` silently did nothing. Wrapped
inputs use `defineOptions({ inheritAttrs: false })` and `v-bind="$attrs"` on the real `<input>`
(FE-1.1 review).

### The TanStack Query cache knows keys, not users
`setQueryData(queryKeys.me, user)` after login saves a GET /me, but everything else cached in the
tab survives a re-login, so after an expired session a different user could briefly see the previous
user's data. Login now calls `queryClient.clear()` first. Keys live in `src/api/queryKeys.ts`, so the
code that writes an entry and the code that reads it can't drift apart (FE-1.1 review).

### A cached user isn't proof of a session
The first route guard used `ensureQueryData` (deprecated in TanStack Query 5.10x), which returns
cached data without a request, and a failed refetch keeps the old `data` next to the error. So after
a session expired, `client.ts` sent the user to `/login`, the guard found the cached user and sent
them straight back home. Now the guard uses `fetchQuery` with `staleTime: 0` for the login page
(always ask the server) and `Infinity` elsewhere, and every 401 removes the cached user
(FE-1.2 review).

### A guard error on the first navigation rejects `router.isReady()`
Throwing in `beforeEach` cancels a navigation. Later that just keeps the current page, but on first
load there is no page: `await router.isReady(); app.mount()` never mounts and the screen stays
blank. `main.ts` catches that case and shows a fallback message (FE-1.2 review).

## TypeScript

### One tsconfig per environment, tied together with project references
`tsconfig.json` only lists references: `tsconfig.app.json` (browser code, DOM types),
`tsconfig.node.json` (vite, vitest and eslint config files, Node types) and `tsconfig.vitest.json`
(tests, jsdom + Node types). `vue-tsc --build` checks each with its own globals, so app code can't
use Node APIs. `pnpm build` runs it first, so a type error in a test fails the build (FE-0.1).

## Java

## Spring Boot

### CSRF tokens are deferred: rotating one must also load the new one
Since Spring Security 6 the `XSRF-TOKEN` cookie is only written when something reads the token. `csrf.spa()` does that on every request, but a hand-built `CsrfAuthenticationStrategy` doesn't: login deleted the old cookie and sent no new one, so the FE's next POST got a 403. Give the strategy a `XorCsrfTokenRequestAttributeHandler` with `setCsrfRequestAttributeName(null)`, and after logout call `csrfTokenRepository.loadDeferredToken(request, response).get()`. A test helper that fetches a fresh token before every request hid this; the tests now reuse the token from the previous response, like the browser. (BE-1.2 review)

### spring-security-test's `csrf()` changes the whole test context
`.with(csrf())` replaces the CSRF token repository inside the `CsrfFilter`, and the filter lives in the cached Spring context. After one test uses it, other tests in the same context stop getting a real `XSRF-TOKEN` cookie. Our integration tests copy the cookie into the `X-XSRF-TOKEN` header like the browser does, and `CsrfCookieTest` checks the cookie in a fresh web slice. (BE-1.2)

### A JSON login has to do what `formLogin` did
With a custom `POST /api/auth/login`, Spring Security 6+ doesn't save the `SecurityContext` for you. `SessionLogin` authenticates, changes the session id (session fixation), rotates the CSRF token and calls `SecurityContextRepository.saveContext`. Without the last step, the next request is anonymous again. Filter-chain 401/403 never reach `@RestControllerAdvice`; the entry point and access-denied handler pass them to the MVC `HandlerExceptionResolver`, so `ApiExceptionHandler` writes the Problem Details. (BE-1.2)

### DB defaults are invisible to Hibernate unless marked `@Generated`
A column filled by `default now()` stays `null` on the entity after `save`, and also after a find in the same transaction, because the persistence context returns the same instance. `@org.hibernate.annotations.Generated` makes Hibernate read it back (`insert ... returning` on Postgres). (BE-1.1 review)

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

### An empty context list runs the dev seed too
Changesets without a `context` always run. A `context: dev` changeset runs when `dev` is active, but also when *no* context is set at all. So `spring.liquibase.contexts` defaults to the non-empty `default`, and only the `dev` profile (`./mvnw spring-boot:run`) and the test config switch on `dev`. (BE-1.1 review)

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

## Epic 0 recap: how the frontend was set up

A short summary of the steps Claude Code took for the frontend stories of epic 0, and what it would
repeat next time. Details are in the entries above and in `decisions.md` (#25, #26).

1. **Switched to pnpm.** Removed the npm lockfile, pinned the pnpm version in `package.json` and
   changed the docs to use pnpm commands.
2. **Built the skeleton (FE-0.1).** Added routing, TanStack Query for server data, and the tooling
   for linting, formatting and tests. When pnpm asked whether a package could run an install
   script, it checked what the script did before answering.
3. **Had the work reviewed.** Ran the fe-specialist reviewer before merging and fixed what it found.
   The main issue was that the Vue skill still taught an older pattern than our decisions.
4. **Connected the frontend to the API contract (FE-0.2).** Generated TypeScript types from
   `openapi.yaml`, wrote the single API client (errors and the redirect to login when not logged
   in), and added mock responses that both the dev server and the tests use. One bug took some
   digging: the tests reached the real network instead of the mocks.
5. **Automated the checks (FE-0.3).** Added a GitHub Actions workflow that runs the same checks on
   every PR, including one that fails when the generated types are out of date. It was tried in a
   fresh copy of the repo first, including a deliberate failure to prove the check works.

**Habits worth keeping:**
- Read `decisions.md` before following a tool's default or a skill's advice, because the
  decisions win.
- Run lint, tests and build before every commit, not just at the end.
- When something only works on your machine, try it in a clean clone.
- Write down each choice (in `decisions.md`) and each surprise (here) while it's fresh.
- Keep one story per branch and PR, and mention anything found outside the story, such as the YAML
  comment bug in the contract, instead of quietly fixing it.
